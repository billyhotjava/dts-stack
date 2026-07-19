package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class ModelingVNextCanonicalProjectionTest {

    @Test
    void oldListAndSingleReadProjectV2WithoutInventingObjectId() {
        ModelSpecApplicationService canonical = mock(ModelSpecApplicationService.class);
        ModelSpecView view = view();
        when(canonical.canonicalReadEnabled()).thenReturn(true);
        when(canonical.list("server-tenant", null, null, null, null)).thenReturn(List.of(view));
        when(canonical.get("server-tenant", view.id())).thenReturn(view);
        JdbcTemplate emptyLegacyLedger = new JdbcTemplate() {
            @Override
            public <T> List<T> query(String sql, Object[] args, RowMapper<T> rowMapper) {
                return List.of();
            }

            @Override
            public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
                throw new EmptyResultDataAccessException(1);
            }
        };
        ModelingVNextApplicationService service = new ModelingVNextApplicationService(
            emptyLegacyLedger,
            new ObjectMapper().findAndRegisterModules(),
            null,
            canonical
        );

        assertThat(service.listModelSpecs("server-tenant", null, null, null))
            .singleElement()
            .satisfies(projected -> {
                assertThat(projected.id()).isEqualTo(view.id().toString());
                assertThat(projected.objectId()).isNull();
            });
        assertThat(service.lineage("server-tenant", view.id().toString()).modelSpecId()).isEqualTo(view.id().toString());
    }

    @Test
    void canonicalDependenciesResolveThePinnedRevisionInsteadOfTheCurrentHead() {
        ModelSpecApplicationService canonical = mock(ModelSpecApplicationService.class);
        when(canonical.canonicalReadEnabled()).thenReturn(true);
        UUID derivedId = UUID.fromString("30000000-0000-0000-0000-000000000010");
        UUID upstreamId = UUID.fromString("30000000-0000-0000-0000-000000000020");
        ModelRevisionRef pinned = new ModelRevisionRef(upstreamId, 3);
        ModelSpecView derived = view(derivedId, "customer_summary", ModelType.SUMMARY, List.of(pinned));
        ModelSpecView revisionThree = view(upstreamId, "customer_detail_revision_3", ModelType.FACT, List.of());
        when(canonical.get("server-tenant", derivedId)).thenReturn(derived);
        when(canonical.revision("server-tenant", pinned)).thenReturn(revisionThree);
        JdbcTemplate noLegacyRows = emptyLegacyLedger();
        ModelingVNextApplicationService service = new ModelingVNextApplicationService(
            noLegacyRows,
            new ObjectMapper().findAndRegisterModules(),
            null,
            canonical
        );

        assertThat(service.dependencies("server-tenant", derivedId.toString()))
            .extracting(ModelingVNextContract.ModelSpec::name)
            .containsExactly("customer_detail_revision_3");
        verify(canonical).revision("server-tenant", pinned);
        verify(canonical, never()).get("server-tenant", upstreamId);
    }

    @Test
    void oldEndpointDoesNotProjectV2WhenCanonicalReadRollbackFlagIsOff() {
        ModelSpecApplicationService canonical = mock(ModelSpecApplicationService.class);
        when(canonical.canonicalReadEnabled()).thenReturn(false);
        ModelingVNextApplicationService service = new ModelingVNextApplicationService(
            emptyLegacyLedger(),
            new ObjectMapper().findAndRegisterModules(),
            null,
            canonical
        );

        assertThat(service.listModelSpecs("server-tenant", null, null, null)).isEmpty();
        verify(canonical, never()).list("server-tenant", null, null, null, null);
    }

    private static ModelSpecView view() {
        return view(
            UUID.fromString("30000000-0000-0000-0000-000000000001"),
            "customer_detail",
            ModelType.FACT,
            List.of()
        );
    }

    private static ModelSpecView view(UUID id, String name, ModelType type, List<ModelRevisionRef> dependsOn) {
        return new ModelSpecView(
            2,
            id,
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            UUID.fromString("20000000-0000-0000-0000-000000000001"),
            type,
            type == ModelType.SUMMARY ? Layer.DWS : Layer.DWD,
            name,
            null,
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            null,
            null,
            new Grain("one row per customer", List.of("customer_id")),
            null,
            null,
            List.of(new ModelField("customer_id", "varchar", false, null, FieldRole.KEY, null)),
            type == ModelType.SUMMARY
                ? List.of()
                : List.of(
                new SourceRef(
                    SourceKind.TABLE,
                    "ods.customer",
                    Layer.ODS,
                    SourceRole.PRIMARY,
                    null,
                    null,
                    null,
                    0,
                    UUID.fromString("50000000-0000-0000-0000-000000000001"),
                    "v1"
                )
            ),
            dependsOn,
            List.of(),
            List.of(),
            List.of(),
            null,
            ModelStatus.DRAFT,
            1,
            "a".repeat(64),
            Instant.EPOCH,
            Instant.EPOCH,
            CompatibilityMode.CANONICAL,
            null
        );
    }

    private static JdbcTemplate emptyLegacyLedger() {
        return new JdbcTemplate() {
            @Override
            public <T> List<T> query(String sql, Object[] args, RowMapper<T> rowMapper) {
                return List.of();
            }

            @Override
            public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
                throw new EmptyResultDataAccessException(1);
            }
        };
    }
}
