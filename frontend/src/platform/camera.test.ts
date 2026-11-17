import { describe, expect, it } from "vitest";
import { CameraError, pickRecorderType, toCameraError } from "./camera";

describe("toCameraError (US-20, US-21)", () => {
  it("maps browser errors to the kinds the screen explains", () => {
    expect(toCameraError(new DOMException("denied", "NotAllowedError")).kind).toBe("denied");
    expect(toCameraError(new DOMException("denied", "SecurityError")).kind).toBe("denied");
    expect(toCameraError(new DOMException("none", "NotFoundError")).kind).toBe("not-found");
    expect(toCameraError(new DOMException("none", "OverconstrainedError")).kind).toBe("not-found");
    expect(toCameraError(new DOMException("in use", "NotReadableError")).kind).toBe("busy");
    expect(toCameraError(new Error("boom")).kind).toBe("unknown");
  });

  it("keeps a CameraError as it is", () => {
    const original = new CameraError("unsupported");
    expect(toCameraError(original)).toBe(original);
  });
});

describe("pickRecorderType (BR-10)", () => {
  it("prefers MP4 and falls back to WebM, reporting the upload content type without codecs", () => {
    expect(pickRecorderType((type) => type.startsWith("video/mp4"))).toEqual({
      recorderType: "video/mp4;codecs=avc1,mp4a",
      contentType: "video/mp4",
    });
    expect(pickRecorderType((type) => type === "video/webm;codecs=vp8,opus" || type === "video/webm")).toEqual({
      recorderType: "video/webm;codecs=vp8,opus",
      contentType: "video/webm",
    });
  });

  it("returns null when the browser can record neither MP4 nor WebM", () => {
    expect(pickRecorderType(() => false)).toBeNull();
  });
});
