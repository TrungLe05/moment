package com.dev.moment.reaction;

import com.dev.moment.user.UserSummary;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/** Request và response của reaction (danh-sach-api.md mục 3.5). */
final class ReactionResponses {

    private ReactionResponses() {
    }

    // Emoji kiểm tra theo bộ cố định ở service sau khi chuẩn hóa (D-028), nên ở đây chỉ bắt buộc có trường.
    record ReactRequest(@NotNull String emoji) {
    }

    record Mine(String emoji) {
    }

    record Item(UserSummary user, String emoji, Instant reactedAt) {
    }

    record Items(List<Item> items) {
    }
}
