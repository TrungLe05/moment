package com.dev.moment.media;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Chuẩn hóa media bằng ffmpeg (ARCHITECTURE.md mục 2: server không tin định dạng hay thời lượng client gửi).
 * File đầu vào do người dùng gửi nên mỗi lệnh đều:
 * ép đúng demuxer theo Content-Type đã kiểm tra (không để ffmpeg đoán, vì playlist HLS đội lốt video có thể
 * khiến ffmpeg đọc file cục bộ hay gọi URL), chỉ cho giao thức file, bỏ metadata (vị trí GPS), và có timeout.
 */
@Component
@EnableConfigurationProperties(MediaToolsProperties.class)
public class FfmpegTranscoder {

    private static final int MAX_LOG_BYTES = 4000;
    // Đọc output của tiến trình là việc chặn I/O, nên dùng thread ảo thay vì pool chung của CompletableFuture.
    private static final Executor OUTPUT_READERS =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("ffmpeg-output-", 0).factory());
    // Mp4 và MOV cùng dùng demuxer mov; WebM dùng demuxer matroska.
    private static final Map<String, String> VIDEO_DEMUXERS = Map.of(
            "video/mp4", "mov", "video/quicktime", "mov", "video/webm", "matroska");

    private final MediaToolsProperties properties;
    private final JsonMapper jsonMapper;

    FfmpegTranscoder(MediaToolsProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    /** Kích thước và thời lượng (mili giây, null với ảnh) của file media. */
    public record MediaInfo(int width, int height, Integer durationMs) {
    }

    /** Thông tin file đầu vào; file không đọc được theo đúng định dạng khai báo thì ném {@link UnreadableMediaException}. */
    public MediaInfo probe(Path input, String contentType) {
        List<String> command = new ArrayList<>(List.of(properties.ffprobePath(), "-v", "error",
                "-protocol_whitelist", "file"));
        command.addAll(demuxer(input, contentType));
        command.addAll(List.of("-show_entries", "stream=codec_type,width,height:format=duration", "-of", "json",
                fileUrl(input)));
        return parseProbe(run(command));
    }

    /** Ảnh JPEG cạnh dài tối đa maxLongSide, không phóng to ảnh nhỏ (DB mục 6). */
    public MediaInfo resizeImage(Path input, String contentType, Path outputJpeg, int maxLongSide) {
        List<String> command = ffmpeg();
        command.addAll(demuxer(input, contentType));
        command.addAll(List.of("-i", fileUrl(input), "-frames:v", "1", "-vf", fitLongSide(maxLongSide)));
        command.addAll(jpegOutput(outputJpeg));
        run(command);
        return still(outputJpeg);
    }

    /**
     * Video H.264 + AAC, cạnh ngắn tối đa maxShortSide (720p), cắt lengthMs từ startMs (BR-10).
     * Video không có tiếng thì bản ra cũng không có tiếng.
     */
    public MediaInfo transcodeVideo(Path input, String contentType, Path outputMp4, long startMs, long lengthMs,
            int maxShortSide) {
        String scale = String.format(Locale.ROOT,
                "scale=w='if(lte(iw,ih),trunc(min(%1$d,iw)/2)*2,-2)':h='if(lte(iw,ih),-2,trunc(min(%1$d,ih)/2)*2)'",
                maxShortSide);
        List<String> command = ffmpeg();
        command.addAll(demuxer(input, contentType));
        command.addAll(List.of("-ss", seconds(startMs), "-t", seconds(lengthMs), "-i", fileUrl(input),
                "-map", "0:v:0", "-map", "0:a:0?", "-vf", scale,
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", "-map_metadata", "-1",
                "-f", "mp4", "-y", fileUrl(outputMp4)));
        run(command);
        return probe(outputMp4, "video/mp4");
    }

    /** Khung hình đầu của video đã xử lý, JPEG cạnh dài tối đa maxLongSide (thumbnail video, DB mục 6). */
    public MediaInfo firstFrame(Path videoMp4, Path outputJpeg, int maxLongSide) {
        List<String> command = ffmpeg();
        command.addAll(demuxer(videoMp4, "video/mp4"));
        command.addAll(List.of("-i", fileUrl(videoMp4), "-frames:v", "1", "-vf", fitLongSide(maxLongSide)));
        command.addAll(jpegOutput(outputJpeg));
        run(command);
        return still(outputJpeg);
    }

    // ffprobe báo ảnh dài bằng một khung hình (vài chục mili giây); ảnh thì không có thời lượng.
    private MediaInfo still(Path outputJpeg) {
        MediaInfo info = probe(outputJpeg, "image/jpeg");
        return new MediaInfo(info.width(), info.height(), null);
    }

    private List<String> ffmpeg() {
        return new ArrayList<>(List.of(properties.ffmpegPath(), "-nostdin", "-hide_banner", "-loglevel", "error",
                "-protocol_whitelist", "file"));
    }

    private static List<String> jpegOutput(Path output) {
        return List.of("-map_metadata", "-1", "-q:v", "3", "-f", "image2", "-update", "1", "-y", fileUrl(output));
    }

    private static String fitLongSide(int maxLongSide) {
        return String.format(Locale.ROOT,
                "scale=w='if(gte(iw,ih),min(%1$d,iw),-1)':h='if(gte(iw,ih),-1,min(%1$d,ih))'", maxLongSide);
    }

    // Ảnh: demuxer image2 chỉ đọc đúng một file, codec theo đuôi file do server đặt từ Content-Type (MediaRules).
    private static List<String> demuxer(Path input, String contentType) {
        if (contentType.startsWith("image/") && MediaRules.photoExtension(contentType)
                .filter(extension -> input.getFileName().toString().endsWith("." + extension)).isPresent()) {
            return List.of("-f", "image2", "-pattern_type", "none");
        }
        String video = VIDEO_DEMUXERS.get(contentType);
        if (video == null) {
            throw new UnreadableMediaException("Định dạng không hỗ trợ: " + contentType);
        }
        return List.of("-f", video);
    }

    private static String fileUrl(Path path) {
        return "file:" + path.toAbsolutePath();
    }

    private static String seconds(long millis) {
        return String.format(Locale.ROOT, "%d.%03d", millis / 1000, millis % 1000);
    }

    private MediaInfo parseProbe(String json) {
        JsonNode root;
        try {
            root = jsonMapper.readTree(json);
        } catch (JacksonException malformed) {
            throw new UnreadableMediaException("ffprobe trả kết quả không đọc được", malformed);
        }
        for (JsonNode stream : root.path("streams")) {
            if ("video".equals(stream.path("codec_type").asString(""))
                    && stream.path("width").asInt(0) > 0 && stream.path("height").asInt(0) > 0) {
                return new MediaInfo(stream.get("width").asInt(), stream.get("height").asInt(),
                        durationMs(root.path("format").path("duration").asString("")));
            }
        }
        throw new UnreadableMediaException("Không có luồng hình");
    }

    private static Integer durationMs(String seconds) {
        try {
            return (int) Math.round(Double.parseDouble(seconds) * 1000);
        } catch (NumberFormatException notVideo) {
            // Ảnh không có thời lượng ("N/A" hoặc không có trường).
            return null;
        }
    }

    /**
     * Chạy lệnh, trả stdout. Mã thoát khác 0 nghĩa là ffmpeg không đọc hay chuyển được file:
     * với đầu vào đã ép demuxer, đó là lỗi của file nên không thử lại. Không chạy được lệnh hoặc quá giờ là lỗi tạm thời.
     */
    private String run(List<String> command) {
        Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException exception) {
            throw new UncheckedIOException("Không chạy được " + command.getFirst(), exception);
        }
        // Đọc stdout và stderr song song để tiến trình không bị treo vì đầy bộ đệm.
        CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> readLimited(process.getInputStream()),
                OUTPUT_READERS);
        CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readLimited(process.getErrorStream()),
                OUTPUT_READERS);
        try {
            if (!process.waitFor(properties.processTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("Quá thời gian chạy " + command.getFirst());
            }
            if (process.exitValue() != 0) {
                throw new UnreadableMediaException(command.getFirst() + " thoát mã " + process.exitValue() + ": "
                        + stderr.get(5, TimeUnit.SECONDS).strip());
            }
            return stdout.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bị dừng khi chạy " + command.getFirst(), interrupted);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("Không đọc được kết quả " + command.getFirst(), exception);
        }
    }

    // Giữ tối đa MAX_LOG_BYTES, phần còn lại đọc bỏ: file hỏng có thể làm ffmpeg in rất nhiều lỗi.
    private static String readLimited(InputStream stream) {
        try (stream) {
            byte[] kept = new byte[MAX_LOG_BYTES];
            int length = 0;
            byte[] buffer = new byte[8192];
            for (int read; (read = stream.read(buffer)) != -1; ) {
                int copy = Math.min(read, MAX_LOG_BYTES - length);
                System.arraycopy(buffer, 0, kept, length, copy);
                length += copy;
            }
            return new String(kept, 0, length, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
