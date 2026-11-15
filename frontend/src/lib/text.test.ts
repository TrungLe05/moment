import { describe, expect, it } from "vitest";
import { countGraphemes } from "./text";

describe("countGraphemes (BR-11)", () => {
  it("counts emoji and Vietnamese letters as one visible character", () => {
    expect(countGraphemes("Việt")).toBe(4);
    expect(countGraphemes("Việt")).toBe(4);
    expect(countGraphemes("👨‍👩‍👧")).toBe(1);
    expect(countGraphemes("hi 🔥")).toBe(4);
    expect(countGraphemes("")).toBe(0);
  });
});
