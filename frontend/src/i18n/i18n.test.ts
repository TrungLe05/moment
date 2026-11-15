import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/errors";
import { errorText, fieldErrorText } from "./error-text";
import { detectLocale } from "./locale";
import { format, messages } from "./messages";

function keyPaths(value: unknown, prefix = ""): string[] {
  if (typeof value !== "object" || value === null) {
    return [prefix];
  }
  return Object.entries(value).flatMap(([key, child]) => keyPaths(child, prefix ? `${prefix}.${key}` : key));
}

describe("detectLocale (US-07)", () => {
  it("uses Vietnamese for vi browsers and English for everything else", () => {
    expect(detectLocale(["vi-VN", "en"])).toBe("vi");
    expect(detectLocale(["vi"])).toBe("vi");
    expect(detectLocale(["en-US"])).toBe("en");
    expect(detectLocale(["fr-FR", "vi"])).toBe("en");
    expect(detectLocale([])).toBe("en");
  });
});

describe("messages", () => {
  it("has the same keys in vi and en with no empty strings", () => {
    expect(keyPaths(messages.en).sort()).toEqual(keyPaths(messages.vi).sort());
    for (const locale of ["vi", "en"] as const) {
      expect(JSON.stringify(messages[locale])).not.toContain('""');
    }
  });

  it("formats named placeholders", () => {
    expect(format(messages.vi.home.greeting, { name: "Minh Anh" })).toBe("Chào Minh Anh!");
  });
});

describe("errorText", () => {
  it("chooses the message by error code, not server text", () => {
    expect(errorText(messages.vi, new ApiError(401, "INVALID_CREDENTIALS"))).toBe(messages.vi.errors.INVALID_CREDENTIALS);
    expect(errorText(messages.en, new ApiError(500, "SOMETHING_NEW"))).toBe(messages.en.errors.UNKNOWN);
    expect(errorText(messages.en, new Error("boom"))).toBe(messages.en.errors.UNKNOWN);
  });

  it("returns field messages only for the matching field", () => {
    const error = new ApiError(400, "VALIDATION_FAILED", [{ field: "password", code: "TOO_SHORT" }]);
    expect(fieldErrorText(messages.vi, error, "password")).toBe(messages.vi.fieldErrors.TOO_SHORT);
    expect(fieldErrorText(messages.vi, error, "email")).toBeUndefined();
  });
});
