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
    // Danh sách gói tin của một video tối đa 60 giây chỉ vài chục KB; vượt mức này coi như file bất thường.
    private static final int MAX_PACKET_LIST_BYTES = 1024 * 1024;
    /**
     * D-033: ảnh hay video lớn hơn 50 megapixel bị coi là không đọc được, để một file nén nhỏ nhưng kích thước khổng lồ
     * không làm worker tốn hàng GB RAM khi giải mã. Bản ghép xuất từ canvas của trình duyệt nhỏ hơn nhiều mức này.
     */
    static final long MAX_PIXELS = 50_000_000L;
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

    /**
     * Kiểm tra file người dùng gửi trước khi xử lý. File không đọc được theo đúng định dạng khai báo, hoặc quá
     * {@link #MAX_PIXELS}, thì ném {@link UnreadableMediaException} (không thử lại). Sau bước này, lỗi của ffmpeg
     * được coi là tạm thời (hết đĩa, tiến trình bị dừng) và được thử lại có giới hạn.
     */
    public MediaInfo probe(Path input, String contentType) {
        MediaInfo info;
        try {
            info = inspect(input, contentType);
            if (info.durationMs() == null && !contentType.startsWith("image/")) {
                info = new MediaInfo(info.width(), info.height(), durationFromPackets(input, contentType));
            }
        } catch (ToolFailure failure) {
            throw new UnreadableMediaException(failure.getMessage(), failure);
        }
        if ((long) info.width() * info.height() > MAX_PIXELS) {
            throw new UnreadableMediaException("Kích thước quá lớn: " + info.width() + "x" + info.height());
        }
        return info;
    }

    private MediaInfo inspect(Path file, String contentType) {
        List<String> command = ffprobe(file, contentType);
        command.addAll(List.of("-show_entries", "stream=codec_type,width,height:format=duration", "-of", "json",
                fileUrl(file)));
        return parseProbe(run(command, MAX_LOG_BYTES));
    }

    /**
     * Video ghi bằng MediaRecorder của trình duyệt (WebM) thường không ghi thời lượng vào header, ffprobe báo N/A.
     * Khi đó đọc mốc thời gian của từng gói tin hình (không giải mã) để biết video dài bao nhiêu.
     */
    private Integer durationFromPackets(Path input, String contentType) {
        List<String> command = ffprobe(input, contentType);
        command.addAll(List.of("-select_streams", "v:0", "-show_entries", "packet=pts_time,duration_time",
                "-of", "csv=p=0", fileUrl(input)));
        String packets = run(command, MAX_PACKET_LIST_BYTES);
        if (packets.length() >= MAX_PACKET_LIST_BYTES) {
            throw new ToolFailure("Quá nhiều gói tin hình");
        }
        double endSeconds = 0;
        for (String line : packets.split("\\R")) {
            String[] fields = line.strip().split(",");
            Double pts = number(fields[0]);
            if (pts != null) {
                Double duration = fields.length > 1 ? number(fields[1]) : null;
                endSeconds = Math.max(endSeconds, pts + (duration == null ? 0 : duration));
            }
        }
        if (endSeconds <= 0) {
            throw new ToolFailure("Không đọc được thời lượng video");
        }
        return (int) Math.round(endSeconds * 1000);
    }

    private List<String> ffprobe(Path file, String contentType) {
        List<String> command = new ArrayList<>(List.of(properties.ffprobePath(), "-v", "error",
                "-protocol_whitelist", "file"));
        command.addAll(demuxer(file, contentType));
        return command;
    }

    /** Ảnh JPEG cạnh dài tối đa maxLongSide, không phóng to ảnh nhỏ (DB mục 6). */
    public MediaInfo resizeImage(Path input, String contentType, Path outputJpeg, int maxLongSide) {
        List<String> command = ffmpeg();
        command.addAll(demuxer(input, contentType));
        command.addAll(List.of("-i", fileUrl(input), "-frames:v", "1", "-vf", fitLongSide(maxLongSide)));
        command.addAll(jpegOutput(outputJpeg));
        run(command, MAX_LOG_BYTES);
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
        run(command, MAX_LOG_BYTES);
        return inspect(outputMp4, "video/mp4");
    }

    /** Khung hình đầu của video đã xử lý, JPEG cạnh dài tối đa maxLongSide (thumbnail video, DB mục 6). */
    public MediaInfo firstFrame(Path videoMp4, Path outputJpeg, int maxLongSide) {
        List<String> command = ffmpeg();
        command.addAll(demuxer(videoMp4, "video/mp4"));
        command.addAll(List.of("-i", fileUrl(videoMp4), "-frames:v", "1", "-vf", fitLongSide(maxLongSide)));
        command.addAll(jpegOutput(outputJpeg));
        run(command, MAX_LOG_BYTES);
        return still(outputJpeg);
    }

    // ffprobe báo ảnh dài bằng một khung hình (vài chục mili giây); ảnh thì không có thời lượng.
    private MediaInfo still(Path outputJpeg) {
        MediaInfo info = inspect(outputJpeg, "image/jpeg");
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
            throw new ToolFailure("ffprobe trả kết quả không đọc được", malformed);
        }
        for (JsonNode stream : root.path("streams")) {
            if ("video".equals(stream.path("codec_type").asString(""))
                    && stream.path("width").asInt(0) > 0 && stream.path("height").asInt(0) > 0) {
                return new MediaInfo(stream.get("width").asInt(), stream.get("height").asInt(),
                        durationMs(root.path("format").path("duration").asString("")));
            }
        }
        throw new ToolFailure("Không có luồng hình");
    }

    private static Integer durationMs(String seconds) {
        Double value = number(seconds);
        return value == null ? null : (int) Math.round(value * 1000);
    }

    // ffprobe ghi "N/A" hoặc bỏ trống khi không có giá trị (ảnh không có thời lượng, gói tin thiếu mốc thời gian).
    private static Double number(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    /** Chạy lệnh, trả stdout (tối đa stdoutLimit byte). Mã thoát khác 0 ném {@link ToolFailure}. */
    private String run(List<String> command, int stdoutLimit) {
        Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException exception) {
            throw new UncheckedIOException("Không chạy được " + command.getFirst(), exception);
        }
        // Đọc stdout và stderr song song để tiến trình không bị treo vì đầy bộ đệm.
        CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> readLimited(process.getInputStream(), stdoutLimit),
                OUTPUT_READERS);
        CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readLimited(process.getErrorStream(), MAX_LOG_BYTES),
                OUTPUT_READERS);
        try {
            if (!process.waitFor(properties.processTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                // Chờ tiến trình dừng hẳn để bên gọi xóa thư mục tạm khi không còn ai giữ file.
                process.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
                throw new IllegalStateException("Quá thời gian chạy " + command.getFirst());
            }
            if (process.exitValue() != 0) {
                throw new ToolFailure(command.getFirst() + " thoát mã " + process.exitValue() + ": "
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

    // Giữ tối đa limit byte, phần còn lại đọc bỏ: file hỏng có thể làm ffmpeg in rất nhiều lỗi.
    private static String readLimited(InputStream stream, int limit) {
        try (stream) {
            byte[] kept = new byte[limit];
            int length = 0;
            byte[] buffer = new byte[8192];
            for (int read; (read = stream.read(buffer)) != -1; ) {
                int copy = Math.min(read, limit - length);
                System.arraycopy(buffer, 0, kept, length, copy);
                length += copy;
            }
            return new String(kept, 0, length, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** ffmpeg hoặc ffprobe chạy lỗi. Mặc định là lỗi tạm thời; {@link #probe} đổi thành lỗi của file. */
    private static final class ToolFailure extends RuntimeException {

        ToolFailure(String message) {
            super(message);
        }

        ToolFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
