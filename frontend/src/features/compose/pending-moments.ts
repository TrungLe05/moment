"use client";

import { useSyncExternalStore } from "react";
import { ApiError } from "@/lib/api/errors";
import { momentsApi, type CreateMomentRequest, type CreatedMoment, type Moment, type UploadTarget } from "@/lib/api/moments";
import { uploadFile } from "@/lib/api/uploads";

/**
 * - uploading: đang tải file lên và báo tải xong
 * - processing: server đang xử lý, hỏi trạng thái định kỳ
 * - upload-failed: tải lên hay báo tải xong lỗi (thường do mất mạng), thử lại không phải soạn lại
 * - processing-failed: worker xử lý thất bại (D-033), thử lại trên server hoặc hủy
 */
export type PendingStage = "uploading" | "processing" | "upload-failed" | "processing-failed";

/** Khoảnh khắc vừa gửi, hiện mờ ở đầu feed cho tới khi sẵn sàng (US-28). */
export type PendingMoment = {
  localId: string;
  momentId: string;
  mediaType: CreateMomentRequest["mediaType"];
  /** Object URL của file ở máy: bản ghép với ảnh, video gốc với video (hiện khung hình đầu). */
  previewUrl: string;
  stage: PendingStage;
  error: unknown;
  failureReason: string | null;
};

/** Nội dung đã soạn, giữ lại ở máy để thử lại mà không phải soạn lại. */
export type PendingDraft = {
  request: CreateMomentRequest;
  files: Partial<Record<UploadTarget["variant"], Blob>>;
  previewUrl: string;
};

export type PendingDeps = {
  create: (body: CreateMomentRequest) => Promise<CreatedMoment>;
  complete: (id: string) => Promise<Moment>;
  retry: (id: string) => Promise<Moment>;
  detail: (id: string) => Promise<Moment>;
  remove: (id: string) => Promise<void>;
  upload: (target: UploadTarget, file: Blob) => Promise<void>;
  revokeUrl: (url: string) => void;
  now: () => number;
  /** Khoảng hỏi trạng thái khi server đang xử lý. */
  pollMs: number;
};

// Presigned URL hết hạn sau 15 phút; còn dưới 30 giây thì coi như hết để không tải dở rồi bị từ chối.
const UPLOAD_EXPIRY_MARGIN_MS = 30_000;

type Job = {
  item: PendingMoment;
  draft: PendingDraft;
  uploads: UploadTarget[];
  uploaded: Set<UploadTarget["variant"]>;
  timer: ReturnType<typeof setTimeout> | null;
  cancelled: boolean;
};

let nextLocalId = 1;

