package com.yuzhi.dts.platform.service.modeling.dbtdraft;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator.ValidatedNode;
import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator.ValidatedProject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DbtImplementationDraftDependencyTest {

    @Test
    void resolvesOnlyExternalLeavesAcrossCompilerGeneratedInternalNodes() {
        ValidatedNode staging = node(
            "model.sprint92.stg_orders",
            "stg_orders",
            List.of("source.ods.orders")
        );
        ValidatedNode target = node(
            "model.sprint92.orders",
            "orders",
            List.of("model.sprint92.stg_orders", "model.sprint92.dts_ref_budget")
        );
        ValidatedProject project = new ValidatedProject(
            "a".repeat(64),
            "b".repeat(64),
            "sprint92",
            List.of(staging, target),
            List.of()
        );

        assertThat(
            DbtImplementationDraftService.externalDependencies(
                project,
                target,
                Map.of("model.sprint92.dts_ref_budget", "model.pjm.budget")
            )
        ).containsExactly("model.pjm.budget", "source.ods.orders");
    }

    private static ValidatedNode node(String uniqueId, String name, List<String> dependencies) {
        return new ValidatedNode(
            uniqueId,
            name,
            "models/" + name + ".sql",
            "table",
            "MODEL",
            "select 1\n",
            "c".repeat(64),
            "{}",
            "d".repeat(64),
            dependencies,
            List.of()
        );
    }
}
