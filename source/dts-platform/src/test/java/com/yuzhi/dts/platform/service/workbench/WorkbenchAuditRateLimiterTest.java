package com.yuzhi.dts.platform.service.workbench;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorkbenchAuditRateLimiterTest {

    @Test
    void allows_up_to_60_calls_per_minute_then_blocks_61st() {
        WorkbenchAuditRateLimiter limiter = new WorkbenchAuditRateLimiter();
        for (int i = 0; i < 60; i++) {
            assertThat(limiter.tryAcquire("alice"))
                .as("call #%s should be allowed", i + 1)
                .isTrue();
        }
        assertThat(limiter.tryAcquire("alice"))
            .as("61st call within window should be rate-limited")
            .isFalse();
    }

    @Test
    void per_user_buckets_are_independent() {
        WorkbenchAuditRateLimiter limiter = new WorkbenchAuditRateLimiter();
        for (int i = 0; i < 60; i++) {
            limiter.tryAcquire("alice");
        }
        assertThat(limiter.tryAcquire("alice")).isFalse();
        assertThat(limiter.tryAcquire("bob")).isTrue();
    }
}
