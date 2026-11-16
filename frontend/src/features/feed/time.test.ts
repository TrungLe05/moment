import { describe, expect, it } from "vitest";
import { postedAgo, timeLeft } from "./time";

const now = Date.parse("2026-11-16T12:00:00Z");
const at = (offsetMinutes: number) => new Date(now + offsetMinutes * 60_000).toISOString();

describe("timeLeft (US-31)", () => {
  it("counts whole hours left when at least one hour remains", () => {
    expect(timeLeft(at(23 * 60 + 59), now)).toEqual({ unit: "hours", value: 23 });
    expect(timeLeft(at(60), now)).toEqual({ unit: "hours", value: 1 });
  });

  it("switches to minutes under one hour, never showing zero", () => {
    expect(timeLeft(at(59), now)).toEqual({ unit: "minutes", value: 59 });
    expect(timeLeft(at(0.5), now)).toEqual({ unit: "minutes", value: 1 });
  });

  it("reports nothing once the moment has expired", () => {
    expect(timeLeft(at(0), now)).toBeNull();
    expect(timeLeft(at(-5), now)).toBeNull();
  });
});

describe("postedAgo", () => {
  it("uses minutes under an hour, at least one", () => {
    expect(postedAgo(at(-0.2), now)).toEqual({ unit: "minutes", value: 1 });
    expect(postedAgo(at(-12), now)).toEqual({ unit: "minutes", value: 12 });
  });

  it("uses whole hours after an hour", () => {
    expect(postedAgo(at(-61), now)).toEqual({ unit: "hours", value: 1 });
    expect(postedAgo(at(-23 * 60 - 59), now)).toEqual({ unit: "hours", value: 23 });
  });
});
