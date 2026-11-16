package com.dev.moment.moment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Body của POST /moments (danh-sach-api.md mục 3.4). Bean Validation chỉ kiểm tra định dạng;
 * giới hạn theo BR-08 → BR-12 kiểm tra trong {@link MomentService} và {@link LayerRules}.
 */
record CreateMomentRequest(
        @NotNull Moment.MediaType mediaType,
        @NotNull Moment.Source source,
        @NotNull Moment.Mode mode,
        @NotNull Moment.Audience audience,
        List<UUID> recipientIds,
        String caption,
        @Valid @Size(max = 100) List<Layer> layers,
        Short layersVersion,
        @PositiveOrZero Integer trimStartMs,
        @NotNull @Size(min = 1, max = 2) @Valid List<FileDeclaration> files) {

    record Layer(String id, String type, String text, String bannerId, Double x, Double y, Double scale,
            Double rotation, String style) {
    }

    record FileDeclaration(@NotNull MomentMedia.Variant variant, @NotNull String contentType,
            @NotNull Long sizeBytes) {
    }
}
