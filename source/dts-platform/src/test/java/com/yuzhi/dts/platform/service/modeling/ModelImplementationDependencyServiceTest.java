package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencyReadPort.PlanFacts;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.DependencyFacts;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.ModelFact;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class ModelImplementationDependencyServiceTest {

    private static final String TENANT = "default";
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ROOT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test
    void createsServiceFromSpringContextWhenTestSeamConstructorAlsoExists() {
        ModelImplementationDependencyReadPort readPort = mock(ModelImplementationDependencyReadPort.class);

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ModelImplementationDependencyReadPort.class, () -> readPort);
            context.register(ModelImplementationDependencyService.class);
            context.refresh();

            assertThat(context.getBean(ModelImplementationDependencyService.class)).isNotNull();
        }
    }

    @Test
    void rejectsOneModelPinnedToDifferentRevisionsAcrossBatchRoots() {
        ModelImplementationDependencyReadPort readPort = mock(ModelImplementationDependencyReadPort.class);
        ModelSpecView revisionOne = model(1, "1".repeat(64));
        ModelSpecView revisionTwo = model(2, "2".repeat(64));
        when(readPort.readPlanFacts(TENANT, PLAN_ID, List.of(ROOT_ID))).thenReturn(
            new PlanFacts(
                new DependencyFacts(
                    List.of(new ModelFact(revisionOne, null), new ModelFact(revisionTwo, null)),
                    List.of()
                ),
                Map.of()
            )
        );
        ModelImplementationDependencyService service = new ModelImplementationDependencyService(readPort);

        assertThatThrownBy(() -> service.resolvePlan(TENANT, PLAN_ID, List.of(ROOT_ID)))
            .isInstanceOf(ModelSpecException.class)
            .satisfies(failure -> {
                ModelSpecException exception = (ModelSpecException) failure;
                org.assertj.core.api.Assertions.assertThat(exception.code())
                    .isEqualTo("MODEL_MATERIALIZATION_DEPENDENCY_SELECTOR_CONFLICT");
                org.assertj.core.api.Assertions.assertThat(exception.details()).isEqualTo(
                    Map.of("modelSpecId", ROOT_ID, "pins", List.of("1:" + "1".repeat(64), "2:" + "2".repeat(64)))
                );
            });
    }

    private static ModelSpecView model(int revision, String checksum) {
        ModelSpecView model = mock(ModelSpecView.class);
        when(model.id()).thenReturn(ROOT_ID);
        when(model.revision()).thenReturn(revision);
        when(model.checksum()).thenReturn(checksum);
        return model;
    }
}
