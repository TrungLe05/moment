import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, NETWORK_ERROR } from "@/lib/api/errors";
import type { CreateMomentRequest, CreatedMoment, Moment, UploadTarget } from "@/lib/api/moments";
import { createPendingMoments, type PendingDeps, type PendingDraft } from "./pending-moments";

const POLL_MS = 1000;

function moment(id: string, status: Moment["status"], failureReason: string | null = null): Moment {
  return {
    id,
    isMine: true,
    owner: { id: "me", username: "me", displayName: "Me", avatarUrl: null },
    mediaType: "PHOTO",
    source: "CAMERA",
    mode: "SHARED",
    status,
    caption: null,
    layers: [],
    layersVersion: 1,
    width: null,
    height: null,
    durationMs: null,
    createdAt: "2026-11-17T03:00:00Z",
    expiresAt: "2026-11-18T03:00:00Z",
    keptAt: null,
    media: null,
    myReaction: null,
    ownerView: { audience: "ALL", recipientCount: 1, reactionCount: 0, failureReason },
  };
}

function target(variant: UploadTarget["variant"], expiresAt: string): UploadTarget {
  return { variant, method: "PUT", url: `https://storage.test/${variant}`, headers: { "Content-Type": "image/jpeg" }, expiresAt };
}

const request: CreateMomentRequest = {
  mediaType: "PHOTO",
  source: "CAMERA",
  mode: "SHARED",
  audience: "ALL",
  recipientIds: [],
  caption: null,
  layers: [],
  layersVersion: 1,
  trimStartMs: null,
  files: [
    { variant: "ORIGINAL", contentType: "image/jpeg", sizeBytes: 4 },
    { variant: "COMPOSED", contentType: "image/jpeg", sizeBytes: 4 },
  ],
};

const original = new Blob(["orig"]);
const composed = new Blob(["comp"]);
const draft: PendingDraft = { request, files: { ORIGINAL: original, COMPOSED: composed }, previewUrl: "blob:preview" };

