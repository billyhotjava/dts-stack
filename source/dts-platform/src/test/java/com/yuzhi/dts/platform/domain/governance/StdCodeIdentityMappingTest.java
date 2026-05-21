package com.yuzhi.dts.platform.domain.governance;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class StdCodeIdentityMappingTest {

    @Test
    void stdCodeValueUsesDatabaseIdentityColumn() throws NoSuchFieldException {
        assertIdentityStrategy(StdCodeValue.class.getDeclaredField("itemId"));
    }

    @Test
    void stdCodeMappingUsesDatabaseIdentityColumn() throws NoSuchFieldException {
        assertIdentityStrategy(StdCodeMapping.class.getDeclaredField("mapId"));
    }

    private static void assertIdentityStrategy(Field field) {
        GeneratedValue generatedValue = field.getAnnotation(GeneratedValue.class);

        assertThat(generatedValue).isNotNull();
        assertThat(generatedValue.strategy()).isEqualTo(GenerationType.IDENTITY);
    }
}
