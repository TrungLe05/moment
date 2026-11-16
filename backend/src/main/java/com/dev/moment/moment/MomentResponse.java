package com.dev.moment.moment;

import com.dev.moment.user.UserSummary;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * Đối tượng khoảnh khắc trả về (danh-sach-api.md mục 4). {@code media} chỉ có khi READY;
 * {@code ownerView} chỉ có với chủ; {@code myReaction} chỉ có với người xem khác chủ (làm ở T-143).
 */
record MomentResponse(
        UUID id,
        boolean isMine,
        UserSummary owner,
        String mediaType,
        String source,
        String mode,
        String status,
        String caption,
        JsonNode layers,
        int layersVersion,
        Integer width,
        Integer height,
        Integer durationMs,
        Instant createdAt,
        Instant expiresAt,
        Instant keptAt,
        MediaUrls media,
        String myReaction,
        OwnerView ownerView) {

    record MediaUrls(String displayUrl, String videoUrl, String thumbnailUrl, Instant urlsExpireAt) {
    }

    record OwnerView(String audience, int recipientCount, int reactionCount, String failureReason) {
    }

    record Upload(String variant, String method, String url, java.util.Map<String, String> headers,
            Instant expiresAt) {
    }

    record Created(MomentResponse moment, java.util.List<Upload> uploads) {
    }

    /** Một trang feed hoặc kho; nextCursor null khi hết (API mục 1). */
    record Page(java.util.List<MomentResponse> items, String nextCursor) {
    }
}
