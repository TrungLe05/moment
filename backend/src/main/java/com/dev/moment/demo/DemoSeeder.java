package com.dev.moment.demo;

import com.dev.moment.job.MediaJobService;
import com.dev.moment.media.ObjectStorage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Dựng lại dữ liệu tài khoản demo (US-51, D-027, D-045): tài khoản demo, 3 người bạn demo, 5 khoảnh khắc ảnh (vừa
 * chia sẻ và lưu giữ), vài reaction. Ghi thẳng bằng SQL như dữ liệu migration vì cần mốc thời gian trong quá khứ mà
 * luồng nghiệp vụ không cho tạo; ảnh vẫn qua worker như một lần đăng thật (ADR 0003). File trên storage tải lên ngoài
 * transaction, file cũ xóa qua việc DELETE_OBJECTS (CLAUDE.md).
 */
@Component
public class DemoSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);
    private static final Duration FEED_WINDOW = Duration.ofHours(24);
    private static final String JPEG = "image/jpeg";

    /** Bạn demo không đăng nhập được: không có mật khẩu, email đuôi .invalid không bao giờ nhận thư. */
    private record Person(String key, String username, String displayName) {
    }

    private record Planned(String owner, String image, Duration age, boolean kept, Map<String, String> reactions) {
    }

    private static final List<Person> PEOPLE = List.of(
            new Person("me", "demo", "Bạn Demo"),
            new Person("minhanh", "demo.minhanh", "Minh Anh"),
            new Person("huy", "demo.huy", "Huy"),
            new Person("linh", "demo.linh", "Linh"));

    // Feed có khoảnh khắc mới của bạn bè và của mình; kho có khoảnh khắc lưu giữ của mình và được gửi.
    private static final List<Planned> MOMENTS = List.of(
            new Planned("minhanh", "sunrise.jpg", Duration.ofHours(2), false, Map.of()),
            new Planned("huy", "coffee.jpg", Duration.ofHours(5), false, Map.of()),
            new Planned("me", "hills.jpg", Duration.ofHours(1), false, Map.of("linh", "😍")),
            new Planned("linh", "lake.jpg", Duration.ofDays(3), true, Map.of()),
            new Planned("me", "dusk.jpg", Duration.ofDays(10), true, orderedReactions()));

    private static Map<String, String> orderedReactions() {
        Map<String, String> reactions = new LinkedHashMap<>();
        reactions.put("minhanh", "❤️");
        reactions.put("huy", "🔥");
        return reactions;
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final ObjectStorage storage;
    private final MediaJobService mediaJobService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    DemoSeeder(JdbcTemplate jdbc, TransactionTemplate transactionTemplate, ObjectStorage storage,
            MediaJobService mediaJobService, PasswordEncoder passwordEncoder, Clock clock) {
        this.jdbc = jdbc;
        this.transactionTemplate = transactionTemplate;
        this.storage = storage;
        this.mediaJobService = mediaJobService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** Xóa sạch dữ liệu demo cũ rồi dựng lại; dữ liệu của người dùng thật không bị đụng tới. */
    public void reseed(String email, String password) {
        Instant now = clock.instant();
        String passwordHash = passwordEncoder.encode(password);
        transactionTemplate.executeWithoutResult(status -> removeDemoData());

        // Tải ảnh lên trước, ngoài transaction; nếu bước ghi database hỏng thì file mồ côi bị job dọn bản nháp không
        // thấy, nên xóa ngay qua hàng đợi ở nhánh lỗi bên dưới.
        List<Upload> uploads = new ArrayList<>();
        for (Planned planned : MOMENTS) {
            uploads.add(upload(planned));
        }
        try {
            transactionTemplate.executeWithoutResult(status -> insertDemoData(email, passwordHash, uploads, now));
        } catch (RuntimeException failure) {
            transactionTemplate.executeWithoutResult(status -> mediaJobService.enqueueDeleteObjects(null,
                    uploads.stream().flatMap(upload -> List.of(upload.originalKey(), upload.composedKey()).stream())
                            .toList()));
            throw failure;
        }
        log.info("Đã dựng lại dữ liệu demo: {} người dùng, {} khoảnh khắc", PEOPLE.size(), MOMENTS.size());
    }

    private void removeDemoData() {
        List<String> keys = jdbc.queryForList("""
                SELECT f.object_key FROM moment_media f JOIN moments m ON m.id = f.moment_id
                JOIN users u ON u.id = m.owner_id WHERE u.is_demo
                """, String.class);
        // Người nhận, bạn bè, reaction, token của người dùng demo xóa theo khóa ngoại ON DELETE CASCADE.
        jdbc.update("DELETE FROM users WHERE is_demo");
        mediaJobService.enqueueDeleteObjects(null, keys);
    }

    private record Upload(Planned planned, UUID momentId, String originalKey, String composedKey, long sizeBytes) {
    }

    private Upload upload(Planned planned) {
        UUID momentId = UUID.randomUUID();
        Path file = copyResource(planned.image());
        try {
            long size = Files.size(file);
            String originalKey = objectKey(momentId, "original");
            String composedKey = objectKey(momentId, "composed");
            // Ảnh demo không có lớp trang trí: bản ghép chính là ảnh gốc (ADR 0002).
            storage.upload(originalKey, file, JPEG);
            storage.upload(composedKey, file, JPEG);
            return new Upload(planned, momentId, originalKey, composedKey, size);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } finally {
            deleteQuietly(file);
        }
    }

    private void insertDemoData(String email, String passwordHash, List<Upload> uploads, Instant now) {
        Map<String, UUID> ids = new LinkedHashMap<>();
        for (Person person : PEOPLE) {
            boolean me = person.key().equals("me");
            UUID id = jdbc.queryForObject("""
                    INSERT INTO users (email, email_verified_at, password_hash, username, display_name, locale, is_demo)
                    VALUES (?, ?, ?, ?, ?, 'vi', true) RETURNING id
                    """, UUID.class, me ? email : person.username() + "@demo.invalid", Timestamp.from(now),
                    me ? passwordHash : null, person.username(), person.displayName());
            ids.put(person.key(), id);
        }
        // Mọi người demo là bạn của nhau.
        List<UUID> all = new ArrayList<>(ids.values());
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                jdbc.update("INSERT INTO friendships (user_a_id, user_b_id) VALUES (LEAST(?, ?), GREATEST(?, ?))",
                        all.get(i), all.get(j), all.get(i), all.get(j));
            }
        }
        for (Upload upload : uploads) {
            insertMoment(upload, ids, now);
        }
    }

    private void insertMoment(Upload upload, Map<String, UUID> ids, Instant now) {
        Planned planned = upload.planned();
        UUID ownerId = ids.get(planned.owner());
        Instant createdAt = now.minus(planned.age());
        jdbc.update("""
                INSERT INTO moments (id, owner_id, media_type, source, mode, audience, status, created_at, expires_at,
                    kept_at)
                VALUES (?, ?, 'PHOTO', 'CAMERA', ?, 'ALL', 'PROCESSING', ?, ?, ?)
                """, upload.momentId(), ownerId, planned.kept() ? "KEPT" : "SHARED", Timestamp.from(createdAt),
                Timestamp.from(createdAt.plus(FEED_WINDOW)), planned.kept() ? Timestamp.from(createdAt) : null);
        for (UUID recipient : ids.values()) {
            if (!recipient.equals(ownerId)) {
                jdbc.update("INSERT INTO moment_recipients (moment_id, recipient_id, created_at) VALUES (?, ?, ?)",
                        upload.momentId(), recipient, Timestamp.from(createdAt));
            }
        }
        for (Map.Entry<String, String> variant : Map.of("ORIGINAL", upload.originalKey(), "COMPOSED",
                upload.composedKey()).entrySet()) {
            jdbc.update("""
                    INSERT INTO moment_media (moment_id, variant, object_key, content_type, size_bytes)
                    VALUES (?, ?, ?, ?, ?)
                    """, upload.momentId(), variant.getKey(), variant.getValue(), JPEG, upload.sizeBytes());
        }
        planned.reactions().forEach((who, emoji) -> jdbc.update(
                "INSERT INTO reactions (moment_id, user_id, emoji) VALUES (?, ?, ?)", upload.momentId(),
                ids.get(who), emoji));
        mediaJobService.enqueueProcessMoment(upload.momentId());
    }

    // Cùng dạng khóa với khoảnh khắc thật (DB mục 6): phần uuid ngẫu nhiên để không ai đoán được khóa.
    private static String objectKey(UUID momentId, String variant) {
        return "moments/" + momentId + "/" + variant + "-" + UUID.randomUUID() + ".jpg";
    }

    private static Path copyResource(String name) {
        try (InputStream input = new ClassPathResource("demo/" + name).getInputStream()) {
            Path file = Files.createTempFile("moment-demo-", ".jpg");
            Files.copy(input, file, StandardCopyOption.REPLACE_EXISTING);
            return file;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            log.warn("Không xóa được file tạm {}", file, exception);
        }
    }
}
