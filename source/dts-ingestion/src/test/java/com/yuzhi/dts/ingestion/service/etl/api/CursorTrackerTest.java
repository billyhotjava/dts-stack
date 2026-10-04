package com.yuzhi.dts.ingestion.service.etl.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CursorTrackerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void advance_shouldCompareNumericCursorByNumberInsteadOfString() {
        CursorTracker tracker = new CursorTracker();

        String next = tracker.advance(
            List.of(
                objectMapper.createObjectNode().put("cursor", "99"),
                objectMapper.createObjectNode().put("cursor", "100")
            ),
            Map.of("type", "numeric", "field", "cursor")
        );

        assertThat(next).isEqualTo("100");
    }

    @Test
    void startValue_shouldApplyDatetimeLookbackToInitialValue() {
        CursorTracker tracker = new CursorTracker();

        String start = tracker.startValue(
            Map.of("type", "datetime", "initialValue", "2026-01-01T00:00:00Z", "lookbackSeconds", 300)
        );

        assertThat(start).isEqualTo("2025-12-31T23:55:00Z");
    }

    @Test
    void advance_shouldCompareDatetimeCursorAcrossEpochSecondsMillisAndIsoValues() {
        CursorTracker tracker = new CursorTracker();

        String next = tracker.advance(
            List.of(
                objectMapper.createObjectNode().put("updatedAt", "1767225600"),
                objectMapper.createObjectNode().put("updatedAt", "1767225900000"),
                objectMapper.createObjectNode().put("updatedAt", "2026-01-01T00:10:00Z")
            ),
            Map.of("type", "datetime", "field", "updatedAt")
        );

        assertThat(next).isEqualTo("2026-01-01T00:10:00Z");
    }

    @Test
    void startValue_shouldApplyDatetimeLookbackToEpochMillisCheckpoint() {
        CursorTracker tracker = new CursorTracker();

        String start = tracker.startValue(Map.of("type", "datetime", "lookbackSeconds", 60), "1767225600000");

        assertThat(start).isEqualTo("2025-12-31T23:59:00Z");
    }
}
