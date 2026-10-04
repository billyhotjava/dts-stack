package com.yuzhi.dts.copilot.analytics.service;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class JdbcDriverAvailabilityTest {

    @Test
    void mysqlDriverIsAvailableOnAnalyticsClasspath() {
        assertThatCode(() -> Class.forName("com.mysql.cj.jdbc.Driver")).doesNotThrowAnyException();
    }

    @Test
    void trinoDriverIsAvailableOnAnalyticsClasspath() {
        assertThatCode(() -> Class.forName("io.trino.jdbc.TrinoDriver")).doesNotThrowAnyException();
    }
}
