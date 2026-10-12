package ru.fisher.ToolsMarket.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PathSegmentEncoderTest {

    private final PathSegmentEncoder encoder = new PathSegmentEncoder();

    @Test
    void encodesCyrillic() {
        assertThat(encoder.encode("КАТ020-709")).isEqualTo("%D0%9A%D0%90%D0%A2020-709");
    }

    @Test
    void encodesSpaceAsPercent20() {
        assertThat(encoder.encode("a b")).isEqualTo("a%20b");
    }

    @Test
    void nullBecomesEmptyString() {
        assertThat(encoder.encode(null)).isEmpty();
    }

    @Test
    void asciiPassesThrough() {
        assertThat(encoder.encode("abc-123")).isEqualTo("abc-123");
    }
}
