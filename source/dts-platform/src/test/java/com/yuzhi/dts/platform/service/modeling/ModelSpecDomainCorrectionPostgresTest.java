package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ModelSpecDomainCorrectionPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4");

    @Test
    void persistsCorrectedDomainWithRevisionFenceAndDetectsExecutionEvidence() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.execute("""
            create table modeling_model_spec (
                id uuid primary key, tenant_id text, domain_id uuid, status text, contract_version int,
                layer text, warehouse_layer_code text, model_type text, implementation_mode text, name text,
                grain_statement text, materialization text, business_activity_ref text, description text,
                consumption_scenario text, fact_shape text, grain_json jsonb, time_semantics jsonb,
                fields jsonb, source_refs jsonb, depends_on jsonb, dimension_refs jsonb, metric_refs jsonb,
                standard_bindings jsonb, generation_strategy jsonb, dimension_profile jsonb,
                data_mart_id uuid, variant_code text, business_process_id uuid, subject_domain_id uuid,
                current_checksum text, revision int, version bigint, last_modified_date timestamptz
            );
            create table modeling_model_lifecycle_event (tenant_id text, model_spec_id uuid);
            create table modeling_model_release_candidate_entry (tenant_id text, model_spec_id uuid);
            """);
        var repository = new ModelSpecRepository(jdbc, new ObjectMapper().findAndRegisterModules());
        UUID id = UUID.randomUUID(), category = UUID.randomUUID(), domain = UUID.randomUUID();
        jdbc.update("insert into modeling_model_spec(id,tenant_id,domain_id,status,contract_version,current_checksum,revision,version) values (?, 'tenant', ?, 'DRAFT', 2, 'old', 1, 1)", id, category);
        var replacement = mock(ModelSpecContract.ModelSpecView.class);
        when(replacement.id()).thenReturn(id);
        when(replacement.domainId()).thenReturn(domain);
        when(replacement.layer()).thenReturn(ModelSpecContract.Layer.DWD);
        when(replacement.modelType()).thenReturn(ModelSpecContract.ModelType.FACT);
        when(replacement.implementationMode()).thenReturn(ModelSpecContract.ImplementationMode.DESIGNER_GENERATED);
        when(replacement.updatedAt()).thenReturn(Instant.now());
        when(replacement.checksum()).thenReturn("new");
        when(replacement.revision()).thenReturn(2);
        assertThat(repository.compareAndSetV2("other-tenant", "actor", 1, "old", replacement, "{}" )).isZero();
        assertThat(repository.compareAndSetV2("tenant", "actor", 1, "old", replacement, "{}" )).isEqualTo(1);
        assertThat(jdbc.queryForObject("select domain_id from modeling_model_spec where id = ?", UUID.class, id)).isEqualTo(domain);
        assertThat(jdbc.queryForObject("select current_checksum from modeling_model_spec where id = ?", String.class, id)).isEqualTo("new");
        assertThat(repository.compareAndSetV2("tenant", "actor", 1, "old", replacement, "{}" )).isZero();
        assertThat(repository.hasDomainCorrectionEvidence("tenant", id)).isFalse();
        jdbc.update("insert into modeling_model_lifecycle_event values ('tenant', ?)", id);
        assertThat(repository.hasDomainCorrectionEvidence("tenant", id)).isTrue();
        assertThat(repository.hasDomainCorrectionEvidence("other-tenant", id)).isFalse();
        jdbc.update("delete from modeling_model_lifecycle_event");
        jdbc.update("insert into modeling_model_release_candidate_entry values ('tenant', ?)", id);
        assertThat(repository.hasDomainCorrectionEvidence("tenant", id)).isTrue();
    }
}
