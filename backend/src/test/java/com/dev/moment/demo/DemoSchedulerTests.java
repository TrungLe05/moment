package com.dev.moment.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** T-163: seed demo chỉ chạy ở tiến trình API và khi đã bật (D-027, D-045). */
class DemoSchedulerTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(DemoSeeder.class, () -> mock(DemoSeeder.class))
            .withUserConfiguration(DemoScheduler.class);

    @Test
    void shouldNotSeedUnlessEnabled() {
        runner.withPropertyValues("spring.profiles.active=api")
                .run(context -> assertThat(context).doesNotHaveBean(DemoScheduler.class));
    }

    @Test
    void shouldSeedInTheApiProcessWhenEnabledWithCredentials() {
        runner.withPropertyValues("spring.profiles.active=api", "moment.demo.enabled=true",
                        "moment.demo.email=demo@example.test", "moment.demo.password=unused-in-this-test")
                .run(context -> assertThat(context).hasSingleBean(DemoScheduler.class));
    }

    @Test
    void shouldNotSeedInTheWorkerProcess() {
        runner.withPropertyValues("spring.profiles.active=worker", "moment.demo.enabled=true",
                        "moment.demo.email=demo@example.test", "moment.demo.password=unused-in-this-test")
                .run(context -> assertThat(context).doesNotHaveBean(DemoScheduler.class));
    }

    @Test
    void shouldFailToStartWhenEnabledWithoutCredentials() {
        runner.withPropertyValues("spring.profiles.active=api", "moment.demo.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }
}
