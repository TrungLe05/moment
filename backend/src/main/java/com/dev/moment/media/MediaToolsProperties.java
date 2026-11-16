package com.dev.moment.media;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Đường dẫn ffmpeg, ffprobe và thời gian tối đa cho một lệnh (D-034). Mặc định tìm trong PATH;
 * container worker cài sẵn cả hai (ARCHITECTURE.md mục 6).
 */
@Validated
@ConfigurationProperties("moment.media")
public record MediaToolsProperties(
        @NotBlank String ffmpegPath,
        @NotBlank String ffprobePath,
        @NotNull Duration processTimeout) {
}
