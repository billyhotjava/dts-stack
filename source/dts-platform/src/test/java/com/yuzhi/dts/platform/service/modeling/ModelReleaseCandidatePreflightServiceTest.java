package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CreateCandidateCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.ScopeEntryCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.DependencyEdge;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.DependencyGraph;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.DependencyNode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.DependencyState;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelReleaseCandidatePreflightServiceTest {

    private static final String TENANT = "tenant-a";
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID DWD_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID DWS_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID EXTERNAL_ID = UUID.fromString("20000000-0000-0000-0000-000000000003");
    private static final UUID OTHER_PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");

    @Mock
    private ModelSpecApplicationService modelSpecs;

    private ModelReleaseCandidatePreflightService service;

    @BeforeEach
    void setUp() {
        service = new ModelReleaseCandidatePreflightService(modelSpecs);
    }

    @Test
    void expandsSamePlanDependenciesAndKeepsPublishedCrossPlanDependencyAsSatisfied() {
        when(modelSpecs.dependencyGraph(TENANT, DWS_ID)).thenReturn(
            new DependencyGraph(
                DWS_ID,
                List.of(
                    node(DWS_ID, PLAN_ID, 4, Layer.DWS, ModelStatus.READY_TO_PUBLISH),
                    node(DWD_ID, PLAN_ID, 2, Layer.DWD, ModelStatus.READY_TO_PUBLISH),
                    node(EXTERNAL_ID, OTHER_PLAN_ID, 7, Layer.DWD, ModelStatus.PUBLISHED)
                ),
                List.of(
                    new DependencyEdge(DWS_ID, DWD_ID, 2, 2, DependencyState.CURRENT),
                    new DependencyEdge(DWS_ID, EXTERNAL_ID, 7, 7, DependencyState.CURRENT)
                )
            )
        );
        CreateCandidateCommand roots = command(List.of(new ScopeEntryCommand(DWS_ID, 0, "selected")));

        var result = service.requireEligible(TENANT, roots);

        assertThat(result.preview().eligible()).isTrue();
        assertThat(result.preview().rootCount()).isEqualTo(1);
        assertThat(result.preview().candidateEntryCount()).isEqualTo(2);
        assertThat(result.command().entries())
            .extracting(ScopeEntryCommand::modelSpecId)
            .containsExactly(DWD_ID, DWS_ID);
        assertThat(result.preview().nodes())
            .filteredOn(node -> node.modelSpecId().equals(EXTERNAL_ID))
            .singleElement()
            .satisfies(node -> assertThat(node.inclusion()).isEqualTo("SATISFIED_PUBLISHED"));
    }

    @Test
    void reportsEveryBlockerAndFailsCandidateCreationWithoutSilentlyDroppingModels() {
        when(modelSpecs.dependencyGraph(TENANT, DWS_ID)).thenReturn(
            new DependencyGraph(
                DWS_ID,
                List.of(
                    node(DWS_ID, PLAN_ID, 4, Layer.DWS, ModelStatus.READY_TO_PUBLISH),
                    new DependencyNode(
                        DWD_ID,
                        PLAN_ID,
                        2,
                        3,
                        "受限明细模型",
                        ModelType.FACT,
                        Layer.DWD,
                        ModelStatus.READY_TO_PUBLISH,
                        true
                    )
                ),
                List.of(new DependencyEdge(DWS_ID, DWD_ID, 2, 3, DependencyState.STALE))
            )
        );
        CreateCandidateCommand roots = command(List.of(new ScopeEntryCommand(DWS_ID, 0, "selected")));

        var preview = service.preview(TENANT, roots);

        assertThat(preview.eligible()).isFalse();
        assertThat(preview.blockers())
            .extracting(ModelReleaseCandidatePreflightService.PreflightBlocker::code)
            .containsExactlyInAnyOrder(
                "MODEL_RELEASE_DEPENDENCY_SCOPE_FORBIDDEN",
                "MODEL_RELEASE_DEPENDENCY_PIN_STALE"
            );
        assertThatThrownBy(() -> service.requireEligible(TENANT, roots))
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(((ModelReleaseCandidateException) error).code())
                    .isEqualTo("MODEL_RELEASE_CANDIDATE_PREFLIGHT_BLOCKED")
            );
    }

    @Test
    void rejectsMoreThanOneHundredRootModelsBeforeLoadingAnyGraph() {
        List<ScopeEntryCommand> roots = java.util.stream.IntStream
            .range(0, 101)
            .mapToObj(index -> new ScopeEntryCommand(new UUID(0L, index + 1L), index, "selected"))
            .toList();

        assertThatThrownBy(() -> service.preview(TENANT, command(roots)))
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(((ModelReleaseCandidateException) error).code())
                    .isEqualTo("MODEL_RELEASE_BATCH_ROOT_LIMIT_EXCEEDED")
            );
    }

    private static CreateCandidateCommand command(List<ScopeEntryCommand> entries) {
        return new CreateCandidateCommand(PLAN_ID, "dev", entries, "candidate-key", "batch materialization");
    }

    private static DependencyNode node(
        UUID id,
        UUID planId,
        int revision,
        Layer layer,
        ModelStatus status
    ) {
        return new DependencyNode(
            id,
            planId,
            revision,
            revision,
            "model-" + id,
            layer == Layer.DWD ? ModelType.FACT : ModelType.SUMMARY,
            layer,
            status,
            false
        );
    }
}
