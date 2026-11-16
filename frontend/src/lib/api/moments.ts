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

export const momentsApi = {
  // Con trỏ là chuỗi mờ do server cấp, client chỉ gửi lại (API mục 1).
  feed: (cursor: string | null) =>
    apiClient.request<MomentPage>(cursor ? `/feed?cursor=${encodeURIComponent(cursor)}` : "/feed", { auth: true }),

  detail: (id: string) => apiClient.request<Moment>(`/moments/${encodeURIComponent(id)}`, { auth: true }),
};
