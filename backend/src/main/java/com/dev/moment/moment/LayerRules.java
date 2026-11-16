package com.dev.moment.moment;

import com.dev.moment.common.error.FieldViolation;
import com.dev.moment.common.text.GraphemeCounter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Kiểm tra caption và lớp trang trí theo BR-11 và thiết kế database mục 5. */
final class LayerRules {

    static final int MAX_CAPTION_CHARACTERS = 80;
    static final int MAX_CAPTION_LINES = 3;
    static final int MAX_LAYERS = 20;
    static final int MAX_LAYER_TEXT_CHARACTERS = 40;
    private static final Set<String> TYPES = Set.of("caption", "sticker", "banner");
    // style và bannerId là mã do client định nghĩa; server chỉ kiểm tra là chuỗi ngắn hợp lệ.
    private static final Pattern CLIENT_CODE = Pattern.compile("^[a-z0-9-]{1,40}$");
    private static final Pattern LAYER_ID = Pattern.compile("^[A-Za-z0-9_-]{1,40}$");

    private LayerRules() {
    }

    static List<FieldViolation> check(String caption, List<CreateMomentRequest.Layer> layers) {
        List<FieldViolation> violations = new ArrayList<>();
        checkCaption("caption", caption, violations);
        if (layers.size() > MAX_LAYERS) {
            violations.add(new FieldViolation("layers", "TOO_LONG", Map.of("max", MAX_LAYERS)));
            return violations;
        }
        long captionLayers = layers.stream().filter(layer -> "caption".equals(layer.type())).count();
        if (captionLayers > 1) {
            violations.add(FieldViolation.of("layers", "INVALID"));
        }
        for (int i = 0; i < layers.size(); i++) {
            checkLayer("layers[" + i + "]", layers.get(i), violations);
        }
        return violations;
    }

    /** Chữ của lớp caption, nếu có; cột caption chép lại chữ này (thiết kế database mục 5). */
    static String captionText(String caption, List<CreateMomentRequest.Layer> layers) {
        return layers.stream()
                .filter(layer -> "caption".equals(layer.type()))
                .map(CreateMomentRequest.Layer::text)
                .findFirst()
                .orElse(caption);
    }

    private static void checkLayer(String field, CreateMomentRequest.Layer layer, List<FieldViolation> violations) {
        if (layer.id() == null || !LAYER_ID.matcher(layer.id()).matches()) {
            violations.add(FieldViolation.of(field + ".id", "INVALID_FORMAT"));
        }
        if (layer.type() == null || !TYPES.contains(layer.type())) {
            violations.add(FieldViolation.of(field + ".type", "INVALID"));
            return;
        }
        switch (layer.type()) {
            case "caption" -> checkCaption(field + ".text", layer.text(), violations);
            case "sticker" -> checkText(field + ".text", layer.text(), violations);
            default -> {
                if (layer.bannerId() == null || !CLIENT_CODE.matcher(layer.bannerId()).matches()) {
                    violations.add(FieldViolation.of(field + ".bannerId", "INVALID_FORMAT"));
                }
                if (layer.text() != null) {
                    checkText(field + ".text", layer.text(), violations);
                }
            }
        }
        if (layer.style() != null && !CLIENT_CODE.matcher(layer.style()).matches()) {
            violations.add(FieldViolation.of(field + ".style", "INVALID_FORMAT"));
        }
        checkRange(field + ".x", layer.x(), 0, 1, violations);
        checkRange(field + ".y", layer.y(), 0, 1, violations);
        checkRange(field + ".scale", layer.scale(), 0.2, 5, violations);
        checkRange(field + ".rotation", layer.rotation(), -180, 180, violations);
    }

    private static void checkCaption(String field, String text, List<FieldViolation> violations) {
        if (text == null) {
            return;
        }
        if (GraphemeCounter.count(text) > MAX_CAPTION_CHARACTERS) {
            violations.add(new FieldViolation(field, "TOO_LONG", Map.of("max", MAX_CAPTION_CHARACTERS)));
        }
        if (text.lines().count() > MAX_CAPTION_LINES) {
            violations.add(FieldViolation.of(field, "INVALID"));
        }
    }

    private static void checkText(String field, String text, List<FieldViolation> violations) {
        if (text == null || text.isBlank()) {
            violations.add(FieldViolation.of(field, "REQUIRED"));
        } else if (GraphemeCounter.count(text) > MAX_LAYER_TEXT_CHARACTERS) {
            violations.add(new FieldViolation(field, "TOO_LONG", Map.of("max", MAX_LAYER_TEXT_CHARACTERS)));
        }
    }

    private static void checkRange(String field, Double value, double min, double max,
            List<FieldViolation> violations) {
        if (value == null || value.isNaN() || value < min || value > max) {
            violations.add(FieldViolation.of(field, "INVALID"));
        }
    }
}