/** Kho các khoảnh khắc đang đăng, sống ngoài component nên vẫn chạy tiếp khi chuyển từ trang soạn sang feed. */
export function createPendingMoments(deps: PendingDeps) {
  const jobs = new Map<string, Job>();
  let snapshot: PendingMoment[] = [];
  const listeners = new Set<() => void>();
  const publishedListeners = new Set<(moment: Moment) => void>();

  function emit() {
    snapshot = [...jobs.values()].map((job) => job.item).reverse();
    listeners.forEach((listener) => listener());
  }

  function update(job: Job, change: Partial<PendingMoment>) {
    if (job.cancelled) return;
    job.item = { ...job.item, ...change };
    emit();
  }

  function schedule(job: Job, step: () => Promise<void>, delayMs: number) {
    if (job.cancelled) return;
    if (job.timer) clearTimeout(job.timer);
    job.timer = setTimeout(() => {
      job.timer = null;
      void step();
    }, delayMs);
  }

  function finish(job: Job) {
    job.cancelled = true;
    if (job.timer) clearTimeout(job.timer);
    jobs.delete(job.item.localId);
    deps.revokeUrl(job.draft.previewUrl);
    emit();
  }

  function adopt(job: Job, created: CreatedMoment) {
    job.uploads = created.uploads;
    job.uploaded = new Set();
    job.item = { ...job.item, momentId: created.moment.id };
  }

  async function send(job: Job) {
    try {
      for (const target of job.uploads) {
        if (job.cancelled) return;
        if (job.uploaded.has(target.variant)) continue;
        const file = job.draft.files[target.variant];
        if (!file) throw new ApiError(0, "UNKNOWN");
        await deps.upload(target, file);
        job.uploaded.add(target.variant);
      }
      if (job.cancelled) return;
      try {
        await deps.complete(job.item.momentId);
      } catch (error: unknown) {
        // Lần báo trước đã tới server nhưng mất phản hồi: khoảnh khắc đã sang PROCESSING.
        if (!(error instanceof ApiError && error.code === "MOMENT_NOT_DRAFT")) throw error;
      }
      update(job, { stage: "processing", error: null });
      schedule(job, () => poll(job), deps.pollMs);
    } catch (error: unknown) {
      // Server báo thiếu file thì lần sau tải lại cả hai.
      if (error instanceof ApiError && error.code === "UPLOAD_INCOMPLETE") job.uploaded.clear();
      update(job, { stage: "upload-failed", error });
    }
  }

  async function poll(job: Job) {
    if (job.cancelled) return;
    let fresh: Moment;
    try {
      fresh = await deps.detail(job.item.momentId);
    } catch (error: unknown) {
      if (error instanceof ApiError && error.code === "NOT_FOUND") {
        // Đã bị xóa (ở tab khác) hoặc hết hạn: không còn gì để chờ.
        finish(job);
        return;
      }
      // Mất mạng lúc hỏi trạng thái: hỏi lại lượt sau, server vẫn đang xử lý.
      schedule(job, () => poll(job), deps.pollMs);
      return;
    }
    if (job.cancelled) return;
    if (fresh.status === "READY") {
      finish(job);
      publishedListeners.forEach((listener) => listener(fresh));
    } else if (fresh.status === "FAILED") {
      update(job, { stage: "processing-failed", failureReason: fresh.ownerView?.failureReason ?? null });
    } else {
      schedule(job, () => poll(job), deps.pollMs);
    }
  }

  async function resend(job: Job) {
    const expired = job.uploads.some(
      (target) => Date.parse(target.expiresAt) - UPLOAD_EXPIRY_MARGIN_MS <= deps.now(),
    );
    if (expired && job.uploaded.size < job.uploads.length) {
      try {
        // Không xin lại được URL cho bản nháp cũ (API mục 5): bỏ nó, tạo bản mới với cùng nội dung.
        await deps.remove(job.item.momentId).catch(() => undefined);
        adopt(job, await deps.create(job.draft.request));
      } catch (error: unknown) {
        update(job, { stage: "upload-failed", error });
        return;
      }
    }
    await send(job);
  }

  async function reprocess(job: Job) {
    try {
      await deps.retry(job.item.momentId);
      update(job, { stage: "processing", error: null, failureReason: null });
      schedule(job, () => poll(job), deps.pollMs);
    } catch (error: unknown) {
      update(job, { error });
    }
  }

  return {
    subscribe(listener: () => void): () => void {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },

    getSnapshot(): PendingMoment[] {
      return snapshot;
    },

    /** Báo khi một khoảnh khắc đã đăng xong (READY) để feed thêm vào. */
    onPublished(listener: (moment: Moment) => void): () => void {
      publishedListeners.add(listener);
      return () => {
        publishedListeners.delete(listener);
      };
    },

    /**
     * Tạo bản nháp rồi tải lên ở nền. Lỗi lúc tạo (chưa có bạn, người nhận không còn là bạn, mất mạng) ném cho trang
     * soạn báo ngay; sau khi tạo được thì mọi lỗi hiện trên thẻ mờ ở feed.
     */
    async submit(draft: PendingDraft): Promise<void> {
      const created = await deps.create(draft.request);
      const job: Job = {
        item: {
          localId: `pending-${nextLocalId++}`,
          momentId: created.moment.id,
          mediaType: draft.request.mediaType,
          previewUrl: draft.previewUrl,
          stage: "uploading",
          error: null,
          failureReason: null,
        },
        draft,
        uploads: created.uploads,
        uploaded: new Set(),
        timer: null,
        cancelled: false,
      };
      jobs.set(job.item.localId, job);
      emit();
      schedule(job, () => send(job), 0);
    },

    retry(localId: string) {
      const job = jobs.get(localId);
      if (!job) return;
      if (job.item.stage === "upload-failed") {
        update(job, { stage: "uploading", error: null });
        schedule(job, () => resend(job), 0);
      } else if (job.item.stage === "processing-failed") {
        schedule(job, () => reprocess(job), 0);
      }
    },

    /** US-28: hủy khoảnh khắc đang đăng; bản nháp hay khoảnh khắc lỗi trên server bị xóa luôn. */
    cancel(localId: string) {
      const job = jobs.get(localId);
      if (!job) return;
      finish(job);
      // Xóa không được (mất mạng) thì job dọn bản nháp và khoảnh khắc lỗi sẽ xóa sau (DB mục 9).
      deps.remove(job.item.momentId).catch(() => undefined);
    },
  };
}

export type PendingMoments = ReturnType<typeof createPendingMoments>;

export const pendingMoments: PendingMoments = createPendingMoments({
  create: momentsApi.create,
  complete: momentsApi.complete,
  retry: momentsApi.retry,
  detail: momentsApi.detail,
  remove: momentsApi.remove,
  upload: (target, file) => uploadFile(target, file),
  revokeUrl: (url) => URL.revokeObjectURL(url),
  now: () => Date.now(),
  pollMs: 2000,
});

/** Danh sách khoảnh khắc đang đăng, mới nhất trước. */
export function usePendingMoments(store: PendingMoments = pendingMoments): PendingMoment[] {
  return useSyncExternalStore(store.subscribe, store.getSnapshot, store.getSnapshot);
}
