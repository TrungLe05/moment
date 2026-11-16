package com.dev.moment.support;

import com.dev.moment.auth.FakeGoogleTokenVerifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Thay các phụ thuộc bên ngoài (đồng hồ, SMTP, Google) bằng bản điều khiển được trong test. */
@TestConfiguration(proxyBeanMethods = false)
public class TestSupportConfiguration {

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock();
    }

    @Bean
    @Primary
    RecordingEmailSender recordingEmailSender() {
        return new RecordingEmailSender();
    }

    @Bean
    @Primary
    FakeGoogleTokenVerifier fakeGoogleTokenVerifier() {
        return new FakeGoogleTokenVerifier();
    }
}
