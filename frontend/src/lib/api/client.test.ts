import { describe, expect, it, vi } from "vitest";
import { MemoryTokenStore } from "@/platform/token-store";
import { createApiClient } from "./client";
import { ApiError, NETWORK_ERROR } from "./errors";

function jsonResponse(status: number, body: unknown, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json", ...headers },
  });
}

function setup(responses: Response[]) {
  const fetchImpl = vi.fn<typeof fetch>();
  responses.forEach((response) => fetchImpl.mockResolvedValueOnce(response));
  const tokenStore = new MemoryTokenStore();
  const client = createApiClient({ baseUrl: "http://api.test/", tokenStore, getLocale: () => "vi", fetchImpl });
  return { client, fetchImpl, tokenStore };
}

function headersOf(call: Parameters<typeof fetch>): Record<string, string> {
  const init = call[1];
  const headers = init?.headers;
  return typeof headers === "object" && headers !== null && !(headers instanceof Headers) && !Array.isArray(headers)
    ? headers
    : {};
}

describe("createApiClient", () => {
  it("sends locale, client type and bearer token for authenticated calls", async () => {
    const { client, fetchImpl, tokenStore } = setup([jsonResponse(200, { ok: true })]);
    tokenStore.setAccessToken("access-1");

    await client.request("/me", { auth: true });

    const call = fetchImpl.mock.calls[0];
    expect(call[0]).toBe("http://api.test/api/v1/me");
    expect(headersOf(call)).toMatchObject({
      "Accept-Language": "vi",
      "X-Client-Type": "web",
      Authorization: "Bearer access-1",
    });
  });

  it("includes cookies only for auth endpoints", async () => {
    const { client, fetchImpl } = setup([jsonResponse(200, {}), jsonResponse(200, {})]);

    await client.request("/auth/login", { method: "POST", body: {} });
    await client.request("/me", { auth: true });

    expect(fetchImpl.mock.calls[0][1]?.credentials).toBe("include");
    expect(fetchImpl.mock.calls[1][1]?.credentials).toBe("same-origin");
  });

  it("refreshes once and retries when the access token expired", async () => {
    const { client, fetchImpl, tokenStore } = setup([
      jsonResponse(401, { code: "UNAUTHENTICATED" }),
      jsonResponse(200, { accessToken: "access-2", expiresIn: 900 }),
      jsonResponse(200, { id: "u1" }),
    ]);
    tokenStore.setAccessToken("expired");

    const result = await client.request<{ id: string }>("/me", { auth: true });

    expect(result.id).toBe("u1");
    expect(fetchImpl.mock.calls[1][0]).toBe("http://api.test/api/v1/auth/refresh");
    expect(headersOf(fetchImpl.mock.calls[2]).Authorization).toBe("Bearer access-2");
  });

  it("clears the token and throws when refresh fails", async () => {
    const { client, tokenStore } = setup([
      jsonResponse(401, { code: "UNAUTHENTICATED" }),
      jsonResponse(401, { code: "TOKEN_REUSED" }),
    ]);
    tokenStore.setAccessToken("expired");

    await expect(client.request("/me", { auth: true })).rejects.toMatchObject({ code: "UNAUTHENTICATED" });
    expect(tokenStore.getAccessToken()).toBeNull();
  });

  it("shares one refresh call between concurrent requests", async () => {
    const fetchImpl = vi.fn<typeof fetch>((input) =>
      Promise.resolve(
        String(input).endsWith("/auth/refresh") ? jsonResponse(200, { accessToken: "fresh" }) : jsonResponse(200, {}),
      ),
    );
    const client = createApiClient({
      baseUrl: "http://api.test",
      tokenStore: new MemoryTokenStore(),
      getLocale: () => "en",
      fetchImpl,
    });

    await Promise.all([client.refreshSession(), client.refreshSession()]);

    expect(fetchImpl).toHaveBeenCalledTimes(1);
  });

  it("maps problem details to ApiError with field errors and Retry-After", async () => {
    const { client } = setup([
      jsonResponse(429, { code: "LOGIN_LOCKED", errors: [{ field: "email", code: "INVALID_FORMAT" }] }, {
        "Retry-After": "900",
      }),
    ]);

    const error: unknown = await client.request("/auth/login", { method: "POST", body: {} }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 429,
      code: "LOGIN_LOCKED",
      retryAfterSeconds: 900,
      fieldErrors: [{ field: "email", code: "INVALID_FORMAT" }],
    });
  });

  it("reports NETWORK_ERROR when the server is unreachable", async () => {
    const fetchImpl = vi.fn<typeof fetch>().mockRejectedValue(new TypeError("Failed to fetch"));
    const client = createApiClient({
      baseUrl: "http://api.test",
      tokenStore: new MemoryTokenStore(),
      getLocale: () => "vi",
      fetchImpl,
    });

    await expect(client.request("/me")).rejects.toMatchObject({ code: NETWORK_ERROR });
  });
});
