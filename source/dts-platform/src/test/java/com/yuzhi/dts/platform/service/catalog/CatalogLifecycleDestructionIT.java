package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogLifecycleControlService.ActionView;
import com.yuzhi.dts.platform.service.catalog.CatalogLifecycleControlService.SubmitCommand;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(
    properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.cache.use_second_level_cache=false",
        "spring.liquibase.change-log=classpath:config/liquibase/master.xml",
        "dts.lifecycle.destruction.postgres-enabled=true",
    }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(
    {
        CatalogLifecycleControlService.class,
        ManagedPostgresCopyDestructionAdapter.class,
        ExternalJdbcCopyDestructionAdapter.class,
        CatalogLifecycleDestructionIT.TestBeans.class,
    }
)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CatalogLifecycleDestructionIT {

    private static final String PAYLOAD_CHECKSUM = "a".repeat(64);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17.4")
            .withDatabaseName("catalogLifecycleDestructionIT")
            .withUsername("catalog_lifecycle_test")
            .withPassword("catalog_lifecycle_test");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private CatalogLifecycleControlService service;

    @Autowired
    private CatalogDatasetRepository datasetRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private CatalogClassificationService classificationService;

    @MockBean
    private CatalogClassificationPropagationService propagationService;

    private CatalogClassificationSnapshot seal;

    @BeforeEach
    void setUpClassificationAndLineage() {
        seal = new CatalogClassificationSnapshot();
        seal.setId(UUID.randomUUID());
        seal.setEffectiveLevel("SECRET");
        seal.setPropagationStatus(CatalogClassificationService.STATUS_PROPAGATED);
        seal.setRecordVersion(7L);
        Instant now = Instant.now();
        jdbcTemplate.update(
            """
            insert into catalog_classification_snapshot (
                id, subject_type, subject_key, asset_type, declared_level,
                effective_level, origin_type, sealed_at, evidence_checksum,
                propagation_status, record_version, created_by, created_date,
                last_modified_by, last_modified_date
            ) values (?, 'ASSET', ?, 'DATASET', 'SECRET', 'SECRET',
                      'TEST_FIXTURE', ?, ?, ?, ?, 'sprint72-it', ?,
                      'sprint72-it', ?)
            """,
            seal.getId(),
            "dataset:test-fixture:" + seal.getId(),
            Timestamp.from(now),
            "f".repeat(64),
            seal.getPropagationStatus(),
            seal.getRecordVersion(),
            Timestamp.from(now),
            Timestamp.from(now)
        );
        when(classificationService.resolve(anyString(), anyString()))
            .thenReturn(Optional.of(seal));
        when(propagationService.explainImpact(org.mockito.ArgumentMatchers.any(UUID.class)))
            .thenAnswer(invocation -> new CatalogClassificationPropagationService.ImpactExplanation(
                invocation.getArgument(0),
                List.of(),
                List.of(),
                false,
                List.of()
            ));
    }

    @Test
    void temporaryRestoreAndDualControlPermanentDestructionStayInsideDtsBoundary() {
        jdbcTemplate.execute("create table public.sprint72_managed_copy (id bigint primary key, value text)");
        jdbcTemplate.update("insert into public.sprint72_managed_copy values (1, 'managed')");
        jdbcTemplate.execute("create table public.sprint72_external_source (id bigint primary key, value text)");
        jdbcTemplate.update("insert into public.sprint72_external_source values (1, 'external')");

        CatalogDataset managed = dataset(
            "managed-copy",
            "sprint72_managed_copy",
            "dts-managed-copy"
        );
        CatalogDataset external = dataset(
            "external-source",
            "sprint72_external_source",
            null
        );

        ActionView managedTrash = submitAndDualApprove(managed, "TRASH", 30);
        CatalogDataset trashed = datasetRepository.findById(managed.getId()).orElseThrow();
        assertThat(managedTrash.status()).isEqualTo("EXECUTED");
        assertThat(trashed.getEnabled()).isFalse();
        assertThat(trashed.getLifecycleStatus()).isEqualTo("TRASHED");
        assertThat(tableExists("sprint72_managed_copy")).isTrue();

        ActionView managedRestore = submitAndDualApprove(managed, "RESTORE", null);
        CatalogDataset restored = datasetRepository.findById(managed.getId()).orElseThrow();
        assertThat(managedRestore.status()).isEqualTo("EXECUTED");
        assertThat(restored.getEnabled()).isTrue();
        assertThat(restored.getLifecycleStatus()).isEqualTo("ACTIVE");
        assertThat(tableExists("sprint72_managed_copy")).isTrue();

        submitAndDualApprove(managed, "TRASH", 30);
        ActionView managedDestroy = submitAndDualApprove(managed, "PERMANENT_DESTROY", null);
        assertThat(managedDestroy.status()).isEqualTo("EXECUTED");
        assertThat(tableExists("sprint72_managed_copy")).isFalse();
        assertSuccessfulProof(managed.getId(), "DTS_POSTGRES_MANAGED_COPY");

        submitAndDualApprove(external, "TRASH", 30);
        ActionView externalDestroy = submitAndDualApprove(external, "PERMANENT_DESTROY", null);
        assertThat(externalDestroy.status()).isEqualTo("EXECUTED");
        assertThat(tableExists("sprint72_external_source")).isTrue();
        assertThat(
            jdbcTemplate.queryForObject(
                "select value from public.sprint72_external_source where id=1",
                String.class
            )
        ).isEqualTo("external");
        assertSuccessfulProof(external.getId(), "EXTERNAL_JDBC_DTS_REFERENCES_ONLY");

        Integer dualControlledProofs = jdbcTemplate.queryForObject(
            """
            select count(*) from catalog_destruction_proof
             where first_approved_by='approver-a'
               and second_approved_by='approver-b'
               and external_source_touched=false
            """,
            Integer.class
        );
        assertThat(dualControlledProofs).isEqualTo(2);
    }

    @Test
    void requesterAndSameSecondApproverCannotCollapseDualControl() {
        CatalogDataset dataset = dataset("dual-control", "sprint72_dual_control", null);
        ActionView action = service.submit(command(dataset, "PERMANENT_DESTROY", null), "requester");

        assertThatThrownBy(() -> service.approve(action.id(), "requester", "self approval"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Requester cannot approve");

        service.approve(action.id(), "approver-a", "first approval");
        assertThatThrownBy(() -> service.approve(action.id(), "approver-a", "same second approval"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Second approver must be different");
    }

    private ActionView submitAndDualApprove(
        CatalogDataset dataset,
        String actionType,
        Integer retentionDays
    ) {
        ActionView submitted = service.submit(
            command(dataset, actionType, retentionDays),
            "requester"
        );
        assertThat(submitted.status()).isEqualTo("PENDING");
        ActionView first = service
            .approve(submitted.id(), "approver-a", "first approval")
            .action();
        assertThat(first.status()).isEqualTo("SECOND_APPROVAL_PENDING");
        return service
            .approve(submitted.id(), "approver-b", "second approval")
            .action();
    }

    private SubmitCommand command(
        CatalogDataset dataset,
        String actionType,
        Integer retentionDays
    ) {
        return new SubmitCommand(
            dataset.getId(),
            actionType,
            PAYLOAD_CHECKSUM,
            seal.getId(),
            seal.getRecordVersion(),
            "D01",
            "Sprint-72 isolated lifecycle verification",
            retentionDays
        );
    }

    private CatalogDataset dataset(String name, String table, String tags) {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setName(name);
        dataset.setType("postgresql");
        dataset.setSourceId(UUID.randomUUID());
        dataset.setClassification("SECRET");
        dataset.setOwnerDept("D01");
        dataset.setOwner("requester");
        dataset.setHiveDatabase("public");
        dataset.setHiveTable(table);
        dataset.setTags(tags);
        dataset.setEnabled(true);
        dataset.setLifecycleStatus("ACTIVE");
        return datasetRepository.saveAndFlush(dataset);
    }

    private boolean tableExists(String table) {
        return jdbcTemplate.queryForObject(
            "select to_regclass(?) is not null",
            Boolean.class,
            "public." + table
        );
    }

    private void assertSuccessfulProof(UUID datasetId, String adapterCode) {
        Map<String, Object> proof = jdbcTemplate.queryForMap(
            """
            select adapter_code, result_status, external_source_touched,
                   first_approved_by, second_approved_by
              from catalog_destruction_proof
             where dataset_id=?
            """,
            datasetId
        );
        assertThat(proof.get("adapter_code")).isEqualTo(adapterCode);
        assertThat(proof.get("result_status")).isEqualTo("SUCCEEDED");
        assertThat(proof.get("external_source_touched")).isEqualTo(false);
        assertThat(proof.get("first_approved_by")).isEqualTo("approver-a");
        assertThat(proof.get("second_approved_by")).isEqualTo("approver-b");
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
