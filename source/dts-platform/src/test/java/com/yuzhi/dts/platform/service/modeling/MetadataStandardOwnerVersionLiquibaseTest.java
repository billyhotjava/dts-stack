package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MetadataStandardOwnerVersionLiquibaseTest {

    @Test
    void addsANonNullOwnerVersionWithSafeBackfillAndRollback() throws Exception {
        String xml;
        try (var input = getClass().getResourceAsStream(
            "/config/liquibase/changelog/20260720_01_metadata_standard_owner_version.xml"
        )) {
            assertThat(input).isNotNull();
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(xml)
            .contains("tableName=\"metadata_standard\"")
            .contains("name=\"version\"")
            .contains("defaultValueNumeric=\"1\"")
            .contains("nullable=\"false\"")
            .contains("dropColumn tableName=\"metadata_standard\" columnName=\"version\"");
    }
}
