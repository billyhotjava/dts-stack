package com.yuzhi.dts.platform.service.modeling.authoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.BundleFileView;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.SourceBundleKind;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.SourceBundleView;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator;
import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator.ValidatedNode;
import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator.ValidatedProject;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.ProjectionCoverage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelAuthoringProjectionServiceTest {

    private final AdvancedDbtDraftStaticValidator validator = mock(AdvancedDbtDraftStaticValidator.class);
    private final ModelAuthoringProjectionService service = new ModelAuthoringProjectionService(
        validator,
        new ObjectMapper()
    );

    @Test
    void classifiesAStaticallyAddressableSelectAsFull() {
        String sql = "select order_id, amount as total_amount from raw_orders";
        when(validator.validate(any())).thenReturn(project(node("orders", sql, List.of())));

        var projection = service.project(bundle(SourceBundleKind.CANONICAL_INITIALIZATION, sql));

        assertThat(projection.coverage()).isEqualTo(ProjectionCoverage.FULL);
        assertThat(projection.managedPaths()).containsExactly("models/orders.sql");
        assertThat(projection.rawNodes()).isEmpty();
    }

    @Test
    void keepsUnprojectableSqlAsAnEditableRawNode() {
        String sql = "select * from {{ ref(dynamic_model) }}";
        when(validator.validate(any())).thenReturn(
            project(node("orders", sql, List.of("DBT_SOURCE_PROJECT_DYNAMIC_REFERENCE")))
        );

        var projection = service.project(bundle(SourceBundleKind.FROZEN_SOURCE_BUNDLE, sql));

        assertThat(projection.coverage()).isEqualTo(ProjectionCoverage.NONE);
        assertThat(projection.managedPaths()).isEmpty();
        assertThat(projection.rawNodes()).singleElement().satisfies(node -> {
            assertThat(node.sourcePath()).isEqualTo("models/orders.sql");
            assertThat(node.editable()).isTrue();
        });
    }

    @Test
    void classifiesMixedAddressableAndDynamicModelsAsPartialWithoutDroppingEitherNode() {
        String managedSql = "select order_id, amount as total_amount from raw_orders";
        String rawSql = "select * from {{ ref(dynamic_model) }}";
        when(validator.validate(any())).thenReturn(
            new ValidatedProject(
                "a".repeat(64),
                "b".repeat(64),
                "project",
                List.of(
                    node("orders", managedSql, List.of()),
                    node("dynamic_orders", rawSql, List.of("DBT_SOURCE_PROJECT_DYNAMIC_REFERENCE"))
                ),
                List.of()
            )
        );
        SourceBundleView source = bundle(
            SourceBundleKind.FROZEN_SOURCE_BUNDLE,
            Map.of("models/orders.sql", managedSql, "models/dynamic_orders.sql", rawSql)
        );

        var first = service.project(source);
        var replay = service.project(source);

        assertThat(first).isEqualTo(replay);
        assertThat(first.coverage()).isEqualTo(ProjectionCoverage.PARTIAL);
        assertThat(first.managedPaths()).containsExactly("models/orders.sql");
        assertThat(first.rawNodes()).singleElement().satisfies(node ->
            assertThat(node.sourcePath()).isEqualTo("models/dynamic_orders.sql")
        );
        assertThat(first.reasons()).contains("DBT_SOURCE_PROJECT_DYNAMIC_REFERENCE");
    }

    @Test
    void treatsTopLevelWildcardsAsUnsafeEvenWhenTheStaticValidatorAcceptsTheNode() {
        String sql = "select src.* from raw_orders src";
        when(validator.validate(any())).thenReturn(project(node("orders", sql, List.of())));

        var projection = service.project(bundle(SourceBundleKind.FROZEN_SOURCE_BUNDLE, sql));

        assertThat(projection.coverage()).isEqualTo(ProjectionCoverage.NONE);
        assertThat(projection.reasons()).contains("MODEL_AUTHORING_PROJECTION_WILDCARD_UNSAFE");
        assertThat(projection.rawNodes()).hasSize(1);
    }

    private static ValidatedProject project(ValidatedNode node) {
        return new ValidatedProject("a".repeat(64), "b".repeat(64), "project", List.of(node), List.of());
    }

    private static ValidatedNode node(String name, String sql, List<String> reasons) {
        return new ValidatedNode(
            "model.project." + name,
            name,
            "models/" + name + ".sql",
            "table",
            "MODEL",
            sql,
            ModelPackageChecksum.sha256Text(sql),
            "{}",
            ModelPackageChecksum.sha256Text("{}"),
            List.of(),
            reasons
        );
    }

    private static SourceBundleView bundle(SourceBundleKind kind, String sql) {
        return bundle(kind, Map.of("models/orders.sql", sql));
    }

    private static SourceBundleView bundle(SourceBundleKind kind, Map<String, String> sqlFiles) {
        String project = "name: project\nmodel-paths: [models]\n";
        var files = new java.util.ArrayList<BundleFileView>();
        files.add(file("dbt_project.yml", project));
        sqlFiles.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
            files.add(file(entry.getKey(), entry.getValue()))
        );
        return new SourceBundleView(
            "project",
            ModelPackageChecksum.sha256Text(project),
            "c".repeat(64),
            kind,
            true,
            files,
            null,
            null,
            Map.of()
        );
    }

    private static BundleFileView file(String path, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new BundleFileView(path, content, ModelPackageChecksum.sha256(bytes), bytes.length);
    }
}
