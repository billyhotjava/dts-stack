package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.config.ModelMaterializationProperties;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencyService.PlanResolution;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencyService.ResolvedModel;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.DependencyRole;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.ModelInput;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.Snapshot;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanContract.Action;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanContract.PreviewCommand;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanContract.Strategy;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanRelationPort.RelationObservation;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModelMaterializationPlanServiceTest {

    private static final String TENANT = "default";
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID UPSTREAM_PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID DIM_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID DWS_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ADS_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final String MODEL_CHECKSUM = "1".repeat(64);
    private static final String IMPLEMENTATION_CHECKSUM = "2".repeat(64);
    private static final String DEPENDENCY_CHECKSUM = "3".repeat(64);

    private ModelImplementationDependencyService dependencies;
    private ModelMaterializationPlanRelationPort relations;
    private ModelMaterializationPlanService service;
    private PlanResolution resolution;
    private com.yuzhi.dts.platform.security.modeling.ModelingIdentityService.Scope identity;
    @org.junit.jupiter.api.AfterEach void closeIdentity() { if (identity != null) identity.close(); }


    @BeforeEach
    void setUp() {
        dependencies = mock(ModelImplementationDependencyService.class);
        relations = mock(ModelMaterializationPlanRelationPort.class);
        ModelMaterializationProperties properties = new ModelMaterializationProperties();
        properties.setExecutionTargetKey("postgres-primary");
        properties.setAdapter("postgres");
        service = new ModelMaterializationPlanService(dependencies, relations, properties);
        var access = mock(ModelSpecPlanWriteAccessPort.class);
        when(access.canReadPlan(TENANT, PLAN_ID)).thenReturn(true);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "access", access);
        var directory = mock(com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway.class);
        when(directory.currentModelingUser("modeler")).thenReturn(new com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway.ModelingUser("modeler","modeler","Modeler","dept-a","甲",List.of("ROLE_DEPT_DATA_OWNER"),true,"GENERAL"));
        identity = new com.yuzhi.dts.platform.security.modeling.ModelingIdentityService(directory).openCurrentUser("modeler");
        resolution = chain();
        when(dependencies.resolvePlan(TENANT, PLAN_ID, List.of(ADS_ID))).thenReturn(resolution);
    }

    @Test
    void buildsMissingClosureInStableTopologicalOrder() {
        when(relations.findLatest(eq(TENANT), eq(PLAN_ID), eq("dev"), eq("postgres-primary"), eq("postgres"), anyList()))
            .thenReturn(Map.of());

        var preview = service.preview(TENANT, command(Strategy.WITH_MISSING_UPSTREAMS));

        assertThat(preview.canStart()).isTrue();
        assertThat(preview.orderedEntries()).extracting(entry -> entry.modelSpecId()).containsExactly(DIM_ID, DWS_ID, ADS_ID);
        assertThat(preview.orderedEntries()).extracting(entry -> entry.action()).containsOnly(Action.BUILD);
        assertThat(preview.planChecksum()).matches("^[0-9a-f]{64}$");
    }

    @Test
    void stopsClosureAtAnExactVerifiedReusableRelation() {
        when(relations.findLatest(eq(TENANT), eq(PLAN_ID), eq("dev"), eq("postgres-primary"), eq("postgres"), anyList()))
            .thenReturn(Map.of(DWS_ID, observation(DWS_ID, "4".repeat(64))));

        var preview = service.preview(TENANT, command(Strategy.WITH_MISSING_UPSTREAMS));

        assertThat(preview.canStart()).isTrue();
        assertThat(preview.orderedEntries()).extracting(entry -> entry.modelSpecId()).containsExactly(DWS_ID, ADS_ID);
        assertThat(preview.orderedEntries()).extracting(entry -> entry.action()).containsExactly(Action.REUSE, Action.BUILD);
        assertThat(preview.orderedEntries().getFirst().targetRelation()).isEqualTo("warehouse.public.model_table");
        assertThat(service.requireCurrent(TENANT, command(Strategy.WITH_MISSING_UPSTREAMS), preview.planChecksum()).buildEntries())
            .extracting(entry -> entry.modelSpecId())
            .containsExactly(ADS_ID);
    }

    @Test
    void mergesMultipleRequestedRootsIntoOneDeduplicatedTopologicalPlan() {
        PlanResolution multiRoot = new PlanResolution(
            List.of(DWS_ID, ADS_ID),
            resolution.models(),
            resolution.physicalSourceFacts()
        );
        when(dependencies.resolvePlan(TENANT, PLAN_ID, List.of(DWS_ID, ADS_ID))).thenReturn(multiRoot);
        when(relations.findLatest(eq(TENANT), eq(PLAN_ID), eq("dev"), eq("postgres-primary"), eq("postgres"), anyList()))
            .thenReturn(Map.of());

        var preview = service.preview(
            TENANT,
            new PreviewCommand(PLAN_ID, "dev", List.of(ADS_ID, DWS_ID, ADS_ID), Strategy.WITH_MISSING_UPSTREAMS)
        );

        assertThat(preview.requestedModelSpecIds()).containsExactly(DWS_ID, ADS_ID);
        assertThat(preview.orderedEntries()).extracting(entry -> entry.modelSpecId()).containsExactly(DIM_ID, DWS_ID, ADS_ID);
        assertThat(preview.orderedEntries()).extracting(entry -> entry.action()).containsOnly(Action.BUILD);
        assertThat(preview.orderedEntries()).extracting(entry -> entry.dependencyRole().name())
            .containsExactly("DIMENSION", "ROOT", "ROOT");
    }

    @Test
    void currentOnlyFailsClosedWhenAnyRequiredUpstreamCannotBeReused() {
        when(relations.findLatest(eq(TENANT), eq(PLAN_ID), eq("dev"), eq("postgres-primary"), eq("postgres"), anyList()))
            .thenReturn(Map.of());

        var preview = service.preview(TENANT, command(Strategy.CURRENT_ONLY));

        assertThat(preview.canStart()).isFalse();
        assertThat(preview.blockers())
            .extracting(blocker -> blocker.code())
            .containsOnly("MODEL_MATERIALIZATION_UPSTREAM_NOT_CURRENT");
        assertThatThrownBy(() -> service.requireCurrent(TENANT, command(Strategy.CURRENT_ONLY), preview.planChecksum()))
            .isInstanceOf(ModelReleaseCandidateException.class)
            .extracting(failure -> ((ModelReleaseCandidateException) failure).code())
            .isEqualTo(ModelMaterializationPlanService.BLOCKED_ERROR_CODE);
    }

    @Test
    void observationDriftChangesChecksumAndRejectsTheOldPreview() {
        when(relations.findLatest(eq(TENANT), eq(PLAN_ID), eq("dev"), eq("postgres-primary"), eq("postgres"), anyList()))
            .thenReturn(Map.of(DWS_ID, observation(DWS_ID, "4".repeat(64))))
            .thenReturn(Map.of(DWS_ID, observation(DWS_ID, "5".repeat(64))));
        var preview = service.preview(TENANT, command(Strategy.WITH_MISSING_UPSTREAMS));

        assertThatThrownBy(() ->
                service.requireCurrent(TENANT, command(Strategy.WITH_MISSING_UPSTREAMS), preview.planChecksum())
            )
            .isInstanceOf(ModelReleaseCandidateException.class)
            .extracting(failure -> ((ModelReleaseCandidateException) failure).code())
            .isEqualTo(ModelMaterializationPlanService.STALE_ERROR_CODE);
    }

    @Test
    void dependencySnapshotFailureReturnsAStablePreviewBlocker() {
        when(dependencies.resolvePlan(TENANT, PLAN_ID, List.of(ADS_ID))).thenThrow(
            new ModelSpecException(
                "MODEL_SOURCE_BINDING_STALE",
                "Source binding changed",
                ModelSpecException.Kind.CONFLICT,
                Map.of("modelSpecId", ADS_ID)
            )
        );

        var preview = service.preview(TENANT, command(Strategy.WITH_MISSING_UPSTREAMS));

        assertThat(preview.canStart()).isFalse();
        assertThat(preview.blockers()).singleElement().satisfies(blocker -> {
            assertThat(blocker.code()).isEqualTo("MODEL_SOURCE_BINDING_STALE");
            assertThat(blocker.modelSpecId()).isEqualTo(ADS_ID);
        });
    }

    @Test
    void reusesAnExactPublishedUpstreamFromAnotherPlanWithoutCopyingItIntoTheCandidate() {
        resolution = crossPlanChain();
        when(dependencies.resolvePlan(TENANT, PLAN_ID, List.of(ADS_ID))).thenReturn(resolution);
        when(relations.findLatest(eq(TENANT), eq(PLAN_ID), eq("dev"), eq("postgres-primary"), eq("postgres"), anyList()))
            .thenReturn(Map.of(DWS_ID, observation(DWS_ID, "4".repeat(64))));

        var preview = service.preview(TENANT, command(Strategy.WITH_MISSING_UPSTREAMS));

        assertThat(preview.canStart()).isTrue();
        assertThat(preview.orderedEntries()).extracting(entry -> entry.modelSpecId()).containsExactly(DWS_ID, ADS_ID);
        assertThat(preview.orderedEntries()).extracting(entry -> entry.action()).containsExactly(Action.REUSE, Action.BUILD);
        assertThat(service.requireCurrent(TENANT, command(Strategy.WITH_MISSING_UPSTREAMS), preview.planChecksum()).buildEntries())
            .extracting(entry -> entry.modelSpecId())
            .containsExactly(ADS_ID);
    }

    @Test
    void blocksRatherThanRebuildingAnUpstreamOwnedByAnotherPlan() {
        resolution = crossPlanChain();
        when(dependencies.resolvePlan(TENANT, PLAN_ID, List.of(ADS_ID))).thenReturn(resolution);
        when(relations.findLatest(eq(TENANT), eq(PLAN_ID), eq("dev"), eq("postgres-primary"), eq("postgres"), anyList()))
            .thenReturn(Map.of());

        var preview = service.preview(TENANT, command(Strategy.WITH_MISSING_UPSTREAMS));

        assertThat(preview.canStart()).isFalse();
        assertThat(preview.blockers())
            .extracting(blocker -> blocker.code())
            .contains("MODEL_MATERIALIZATION_CROSS_PLAN_UPSTREAM_NOT_CURRENT");
    }

    @Test
    void rejectsAnotherPlansModelWhenItIsRequestedAsTheRoot() {
        ResolvedModel upstream = node(DWS_ID, "跨规划汇总", Layer.DWS, List.of(), UPSTREAM_PLAN_ID);
        resolution = new PlanResolution(List.of(DWS_ID), Map.of(DWS_ID, upstream), Map.of());
        when(dependencies.resolvePlan(TENANT, PLAN_ID, List.of(DWS_ID))).thenReturn(resolution);
        when(relations.findLatest(eq(TENANT), eq(PLAN_ID), eq("dev"), eq("postgres-primary"), eq("postgres"), anyList()))
            .thenReturn(Map.of(DWS_ID, observation(DWS_ID, "4".repeat(64))));

        var preview = service.preview(
            TENANT,
            new PreviewCommand(PLAN_ID, "dev", List.of(DWS_ID), Strategy.WITH_MISSING_UPSTREAMS)
        );

        assertThat(preview.canStart()).isFalse();
        assertThat(preview.blockers())
            .extracting(blocker -> blocker.code())
            .contains("MODEL_MATERIALIZATION_ROOT_PLAN_MISMATCH");
    }

    @Test
    void dependencyAndRelationReadsAdmitOnlyPublishedCrossPlanPins() throws IOException {
        String dependencies = Files.readString(
            Path.of("src/main/java/com/yuzhi/dts/platform/repository/modeling/ModelImplementationDependencyReadAdapter.java")
        );
        String observations = Files.readString(
            Path.of("src/main/java/com/yuzhi/dts/platform/repository/modeling/ModelMaterializationPlanRelationReadAdapter.java")
        );

        assertThat(dependencies)
            .contains("spec.plan_id = ? or spec.status = 'PUBLISHED'")
            .doesNotContain("source.tenant_id = ? and source.plan_id = ?");
        assertThat(observations).contains("c.plan_id = ? or c.status = 'PUBLISHED'");
    }

    private static PreviewCommand command(Strategy strategy) {
        return new PreviewCommand(PLAN_ID, "dev", List.of(ADS_ID), strategy);
    }

    private static PlanResolution chain() {
        ResolvedModel dim = node(DIM_ID, "维度", Layer.DWD, List.of());
        ResolvedModel dws = node(
            DWS_ID,
            "汇总",
            Layer.DWS,
            List.of(input(DIM_ID, DependencyRole.DIMENSION))
        );
        ResolvedModel ads = node(
            ADS_ID,
            "应用",
            Layer.ADS,
            List.of(input(DWS_ID, DependencyRole.UPSTREAM))
        );
        return new PlanResolution(List.of(ADS_ID), Map.of(DIM_ID, dim, DWS_ID, dws, ADS_ID, ads), Map.of());
    }

    private static PlanResolution crossPlanChain() {
        ResolvedModel dws = node(DWS_ID, "跨规划汇总", Layer.DWS, List.of(), UPSTREAM_PLAN_ID);
        ResolvedModel ads = node(
            ADS_ID,
            "应用",
            Layer.ADS,
            List.of(input(DWS_ID, DependencyRole.UPSTREAM)),
            PLAN_ID
        );
        return new PlanResolution(List.of(ADS_ID), Map.of(DWS_ID, dws, ADS_ID, ads), Map.of());
    }

    private static ResolvedModel node(UUID id, String name, Layer layer, List<ModelInput> inputs) {
        return node(id, name, layer, inputs, PLAN_ID);
    }

    private static ResolvedModel node(UUID id, String name, Layer layer, List<ModelInput> inputs, UUID planId) {
        ModelSpecView model = mock(ModelSpecView.class);
        when(model.id()).thenReturn(id);
        when(model.name()).thenReturn(name);
        when(model.layer()).thenReturn(layer);
        when(model.planId()).thenReturn(planId);
        ModelLifecycleContract.ImplementationView implementation = mock(ModelLifecycleContract.ImplementationView.class);
        Snapshot snapshot = new Snapshot(
            id,
            1,
            MODEL_CHECKSUM,
            1,
            IMPLEMENTATION_CHECKSUM,
            List.of(),
            inputs,
            DEPENDENCY_CHECKSUM
        );
        return new ResolvedModel(model, implementation, snapshot);
    }

    private static ModelInput input(UUID id, DependencyRole role) {
        return new ModelInput(
            id,
            1,
            MODEL_CHECKSUM,
            1,
            IMPLEMENTATION_CHECKSUM,
            "model.pjm." + id.toString().substring(0, 8),
            role
        );
    }

    private static RelationObservation observation(UUID modelSpecId, String evidenceChecksum) {
        return new RelationObservation(
            modelSpecId,
            1,
            MODEL_CHECKSUM,
            1,
            IMPLEMENTATION_CHECKSUM,
            UUID.nameUUIDFromBytes(evidenceChecksum.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            evidenceChecksum,
            true,
            true,
            "postgres-primary",
            "postgres",
            "warehouse",
            "public",
            "model_table"
        );
    }
}
