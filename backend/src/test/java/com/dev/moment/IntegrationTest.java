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
 * Khóa JWT dưới đây chỉ dùng cho test, không phải secret thật.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
        "moment.auth.jwt-signing-key=dGVzdC1vbmx5LWp3dC1rZXktbm90LWEtcmVhbC1zZWNyZXQtMDEyMzQ1Njc4OQ==",
        "moment.auth.google-client-id=test-client-id",
        "moment.auth.frontend-base-url=http://localhost:3000"
})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestSupportConfiguration.class})
public @interface IntegrationTest {
}
