package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository.ExpectedVersion;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository.StoredDimensionDefinition;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.CreateCommand;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.ReuseScope;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.Status;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.View;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class DimensionDefinitionRepositoryIT {

    @Autowired
    private DimensionDefinitionRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void convergesIdempotencyAndCasWhileRetainingImmutableRevisionAndModelUsage() {
        String tenant = "dimension-definition-it-" + UUID.randomUUID();
        UUID domainId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        CreateCommand command = command(domainId, "dimension-definition-it");
        View first = view(definitionId, domainId, "CUSTOMER", "Customer", Status.DRAFT, 1, "a".repeat(64), now);

        assertThat(repository.insert(tenant, "owner-1", command, first, "1".repeat(64))).isEqualTo(1);
        assertThat(repository.appendRevision(tenant, "owner-1", first)).isEqualTo(1);
        assertThat(repository.insert(tenant, "owner-1", command, first, "1".repeat(64))).isZero();

        StoredDimensionDefinition storedFirst = repository.findCurrent(tenant, definitionId).orElseThrow();
        assertThat(storedFirst.toView(0)).usingRecursiveComparison().isEqualTo(first);
        assertThat(repository.findByIdempotencyKey(tenant, command.idempotencyKey())).contains(storedFirst);
        assertThat(repository.listCurrent(tenant, domainId, Status.DRAFT)).containsExactly(storedFirst);
        assertThat(repository.findCurrent("another-tenant", definitionId)).isEmpty();

        View retired = view(
            definitionId,
            domainId,
            "CUSTOMER",
            "Customer retired",
            Status.RETIRED,
            2,
            "b".repeat(64),
            now.plusSeconds(60)
        );
        assertThat(repository.compareAndSet(
            tenant,
            "owner-2",
            new ExpectedVersion(definitionId, 1, first.checksum()),
            retired
        )).isEqualTo(1);
        assertThat(repository.appendRevision(tenant, "owner-2", retired)).isEqualTo(1);
        assertThat(repository.compareAndSet(
            tenant,
            "owner-2",
            new ExpectedVersion(definitionId, 1, first.checksum()),
            retired
        )).isZero();

        assertThat(repository.findRevision(tenant, definitionId, 1))
            .get()
            .extracting(StoredDimensionDefinition::name, StoredDimensionDefinition::checksum)
            .containsExactly("Customer", first.checksum());

        UUID modelSpecId = seedDimensionModel(tenant, definitionId, retired.revision());
        assertThat(repository.usageCount(tenant, definitionId)).isEqualTo(1);
        assertThat(
            jdbcTemplate.queryForObject("select count(*) from modeling_model_spec where tenant_id = ? and id = ?", Long.class, tenant, modelSpecId)
        ).isEqualTo(1L);

        assertThatThrownBy(() ->
            jdbcTemplate.update(
                "delete from modeling_dimension_definition_revision where tenant_id = ? and dimension_definition_id = ? and revision = ?",
                tenant,
                definitionId,
                retired.revision()
            )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void enforcesGeneratedSystemCodeUniquenessPerTenant() {
        UUID domainId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        String tenant = "dimension-code-it-" + UUID.randomUUID();

        assertThat(
            repository.insert(
                tenant,
                "owner-1",
                command(domainId, "code-1"),
                view(UUID.randomUUID(), domainId, "CUSTOMER", "Customer", Status.DRAFT, 1, "c".repeat(64), now),
                "2".repeat(64)
            )
        ).isEqualTo(1);
        assertThat(
            repository.insert(
                tenant + "-other",
                "owner-1",
                command(domainId, "code-2"),
                view(UUID.randomUUID(), domainId, "CUSTOMER", "Customer", Status.DRAFT, 1, "d".repeat(64), now),
                "3".repeat(64)
            )
        ).isEqualTo(1);

        assertThatThrownBy(() ->
            repository.insert(
                tenant,
                "owner-1",
                command(domainId, "code-3"),
                view(UUID.randomUUID(), domainId, "CUSTOMER", "Duplicate", Status.DRAFT, 1, "e".repeat(64), now),
                "4".repeat(64)
            )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID seedDimensionModel(String tenant, UUID definitionId, int definitionRevision) {
        UUID businessObjectId = UUID.randomUUID();
        UUID modelSpecId = UUID.randomUUID();
        jdbcTemplate.update(
            """
            insert into modeling_business_object (
                id, tenant_id, code, name, object_kind, process_id, status, version
            ) values (?, ?, ?, 'Customer', 'ENTITY', 'customer-process', 'DRAFT', 1)
            """,
            businessObjectId,
            tenant,
            "bo_" + businessObjectId.toString().replace("-", "")
        );
        jdbcTemplate.update(
            """
            insert into modeling_model_spec (
                id, tenant_id, object_id, process_id, layer, model_type, implementation_mode,
                name, status, revision, version, dimension_definition_id, dimension_definition_revision
            ) values (?, ?, ?, 'customer-process', 'DIM', 'DIMENSION', 'DESIGNER_GENERATED',
                      'customer_dimension', 'DRAFT', 1, 1, ?, ?)
            """,
            modelSpecId,
            tenant,
            businessObjectId,
            definitionId,
            definitionRevision
        );
        return modelSpecId;
    }

    private static CreateCommand command(UUID domainId, String idempotencyKey) {
        return new CreateCommand(
            domainId,
            "Customer",
            "Reusable customer dimension",
            "owner-1",
            ReuseScope.DOMAIN,
            List.of(),
            idempotencyKey
        );
    }

    private static View view(
        UUID id,
        UUID domainId,
        String systemCode,
        String name,
        Status status,
        int revision,
        String checksum,
        Instant updatedAt
    ) {
        return new View(
            id,
            systemCode,
            domainId,
            name,
            "Reusable customer dimension",
            "owner-1",
            ReuseScope.DOMAIN,
            List.of(),
            status,
            revision,
            checksum,
            0,
            Instant.parse("2026-07-24T00:00:00Z"),
            updatedAt
        );
    }
}