describe("pending moments (US-28)", () => {
  let now: number;
  let deps: PendingDeps & {
    create: ReturnType<typeof vi.fn<(body: CreateMomentRequest) => Promise<CreatedMoment>>>;
    complete: ReturnType<typeof vi.fn<(id: string) => Promise<Moment>>>;
    retry: ReturnType<typeof vi.fn<(id: string) => Promise<Moment>>>;
    detail: ReturnType<typeof vi.fn<(id: string) => Promise<Moment>>>;
    remove: ReturnType<typeof vi.fn<(id: string) => Promise<void>>>;
    upload: ReturnType<typeof vi.fn<(target: UploadTarget, file: Blob) => Promise<void>>>;
    revokeUrl: ReturnType<typeof vi.fn<(url: string) => void>>;
  };

  function created(id: string): CreatedMoment {
    const expiresAt = new Date(now + 15 * 60_000).toISOString();
    return { moment: moment(id, "DRAFT"), uploads: [target("ORIGINAL", expiresAt), target("COMPOSED", expiresAt)] };
  }

  beforeEach(() => {
    vi.useFakeTimers();
    now = Date.parse("2026-11-17T03:00:00Z");
    deps = {
      create: vi.fn(),
      complete: vi.fn(),
      retry: vi.fn(),
      detail: vi.fn(),
      remove: vi.fn(),
      upload: vi.fn(),
      revokeUrl: vi.fn(),
      now: () => now,
      pollMs: POLL_MS,
    };
    deps.create.mockImplementation(async () => created("m1"));
    deps.upload.mockResolvedValue(undefined);
    deps.complete.mockResolvedValue(moment("m1", "PROCESSING"));
    deps.remove.mockResolvedValue(undefined);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("uploads both files, completes, then publishes the moment once it is ready", async () => {
    const store = createPendingMoments(deps);
    const published = vi.fn();
    store.onPublished(published);
    deps.detail.mockResolvedValueOnce(moment("m1", "PROCESSING")).mockResolvedValueOnce(moment("m1", "READY"));

    await store.submit(draft);
    expect(store.getSnapshot()).toEqual([expect.objectContaining({ previewUrl: "blob:preview", stage: "uploading" })]);

    await vi.advanceTimersByTimeAsync(0);
    expect(deps.upload).toHaveBeenCalledWith(expect.objectContaining({ variant: "ORIGINAL" }), original);
    expect(deps.upload).toHaveBeenCalledWith(expect.objectContaining({ variant: "COMPOSED" }), composed);
    expect(deps.complete).toHaveBeenCalledWith("m1");
    expect(store.getSnapshot()[0]?.stage).toBe("processing");

    await vi.advanceTimersByTimeAsync(POLL_MS);
    expect(store.getSnapshot()[0]?.stage).toBe("processing");
    await vi.advanceTimersByTimeAsync(POLL_MS);

    expect(published).toHaveBeenCalledWith(expect.objectContaining({ id: "m1", status: "READY" }));
    expect(store.getSnapshot()).toEqual([]);
    expect(deps.revokeUrl).toHaveBeenCalledWith("blob:preview");
  });

  it("lets the caller show create errors without keeping anything pending", async () => {
    deps.create.mockRejectedValue(new ApiError(422, "NO_FRIENDS"));
    const store = createPendingMoments(deps);

    await expect(store.submit(draft)).rejects.toMatchObject({ code: "NO_FRIENDS" });
    expect(store.getSnapshot()).toEqual([]);
  });

  // US-28: mất mạng giữa chừng thì vẫn mờ, cho thử lại mà không phải soạn lại; file đã tải xong không tải lại.
  it("keeps the moment when an upload fails and retries only the missing file", async () => {
    deps.upload.mockResolvedValueOnce(undefined).mockRejectedValueOnce(new ApiError(0, NETWORK_ERROR));
    const store = createPendingMoments(deps);
    await store.submit(draft);
    await vi.advanceTimersByTimeAsync(0);

    const failed = store.getSnapshot()[0]!;
    expect(failed.stage).toBe("upload-failed");
    expect(failed.error).toMatchObject({ code: NETWORK_ERROR });

    store.retry(failed.localId);
    await vi.advanceTimersByTimeAsync(0);

    expect(deps.upload).toHaveBeenCalledTimes(3);
    expect(deps.upload).toHaveBeenLastCalledWith(expect.objectContaining({ variant: "COMPOSED" }), composed);
    expect(deps.create).toHaveBeenCalledTimes(1);
    expect(store.getSnapshot()[0]?.stage).toBe("processing");
  });

  // Presigned URL chỉ sống 15 phút: thử lại sau đó thì bỏ bản nháp cũ và tạo bản mới với cùng nội dung.
  it("starts a new draft when the upload links have expired before a retry", async () => {
    deps.upload.mockRejectedValueOnce(new ApiError(0, NETWORK_ERROR));
    const store = createPendingMoments(deps);
    await store.submit(draft);
    await vi.advanceTimersByTimeAsync(0);
    deps.create.mockImplementation(async () => created("m2"));
    deps.complete.mockResolvedValue(moment("m2", "PROCESSING"));

    now += 20 * 60_000;
    store.retry(store.getSnapshot()[0]!.localId);
    await vi.advanceTimersByTimeAsync(0);

    expect(deps.remove).toHaveBeenCalledWith("m1");
    expect(deps.create).toHaveBeenLastCalledWith(request);
    expect(deps.complete).toHaveBeenLastCalledWith("m2");
    expect(store.getSnapshot()[0]).toMatchObject({ momentId: "m2", stage: "processing" });
  });

  // US-28: xử lý thất bại thì báo lỗi theo mã và cho thử lại hoặc hủy (D-033).
  it("reports a processing failure by reason, retries it on the server, or cancels it", async () => {
    deps.detail.mockResolvedValueOnce(moment("m1", "FAILED", "MEDIA_UNREADABLE"));
    const store = createPendingMoments(deps);
    await store.submit(draft);
    await vi.advanceTimersByTimeAsync(POLL_MS);

    const failed = store.getSnapshot()[0]!;
    expect(failed).toMatchObject({ stage: "processing-failed", failureReason: "MEDIA_UNREADABLE" });

    deps.retry.mockResolvedValue(moment("m1", "PROCESSING"));
    store.retry(failed.localId);
    await vi.advanceTimersByTimeAsync(0);
    expect(deps.retry).toHaveBeenCalledWith("m1");
    expect(store.getSnapshot()[0]?.stage).toBe("processing");

    store.cancel(failed.localId);
    expect(store.getSnapshot()).toEqual([]);
    expect(deps.remove).toHaveBeenCalledWith("m1");
    expect(deps.revokeUrl).toHaveBeenCalledWith("blob:preview");
    // Hủy rồi thì không hỏi trạng thái nữa.
    deps.detail.mockClear();
    await vi.advanceTimersByTimeAsync(POLL_MS * 3);
    expect(deps.detail).not.toHaveBeenCalled();
  });

  it("keeps polling through a network blip while processing", async () => {
    deps.detail.mockRejectedValueOnce(new ApiError(0, NETWORK_ERROR)).mockResolvedValueOnce(moment("m1", "READY"));
    const store = createPendingMoments(deps);
    const published = vi.fn();
    store.onPublished(published);
    await store.submit(draft);

    await vi.advanceTimersByTimeAsync(POLL_MS);
    expect(store.getSnapshot()[0]?.stage).toBe("processing");
    await vi.advanceTimersByTimeAsync(POLL_MS);

    expect(published).toHaveBeenCalledTimes(1);
  });

  // Gọi complete lần trước đã tới server nhưng mất phản hồi: thử lại nhận MOMENT_NOT_DRAFT, coi như đã báo xong.
  it("treats an already completed draft as processing on retry", async () => {
    deps.complete.mockRejectedValueOnce(new ApiError(0, NETWORK_ERROR)).mockRejectedValueOnce(new ApiError(409, "MOMENT_NOT_DRAFT"));
    const store = createPendingMoments(deps);
    await store.submit(draft);
    await vi.advanceTimersByTimeAsync(0);
    expect(store.getSnapshot()[0]?.stage).toBe("upload-failed");

    store.retry(store.getSnapshot()[0]!.localId);
    await vi.advanceTimersByTimeAsync(0);

    expect(store.getSnapshot()[0]?.stage).toBe("processing");
  });
});
