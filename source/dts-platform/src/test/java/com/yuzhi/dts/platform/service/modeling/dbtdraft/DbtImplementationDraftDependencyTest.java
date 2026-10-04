package com.yuzhi.dts.platform.service.modeling.dbtdraft;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencyService.Resolution;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.PhysicalSource;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.PhysicalSourceFact;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.Snapshot;
import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator;
import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator.ValidatedNode;
import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator.ValidatedProject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    @Test
    void reconcilesCompilerPhysicalNamesToThePinnedSourceBindingIdentity() {
        UUID modelId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        UUID sourceBindingId = UUID.fromString("50000000-0000-0000-0000-000000000001");
        String projectKey = "dts_model_300000000000";
        String pinnedIdentity = "source." + projectKey + ".dts_src_500000000000.src_500000000000";
        Snapshot snapshot = new Snapshot(
            modelId,
            1,
            "a".repeat(64),
            1,
            "b".repeat(64),
            List.of(new PhysicalSource(sourceBindingId, "source-v1", pinnedIdentity)),
            List.of(),
            "c".repeat(64)
        );
        Resolution resolution = new Resolution(
            snapshot,
            Map.of(
                sourceBindingId,
                new PhysicalSourceFact(
                    sourceBindingId,
                    "source-v1",
                    true,
                    "CONNECTION_TABLE",
                    "public.ods_project_task"
                )
            )
        );
        Map<String, String> files = new LinkedHashMap<>();
        files.put(
            "dbt_project.yml",
            "name: " + projectKey + "\nversion: 1.0\nmodel-paths: [models]\n"
        );
        files.put(
            "models/dwd/project_task/v1/i1/stg_project_task.sql",
            "{{ config(materialized='ephemeral') }}\n" +
            "select * from {{ source('public', 'ods_project_task') }}\n"
        );
        files.put(
            "models/dwd/project_task/v1/i1/project_task.sql",
            "{{ config(materialized='table') }}\n" +
            "select * from {{ ref('stg_project_task') }}\n"
        );
        ValidatedProject project = new AdvancedDbtDraftStaticValidator().validate(files);
        ValidatedNode target = project.nodes().getFirst();

        Map<String, String> aliases = DbtImplementationDraftService.dependencyAliases(
            projectKey,
            resolution,
            Map.of()
        );

        assertThat(aliases)
            .containsEntry("source." + projectKey + ".public.ods_project_task", pinnedIdentity);
        assertThat(project.nodes()).extracting(ValidatedNode::dbtUniqueId)
            .containsExactly("model." + projectKey + ".project_task");
        assertThat(target.dependencies())
            .containsExactly("source." + projectKey + ".public.ods_project_task");
        assertThat(DbtImplementationDraftService.externalDependencies(project, target, aliases))
            .containsExactly(pinnedIdentity);
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
