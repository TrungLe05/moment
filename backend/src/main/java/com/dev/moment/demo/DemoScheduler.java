package com.dev.moment.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * D-027: dựng lại dữ liệu demo lúc ứng dụng chạy lên và mỗi ngày lúc 4 giờ sáng (giờ máy chủ), để khách thử app luôn
 * thấy feed có khoảnh khắc mới. Chỉ ở tiến trình API, chỉ khi bật moment.demo.enabled (D-045).
 */
@Configuration(proxyBeanMethods = false)
@Profile("!worker")
@ConditionalOnProperty(name = "moment.demo.enabled", havingValue = "true")
@EnableConfigurationProperties(DemoProperties.class)
@EnableScheduling
class DemoScheduler {

    private static final Logger log = LoggerFactory.getLogger(DemoScheduler.class);

    private final DemoSeeder seeder;
    private final DemoProperties properties;

    DemoScheduler(DemoSeeder seeder, DemoProperties properties) {
        // Bật demo mà thiếu thông tin đăng nhập thì dừng khởi động, thay vì chạy với tài khoản không đăng nhập được.
        if (isBlank(properties.email()) || isBlank(properties.password())) {
            throw new IllegalStateException("Bật moment.demo.enabled thì phải đặt moment.demo.email và moment.demo.password");
        }
        this.seeder = seeder;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    void seedOnStartup() {
        reseed();
    }

    @Scheduled(cron = "0 0 4 * * *")
    void seedDaily() {
        reseed();
    }

    private void reseed() {
        try {
            seeder.reseed(properties.email(), properties.password());
        } catch (RuntimeException failure) {
            // Seed hỏng (storage chưa sẵn sàng, username "demo" đã có người dùng) không được làm sập API; lần sau thử lại.
            log.error("Không dựng lại được dữ liệu demo", failure);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
