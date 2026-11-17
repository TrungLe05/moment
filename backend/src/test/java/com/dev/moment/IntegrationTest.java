package com.dev.moment;

import com.dev.moment.support.TestSupportConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Integration test chạy với PostgreSQL thật qua Testcontainers (CLAUDE.md, kiểm thử).
 * Dùng chung một cấu hình để Spring giữ lại context giữa các lớp test.
 * Khóa JWT sinh ngẫu nhiên mỗi lần chạy: hai chuỗi hex 32 ký tự của {@code random.value} ghép lại
 * là base64 hợp lệ dài 48 byte, nên không có khóa nào nằm cứng trong code.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
        "moment.auth.jwt-signing-key=${random.value}${random.value}",
        "moment.auth.google-client-id=test-client-id",
        "moment.auth.frontend-base-url=http://localhost:3000",
        // Job định kỳ chạy ngầm sẽ đổi dữ liệu test đang dựng; test gọi thẳng từng job (T-161).
        "moment.housekeeping.enabled=false"
})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestSupportConfiguration.class})
public @interface IntegrationTest {
}
