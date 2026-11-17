package com.dev.moment.housekeeping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.dev.moment.auth.TokenHousekeeping;
import com.dev.moment.job.MediaJobService;
import com.dev.moment.job.MediaWorker;
import com.dev.moment.moment.MomentHousekeeping;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** T-161: lịch job định kỳ chỉ bật ở tiến trình API (DB mục 9, ARCHITECTURE.md mục 4). */
class HousekeepingSchedulerTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(MomentHousekeeping.class, () -> mock(MomentHousekeeping.class))
            .withBean(TokenHousekeeping.class, () -> mock(TokenHousekeeping.class))
            .withBean(MediaJobService.class, () -> mock(MediaJobService.class))
            .withBean(MediaWorker.class, () -> mock(MediaWorker.class))
            .withUserConfiguration(HousekeepingScheduler.class);

    @Test
    void shouldScheduleJobsInTheApiProcessByDefault() {
        runner.withPropertyValues("spring.profiles.active=api")
                .run(context -> assertThat(context).hasSingleBean(HousekeepingScheduler.class));
    }

    @Test
    void shouldNotScheduleJobsInTheWorkerProcess() {
        runner.withPropertyValues("spring.profiles.active=worker")
                .run(context -> assertThat(context).doesNotHaveBean(HousekeepingScheduler.class));
    }

    @Test
    void shouldNotScheduleJobsWhenDisabled() {
        runner.withPropertyValues("spring.profiles.active=api", "moment.housekeeping.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(HousekeepingScheduler.class));
    }
}
