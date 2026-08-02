package com.yuzhi.dts.platform.service.modeling.dbtdraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtProjectBundleManifest.BundleFile;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator.ValidatedNode;
import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator.ValidatedProject;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class DbtProjectBundleManifestTest {

    private static final String PROJECT_CHECKSUM = "c".repeat(64);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void freezesEveryFileWithContentHashSizeProjectChecksumAndTransitiveDependencyClosure() throws Exception {
        List<BundleFile> files = List.of(
            file("models/orders.sql", "select * from {{ ref('stg_orders') }}\n"),
            file("dbt_project.yml", "name: sprint83\n")
        );
        ValidatedNode stage = node("model.sprint83.stg_orders", List.of("source.sprint83.orders"));
        ValidatedNode orders = node("model.sprint83.orders", List.of(stage.dbtUniqueId()));
        ValidatedProject project = new ValidatedProject(
            "a".repeat(64),
            PROJECT_CHECKSUM,
            "sprint83",
            List.of(orders, stage),
            List.of()
        );

        DbtProjectBundleManifest.BundleSnapshot snapshot = DbtProjectBundleManifest.freeze(objectMapper, files, project);
        var json = objectMapper.readTree(snapshot.manifest());

        assertThat(snapshot.bundleChecksum()).isEqualTo(ModelPackageChecksum.sha256Text(snapshot.manifest()));
        assertThat(snapshot.projectChecksum()).isEqualTo(PROJECT_CHECKSUM);
        assertThat(json.path("projectChecksum").asText()).isEqualTo(PROJECT_CHECKSUM);
        assertThat(json.path("files").get(0).path("path").asText()).isEqualTo("dbt_project.yml");
        assertThat(json.path("files").get(0).path("content").asText()).isEqualTo("name: sprint83\n");
        assertThat(json.path("files").get(0).path("byteSize").asLong()).isEqualTo(15);
        assertThat(json.path("files").get(0).path("checksum").asText()).matches("^[0-9a-f]{64}$");
        assertThat(snapshot.dependencyClosure().get("model.sprint83.orders"))
            .containsExactly("model.sprint83.stg_orders", "source.sprint83.orders");
    }

    @Test
    void failsClosedWhenAnyPersistedFileHashOrByteSizeDoesNotMatchItsContent() {
        ValidatedProject project = new ValidatedProject(
            "a".repeat(64),
            PROJECT_CHECKSUM,
            "sprint83",
            List.of(node("model.sprint83.orders", List.of())),
            List.of()
        );

        assertThatThrownBy(() ->
            DbtProjectBundleManifest.freeze(
                objectMapper,
                List.of(new BundleFile("models/orders.sql", "select 1", "0".repeat(64), 8)),
                project
            )
        ).hasMessageContaining("checksum");
        assertThatThrownBy(() ->
            DbtProjectBundleManifest.freeze(
                objectMapper,
                List.of(
                    new BundleFile(
                        "models/orders.sql",
                        "select 1",
                        ModelPackageChecksum.sha256Text("select 1"),
                        999
                    )
                ),
                project
            )
        ).hasMessageContaining("byte size");
    }

    @Test
    void restoresTheCompleteFrozenBundleWithoutFabricatingFilesAndPreservesItsChecksum() {
        List<BundleFile> files = List.of(
            file("dbt_project.yml", "name: sprint83\nmodel-paths: [models]\n"),
            file("models/orders.sql", "select * from {{ ref('stg_orders') }}\n"),
            file("models/schema.yml", "version: 2\nmodels: []\n")
        );
        ValidatedProject project = new ValidatedProject(
            "a".repeat(64),
            PROJECT_CHECKSUM,
            "sprint83",
            List.of(node("model.sprint83.orders", List.of("model.sprint83.stg_orders"))),
            List.of()
        );
        DbtProjectBundleManifest.BundleSnapshot frozen = DbtProjectBundleManifest.freeze(objectMapper, files, project);

        DbtProjectBundleManifest.RestoredBundle restored = DbtProjectBundleManifest.restore(
            objectMapper,
            frozen.manifest(),
            frozen.bundleChecksum(),
            frozen.projectChecksum()
        );

        assertThat(restored.bundleChecksum()).isEqualTo(frozen.bundleChecksum());
        assertThat(restored.projectChecksum()).isEqualTo(frozen.projectChecksum());
        assertThat(restored.files()).containsExactlyElementsOf(files);
    }

    @Test
    void rejectsSensitiveFilesEvenWhenTheyAppearInsideAnExistingFrozenManifest() throws Exception {
        List<BundleFile> safe = List.of(file("dbt_project.yml", "name: sprint83\n"));
        ValidatedProject project = new ValidatedProject(
            "a".repeat(64),
            PROJECT_CHECKSUM,
            "sprint83",
            List.of(node("model.sprint83.orders", List.of())),
            List.of()
        );
        var frozen = DbtProjectBundleManifest.freeze(objectMapper, safe, project);
        var root = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(frozen.manifest());
        var files = (com.fasterxml.jackson.databind.node.ArrayNode) root.path("files");
        String secret = "token-body";
        files.addObject()
            .put("path", ".env")
            .put("byteSize", secret.getBytes(StandardCharsets.UTF_8).length)
            .put("checksum", ModelPackageChecksum.sha256Text(secret))
            .put("content", secret);
        String unsafeManifest = objectMapper.writeValueAsString(root);

        assertThatThrownBy(() ->
            DbtProjectBundleManifest.restore(
                objectMapper,
                unsafeManifest,
                ModelPackageChecksum.sha256Text(unsafeManifest),
                PROJECT_CHECKSUM
            )
        )
            .isInstanceOf(DbtImplementationDraftContract.DraftException.class)
            .hasMessageNotContaining("token-body");
    }

    private static BundleFile file(String path, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new BundleFile(path, content, ModelPackageChecksum.sha256(bytes), bytes.length);
    }

    private static ValidatedNode node(String uniqueId, List<String> dependencies) {
        return new ValidatedNode(
            uniqueId,
            uniqueId.substring(uniqueId.lastIndexOf('.') + 1),
            "models/" + uniqueId.substring(uniqueId.lastIndexOf('.') + 1) + ".sql",
            "table",
            "MODEL",
            "select 1",
            ModelPackageChecksum.sha256Text("select 1"),
            "{\"columns\":[],\"tests\":[]}",
            ModelPackageChecksum.sha256Text("{\"columns\":[],\"tests\":[]}"),
            dependencies,
            List.of()
        );
    }
}
