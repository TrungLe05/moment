import type { TokenStore } from "@/platform/token-store";
import { ApiError, NETWORK_ERROR, apiErrorFromProblem } from "./errors";

type RequestOptions = {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  /** Gắn access token và tự refresh một lần khi nhận 401. */
  auth?: boolean;
};

export type ApiClient = {
  request<T>(path: string, options?: RequestOptions): Promise<T>;
  /** Lấy access token mới bằng cookie refresh token; trả false nếu không còn phiên. */
  refreshSession(): Promise<boolean>;
};

type ClientConfig = {
  baseUrl: string;
  tokenStore: TokenStore;
  getLocale: () => string;
  fetchImpl?: typeof fetch;
};

/**
 * Client HTTP dùng chung cho mọi lời gọi API (CLAUDE.md, quy ước frontend). Gửi kèm Accept-Language,
 * X-Client-Type: web; đọc lỗi Problem Details thành ApiError theo `code`.
 */
export function createApiClient({ baseUrl, tokenStore, getLocale, fetchImpl = fetch }: ClientConfig): ApiClient {
  const apiRoot = `${baseUrl.replace(/\/+$/, "")}/api/v1`;
  let refreshInFlight: Promise<boolean> | null = null;

  async function send(path: string, options: RequestOptions): Promise<Response> {
    const headers: Record<string, string> = {
      Accept: "application/json",
      "Accept-Language": getLocale(),
      "X-Client-Type": "web",
    };
    if (options.body !== undefined) {
      headers["Content-Type"] = "application/json";
    }
    const accessToken = tokenStore.getAccessToken();
    if (options.auth && accessToken) {
      headers.Authorization = `Bearer ${accessToken}`;
    }
    try {
      return await fetchImpl(`${apiRoot}${path}`, {
        method: options.method ?? "GET",
        headers,
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
        // Cookie refresh token chỉ có Path=/api/v1/auth nên chỉ cần gửi kèm cho nhóm Auth.
        credentials: path.startsWith("/auth/") ? "include" : "same-origin",
      });
    } catch {
      throw new ApiError(0, NETWORK_ERROR);
    }
  }

  async function parse<T>(response: Response): Promise<T> {
    const text = await response.text();
    const body: unknown = text.length > 0 ? JSON.parse(text) : undefined;
    if (!response.ok) {
      throw apiErrorFromProblem(response.status, body, response.headers.get("Retry-After"));
    }
    return body as T;
  }

  function refreshSession(): Promise<boolean> {
    // Nhiều request cùng hết hạn token chỉ gọi refresh một lần, vì refresh token xoay vòng mỗi lần dùng.
    refreshInFlight ??= (async () => {
      try {
        const response = await send("/auth/refresh", { method: "POST" });
        if (!response.ok) {
          tokenStore.setAccessToken(null);
          return false;
        }
        const body = await parse<{ accessToken: string }>(response);
        tokenStore.setAccessToken(body.accessToken);
        return true;
      } catch {
        tokenStore.setAccessToken(null);
        return false;
      } finally {
        refreshInFlight = null;
      }
    })();
    return refreshInFlight;
  }

  async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    let response = await send(path, options);
    if (response.status === 401 && options.auth) {
      const refreshed = await refreshSession();
      if (refreshed) {
        response = await send(path, options);
      }
    }
    return parse<T>(response);
  }

  return { request, refreshSession };
}
