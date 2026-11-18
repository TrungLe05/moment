import { describe, expect, it } from "vitest";
import manifest from "./manifest";

describe("web app manifest (US-50)", () => {
  it("makes the app installable as a standalone window in the dark theme", () => {
    const app = manifest();

    expect(app).toMatchObject({ name: "Khoảnh khắc", start_url: "/", scope: "/", display: "standalone" });
    expect(app.background_color).toBe("#0e0e10");
    expect(app.theme_color).toBe("#0e0e10");
  });

  it("offers 192 and 512 px PNG icons, including a maskable one", () => {
    const icons = manifest().icons ?? [];

    expect(icons).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ src: "/icon-192.png", sizes: "192x192", type: "image/png" }),
        expect.objectContaining({ src: "/icon-512.png", sizes: "512x512", type: "image/png" }),
        expect.objectContaining({ sizes: "512x512", purpose: "maskable" }),
      ]),
    );
  });
});
