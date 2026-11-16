package com.dev.moment.reaction;

import java.util.Optional;
import java.util.Set;

/**
 * Bộ emoji cố định của BR-21, dạng chuẩn khớp ràng buộc CHECK của bảng reactions.
 * D-028: chuẩn hóa trước khi kiểm tra, vì cùng một emoji có thể đến kèm hoặc thiếu U+FE0F (variation selector-16)
 * tùy bàn phím: ❤ (U+2764) thành ❤️, còn 😍 🔥 😂 😮 😢 vốn hiện dạng emoji nên bỏ U+FE0F thừa.
 */
final class ReactionEmoji {

    private static final String VARIATION_SELECTOR_16 = "️";
    private static final String HEART = "❤";
    static final Set<String> ALLOWED = Set.of(HEART + VARIATION_SELECTOR_16, "😍", "🔥", "😂", "😮", "😢");

    private ReactionEmoji() {
    }

    /** Dạng chuẩn của emoji, hoặc rỗng nếu không thuộc bộ cố định. */
    static Optional<String> normalize(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String bare = input.strip().replace(VARIATION_SELECTOR_16, "");
        String canonical = HEART.equals(bare) ? HEART + VARIATION_SELECTOR_16 : bare;
        return ALLOWED.contains(canonical) ? Optional.of(canonical) : Optional.empty();
    }
}
