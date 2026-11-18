// @vitest-environment node
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { runInNewContext } from "node:vm";
import { describe, expect, it } from "vitest";

type Strategy = "static" | "page" | "asset" | "network";
type Classify = (input: { url: string; method: string; mode: string }) => Strategy;

// Nạp public/sw.js trong một ngữ cảnh riêng với `self` giả, lấy hàm phân loại request nó công bố để test.
function loadClassify(): Classify {
  const source = readFileSync(join(process.cwd(), "public", "sw.js"), "utf8");
  const self: { location: URL; addEventListener: () => void; __classify?: Classify } = {
    location: new URL("http://localhost:3000/sw.js"),
    addEventListener: () => undefined,
  };
  runInNewContext(source, { self, URL, caches: {}, fetch: () => undefined });
  if (!self.__classify) throw new Error("sw.js không công bố __classify");
  return self.__classify;
}

const classify = loadClassify();
const get = (url: string, mode = "no-cors") => classify({ url, method: "GET", mode });

describe("service worker (US-50)", () => {
  it("caches hashed build files first, since they never change", () => {
    expect(get("http://localhost:3000/_next/static/chunks/app-1a2b3c.js")).toBe("static");
    expect(get("http://localhost:3000/_next/static/css/abc.css")).toBe("static");
  });

  it("loads pages from the network first and keeps their static shell for offline", () => {
    expect(get("http://localhost:3000/", "navigate")).toBe("page");
    expect(get("http://localhost:3000/archive/moment?id=x", "navigate")).toBe("page");
  });

  it("refreshes icons and the manifest in the background", () => {
    expect(get("http://localhost:3000/icon-192.png")).toBe("asset");
    expect(get("http://localhost:3000/manifest.webmanifest")).toBe("asset");
  });

  // Đặc tả US-50: không cache dữ liệu riêng tư (API, file khoảnh khắc qua presigned URL).
  it("never caches the API, other origins or anything but GET", () => {
    expect(get("http://localhost:3000/api/v1/feed")).toBe("network");
    expect(get("http://localhost:8081/api/v1/feed")).toBe("network");
    expect(get("http://localhost:9000/moment/moments/m1/display.jpg?X-Amz-Signature=abc")).toBe("network");
    expect(classify({ url: "http://localhost:3000/_next/static/a.js", method: "POST", mode: "cors" })).toBe("network");
  });
});
