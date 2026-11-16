package com.dev.moment.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Sinh ảnh và video mẫu bằng ffmpeg lúc chạy test (nguồn lavfi), nên repo không chứa file media nhị phân.
 * Cần ffmpeg trong PATH hoặc biến FFMPEG_PATH, giống worker (D-034).
 */
public final class TestMedia {

    private static final String FFMPEG = Optional.ofNullable(System.getenv("FFMPEG_PATH")).orElse("ffmpeg");

    private TestMedia() {
    }

    /** Ảnh một khung hình; định dạng theo đuôi: jpg, png, webp. */
    public static byte[] image(int width, int height, String extension) {
        return generate(extension, List.of("-f", "lavfi", "-i", "testsrc2=size=" + width + "x" + height,
                "-frames:v", "1"));
    }

    /** Video mp4 H.264, có tiếng nếu cần; mã hóa nhanh nhất vì chỉ để làm đầu vào. */
    public static byte[] video(int width, int height, int seconds, boolean withAudio) {
        List<String> args = new ArrayList<>(List.of("-f", "lavfi", "-i",
                "testsrc2=size=" + width + "x" + height + ":rate=" + (width <= 128 ? 5 : 30) + ":duration=" + seconds));
        if (withAudio) {
            args.addAll(List.of("-f", "lavfi", "-i", "sine=duration=" + seconds, "-c:a", "aac", "-shortest"));
        }
        args.addAll(List.of("-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p"));
        return generate("mp4", args);
    }

    private static byte[] generate(String extension, List<String> args) {
        Path directory = null;
        try {
            directory = Files.createTempDirectory("moment-test-media-");
            Path output = directory.resolve("sample." + extension);
            List<String> command = new ArrayList<>(List.of(FFMPEG, "-nostdin", "-hide_banner", "-loglevel", "error"));
            command.addAll(args);
            command.addAll(List.of("-y", output.toString()));
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String log = new String(process.getInputStream().readAllBytes());
            if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IllegalStateException("ffmpeg không tạo được file mẫu: " + log);
            }
            return Files.readAllBytes(output);
        } catch (IOException exception) {
            throw new IllegalStateException("Cần ffmpeg để chạy test xử lý media (D-034)", exception);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        } finally {
            deleteQuietly(directory);
        }
    }

    private static void deleteQuietly(Path directory) {
        if (directory == null) {
            return;
        }
        try (var files = Files.list(directory)) {
            for (Path file : files.toList()) {
                Files.deleteIfExists(file);
            }
            Files.deleteIfExists(directory);
        } catch (IOException ignored) {
            // Thư mục tạm của test; hệ điều hành tự dọn nếu xóa không được.
        }
    }
}
