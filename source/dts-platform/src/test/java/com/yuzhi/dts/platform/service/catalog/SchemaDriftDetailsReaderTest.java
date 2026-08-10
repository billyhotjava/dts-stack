package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SchemaDriftDetailsReaderTest {

    private final SchemaDriftDetailsReader reader = new SchemaDriftDetailsReader(new ObjectMapper());

    @Test
    void readsVersionedPayloadAsStructuredFields() {
        SchemaDriftDetailsReader.SchemaDriftDetails details = reader.read(
            """
            {"contractVersion":1,"impactLevel":"BREAKING","changes":[{"field":"amount","kind":"FIELD_REMOVED","impact":"BREAKING"}]}
            """
        );

        assertThat(details.contractVersion()).isEqualTo(1);
        assertThat(details.impactLevel()).isEqualTo("BREAKING");
        assertThat(details.changes()).singleElement().containsEntry("field", "amount");
    }

    @Test
    void projectsLegacyPayloadWithoutInventingCompatibility() {
        SchemaDriftDetailsReader.SchemaDriftDetails details = reader.read(
            """
            {"removed":[{"name":"legacy_code","dataType":"varchar(20)","nullable":true}]}
            """
        );

        assertThat(details.contractVersion()).isZero();
        assertThat(details.impactLevel()).isEqualTo("REVIEW_REQUIRED");
        assertThat(details.changes())
            .singleElement()
            .containsEntry("field", "legacy_code")
            .containsEntry("kind", "FIELD_REMOVED")
            .containsEntry("impact", "REVIEW_REQUIRED");
    }
}
