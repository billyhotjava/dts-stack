package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(
    properties = {
        "spring.liquibase.enabled=true",
        "spring.liquibase.change-log=classpath:config/liquibase/master.xml",
        "logging.file.name=/tmp/dts-platform-classification-version-visibility-it.log",
    }
)
@Import(CatalogClassificationService.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CatalogClassificationVersionVisibilityPostgresIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("classification_version_visibility_it")
        .withUsername("classification_version_test")
        .withPassword("classification_version_test");

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired
    private CatalogClassificationService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void sealReturnsTheSamePositiveVersionThatConsumersWillReadAfterCommit() {
        String subjectKey = "semantic-model:" + UUID.randomUUID();

        var snapshot = service.seal(
            new CatalogClassificationService.SealCommand(
                "ASSET",
                subjectKey,
                "SEMANTIC_MODEL",
                "INTERNAL",
                null,
                null,
                List.of(),
                "SOURCE_DECLARATION",
                "classification-version-visibility-it",
                "a".repeat(64),
                "{}"
            )
        );

        Long storedVersion = jdbcTemplate.queryForObject(
            "select record_version from catalog_classification_snapshot where subject_type='ASSET' and subject_key=?",
            Long.class,
            subjectKey
        );
        assertThat(snapshot.getPropagationStatus()).isEqualTo(CatalogClassificationService.STATUS_PROPAGATED);
        assertThat(snapshot.getRecordVersion()).isPositive().isEqualTo(storedVersion);
    }
}
