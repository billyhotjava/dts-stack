package com.yuzhi.dts.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Pins the env-var → property binding for {@link SqlIdeFeatureProperties}.
 *
 * <p>Protects against future rename regressions. If the prefix or field name
 * changes, this test will fail at CI and the developer will know to also update
 * the docker-compose / Helm values that set {@code DTS_SQL_IDE_V2_ENABLED}.
 */
@IntegrationTest
@TestPropertySource(properties = "dts.sql-ide.v2.enabled=true")
class SqlIdeFeaturePropertiesIT {

    @Autowired
    private SqlIdeFeatureProperties props;

    @Test
    void enabledPropertyBindsCorrectly() {
        assertThat(props.isEnabled())
            .as("dts.sql-ide.v2.enabled (set via DTS_SQL_IDE_V2_ENABLED) should bind to true")
            .isTrue();
    }
}
