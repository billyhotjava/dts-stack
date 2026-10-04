package com.yuzhi.dts.platform.repository.explore;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset;
import com.yuzhi.dts.platform.domain.explore.QueryDatasetVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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
        "logging.file.name=/tmp/dts-platform-query-dataset-version-jsonb-it.log",
    }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class QueryDatasetVersionJsonbPostgresIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("query_dataset_version_jsonb_it")
        .withUsername("query_dataset_version_test")
        .withPassword("query_dataset_version_test");

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired
    private QueryDatasetAssetRepository assetRepository;

    @Autowired
    private QueryDatasetVersionRepository versionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void semanticContractPersistsAsQueryableJsonb() {
        QueryDatasetAsset asset = new QueryDatasetAsset();
        asset.setName("jsonb-contract-it");
        asset.setStatus("PUBLISHED");
        asset.setRefreshStrategy("MANUAL");
        asset.setSqlText("SELECT 1");
        asset.setPublishedVersion(1);
        asset = assetRepository.saveAndFlush(asset);

        QueryDatasetVersion version = new QueryDatasetVersion();
        version.setDataset(asset);
        version.setVersionNo(1);
        version.setStatus("PUBLISHED");
        version.setSqlText("SELECT 1");
        version.setSemanticContractSchema("dts.query-dataset-contract/v1");
        version.setSemanticContractVersion("r1");
        version.setSemanticContractJson("{\"schema\":\"dts.query-dataset-contract/v1\",\"dimensions\":[]}");
        version.setSemanticContractChecksum("a".repeat(64));
        version.setContractSnapshotStatus("READY");
        version = versionRepository.saveAndFlush(version);

        String storedSchema = jdbcTemplate.queryForObject(
            "select semantic_contract_json ->> 'schema' from query_dataset_version where id = ?",
            String.class,
            version.getId()
        );
        assertThat(storedSchema).isEqualTo("dts.query-dataset-contract/v1");
    }
}
