package com.dev.moment.common.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GraphemeCounterTests {

    @Test
    void shouldCountPrecomposedAndDecomposedVietnameseTheSame() {
        String precomposed = "Việt";
        String decomposed = "Việt";
        assertThat(GraphemeCounter.count(precomposed)).isEqualTo(4);
        assertThat(GraphemeCounter.count(decomposed)).isEqualTo(4);
    }

    @Test
    void shouldCountEmojiSequencesAsOneCharacter() {
        assertThat(GraphemeCounter.count("❤️")).isEqualTo(1);
        assertThat(GraphemeCounter.count("👨‍👩‍👧")).isEqualTo(1);
        assertThat(GraphemeCounter.count("🇻🇳")).isEqualTo(1);
        assertThat(GraphemeCounter.count("hi 🔥")).isEqualTo(4);
    }

    @Test
    void shouldReturnZeroForNullOrEmpty() {
        assertThat(GraphemeCounter.count(null)).isZero();
        assertThat(GraphemeCounter.count("")).isZero();
    }
}
