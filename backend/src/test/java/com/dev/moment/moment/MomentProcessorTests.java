package com.dev.moment.moment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Điểm bắt đầu đoạn cắt video (BR-10, D-033); không cần Spring hay ffmpeg. */
class MomentProcessorTests {

    @Test
    void shouldStartFromBeginningWhenNoStartPointGiven() {
        assertThat(MomentProcessor.clipStart(null, 12_000)).isZero();
    }

    @Test
    void shouldKeepStartPointThatLeavesFiveSeconds() {
        assertThat(MomentProcessor.clipStart(3_000, 12_000)).isEqualTo(3_000);
        assertThat(MomentProcessor.clipStart(7_000, 12_000)).isEqualTo(7_000);
    }

    @Test
    void shouldMoveStartPointBackWhenTooCloseToEnd() {
        assertThat(MomentProcessor.clipStart(7_001, 12_000)).isEqualTo(7_000);
        assertThat(MomentProcessor.clipStart(60_000, 12_000)).isEqualTo(7_000);
    }

    @Test
    void shouldStartAtZeroWhenVideoShorterThanFiveSeconds() {
        assertThat(MomentProcessor.clipStart(2_000, 3_000)).isZero();
    }
}
