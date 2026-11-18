import { describe, expect, it } from "vitest";
import { checkDuration, classifyFile, MAX_PHOTO_BYTES, MAX_VIDEO_BYTES } from "./upload-rules";

function file(type: string, size: number): File {
  const picked = new File(["x"], "picked", { type });
  Object.defineProperty(picked, "size", { value: size });
  return picked;
}

describe("classifyFile (BR-10, US-22)", () => {
  it("accepts JPEG, PNG, WebP photos up to 15 MB and MP4, WebM, MOV videos up to 50 MB", () => {
    for (const type of ["image/jpeg", "image/png", "image/webp"]) {
      expect(classifyFile(file(type, MAX_PHOTO_BYTES))).toEqual({ kind: "photo" });
    }
    for (const type of ["video/mp4", "video/webm", "video/quicktime"]) {
      expect(classifyFile(file(type, MAX_VIDEO_BYTES))).toEqual({ kind: "video" });
    }
  });

  it("rejects other formats before uploading, whatever the file name says", () => {
    expect(classifyFile(file("image/heic", 1000))).toEqual({ error: "UNSUPPORTED_MEDIA_TYPE" });
    expect(classifyFile(file("image/gif", 1000))).toEqual({ error: "UNSUPPORTED_MEDIA_TYPE" });
    expect(classifyFile(file("", 1000))).toEqual({ error: "UNSUPPORTED_MEDIA_TYPE" });
  });

  it("rejects files over the size limit", () => {
    expect(classifyFile(file("image/jpeg", MAX_PHOTO_BYTES + 1))).toEqual({ error: "PAYLOAD_TOO_LARGE" });
    expect(classifyFile(file("video/mp4", MAX_VIDEO_BYTES + 1))).toEqual({ error: "PAYLOAD_TOO_LARGE" });
  });
});

describe("checkDuration (BR-10)", () => {
  it("accepts videos up to 60 seconds and those whose length the browser cannot tell", () => {
    expect(checkDuration(60_000)).toBeNull();
    expect(checkDuration(null)).toBeNull();
    expect(checkDuration(60_001)).toBe("VIDEO_TOO_LONG");
  });
});
