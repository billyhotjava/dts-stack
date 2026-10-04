package com.yuzhi.dts.platform.domain.governance;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

class GovIndicatorDefinitionJsonMappingTest {

    @Test
    void mapsEveryJsonbBackedStringWithTheJsonJdbcType() throws NoSuchFieldException {
        for (
            String fieldName :
            List.of("sourceRefs", "dynamicFilterConfig", "dependencyIndicators", "dimensionFields", "joinConfig")
        ) {
            Field field = GovIndicatorDefinition.class.getDeclaredField(fieldName);
            JdbcTypeCode jdbcTypeCode = field.getAnnotation(JdbcTypeCode.class);

            assertThat(jdbcTypeCode).as("JSONB mapping for %s", fieldName).isNotNull();
            assertThat(jdbcTypeCode.value()).as("JDBC type for %s", fieldName).isEqualTo(SqlTypes.JSON);
        }
    }
}
