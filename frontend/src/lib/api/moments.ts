import type { UserSummary } from "./friends";
import { apiClient } from "./index";

// Đối tượng khoảnh khắc trả về (danh-sach-api.md mục 4).

export type MomentStatus = "DRAFT" | "PROCESSING" | "READY" | "FAILED" | "EXPIRED";

export type MomentMedia = {
  displayUrl: string | null;
  videoUrl: string | null;
  thumbnailUrl: string | null;
  urlsExpireAt: string;
};

export type OwnerView = {
  audience: "ALL" | "SELECTED";
  recipientCount: number;
  reactionCount: number;
  failureReason: string | null;
};

export type Moment = {
  id: string;
  isMine: boolean;
  owner: UserSummary;
  mediaType: "PHOTO" | "VIDEO";
  source: "CAMERA" | "UPLOAD";
  mode: "SHARED" | "KEPT";
  status: MomentStatus;
  caption: string | null;
  layers: unknown[];
  layersVersion: number;
  width: number | null;
  height: number | null;
  durationMs: number | null;
  createdAt: string;
  expiresAt: string;
  keptAt: string | null;
  media: MomentMedia | null;
  myReaction: string | null;
  ownerView: OwnerView | null;
};

export type MomentPage = { items: Moment[]; nextCursor: string | null };

export const ARCHIVE_SCOPES = ["mine", "received"] as const;

export type ArchiveScope = (typeof ARCHIVE_SCOPES)[number];

/** Bộ emoji cố định (BR-21), đúng dạng chuẩn server lưu (D-028). */
export const REACTION_EMOJIS = ["❤️", "😍", "🔥", "😂", "😮", "😢"] as const;

export type ReactionEmoji = (typeof REACTION_EMOJIS)[number];

export type ReactionItem = { user: UserSummary; emoji: string; reactedAt: string };

export const momentsApi = {
  // Con trỏ là chuỗi mờ do server cấp, client chỉ gửi lại (API mục 1).
  feed: (cursor: string | null) =>
    apiClient.request<MomentPage>(cursor ? `/feed?cursor=${encodeURIComponent(cursor)}` : "/feed", { auth: true }),

  // Kho (API mục 3.4): "mine" là tab "Của tôi", "received" là tab "Được gửi" (US-40, US-41).
  archive: (scope: ArchiveScope, cursor: string | null) =>
    apiClient.request<MomentPage>(
      `/archive?scope=${scope}${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ""}`,
      { auth: true },
    ),

  detail: (id: string) => apiClient.request<Moment>(`/moments/${encodeURIComponent(id)}`, { auth: true }),

  // Chỉ chủ (API mục 3.4): lưu giữ trả khoảnh khắc đã đổi; xóa trả 204.
  keep: (id: string) =>
    apiClient.request<Moment>(`/moments/${encodeURIComponent(id)}/keep`, { method: "POST", auth: true }),

  remove: (id: string) =>
    apiClient.request<void>(`/moments/${encodeURIComponent(id)}`, { method: "DELETE", auth: true }),

  // Reaction (API mục 3.5): đặt hoặc đổi; "thả lại đúng emoji thì gỡ" do client gọi unreact (BR-21).
  react: (id: string, emoji: ReactionEmoji) =>
    apiClient.request<{ emoji: string }>(`/moments/${encodeURIComponent(id)}/reaction`, {
      method: "PUT",
      body: { emoji },
      auth: true,
    }),

  unreact: (id: string) =>
    apiClient.request<void>(`/moments/${encodeURIComponent(id)}/reaction`, { method: "DELETE", auth: true }),

  // Chỉ chủ gọi được (BR-22); người khác nhận 404.
  reactions: async (id: string) =>
    (await apiClient.request<{ items: ReactionItem[] }>(`/moments/${encodeURIComponent(id)}/reactions`, { auth: true }))
      .items,
};
