package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SubjectDomainGovernanceLiquibaseTest {

    @Test
    void addsSubjectDomainLedgerAttachedToTheDataMartLedger() throws Exception {
        String master = resource("config/liquibase/master.xml");
        String migration = resource("config/liquibase/changelog/20260806_03_modeling_subject_domain.xml");

        assertThat(master).contains("20260806_03_modeling_subject_domain.xml");
        assertThat(migration)
            .contains("modeling_subject_domain")
            .contains("mart_id")
            .contains("fk_modeling_subject_domain_mart")
            .contains("status in ('DRAFT', 'CURRENT', 'RETIRED')")
            .contains("idempotency_key")
            .contains("current_checksum");
    }

    private static String resource(String path) throws Exception {
        try (var input = SubjectDomainGovernanceLiquibaseTest.class.getResourceAsStream("/" + path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
