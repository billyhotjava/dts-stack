package com.yuzhi.dts.platform.service.modeling.imports.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ConversionMode;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.ImportProjectionCompatibility;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class Sprint83DbtFixtureContractTest {

    private static final String ROOT = "fixtures/dbt-sprint83";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DbtModelArchiveInspectService service = new DbtModelArchiveInspectService(objectMapper, new SafeZipExtractor());

    @Test
    void artifactRichFixtureHasGovernedModelAndArtifactProvenance() throws Exception {
        var result = service.inspect(archive("fx01-artifact-rich"));

        assertThat(result.dbt().projectName()).isEqualTo("sprint83_artifact_rich");
        assertThat(result.dbt().manifestVersion()).isEqualTo("v12");
        assertThat(result.dbt().adapterType()).isEqualTo("postgres");
        assertThat(result.sources()).hasSize(1);
        assertThat(result.technicalNodes())
            .extracting(node -> node.dbtUniqueId())
            .contains("model.sprint83_artifact_rich.stg_orders", "macro.sprint83_artifact_rich.normalize_money");
        assertThat(result.models())
            .singleElement()
            .satisfies(model -> {
                assertThat(model.dbtUniqueId()).isEqualTo("model.sprint83_artifact_rich.fct_orders");
                assertThat(model.conversion().mode()).isNotEqualTo(ConversionMode.BLOCKED);
                assertThat(model.columns()).extracting(column -> column.dataType()).contains("bigint", "date", "numeric(18,2)");
                assertThat(model.tests()).contains("not_null_fct_orders_order_id");
            });
    }

    @Test
    void sourceOnlyFixtureKeepsUnverifiableDependencyBlocked() throws Exception {
        var result = service.inspect(archive("fx03-basic-blocked"));

        assertThat(result.models())
            .singleElement()
            .satisfies(model -> {
                assertThat(model.conversion().mode()).isEqualTo(ConversionMode.BLOCKED);
                assertThat(model.conversion().reasonCodes())
                    .containsExactlyInAnyOrder(
                        "SOURCE_SEMANTICS_INCOMPLETE",
                        "SOURCE_FIELDS_UNVERIFIED",
                        "SOURCE_PACKAGE_MISSING",
                        "SOURCE_DEPENDENCY_DYNAMIC"
                    );
            });
        assertThat(result.issues())
            .extracting(issue -> issue.code())
            .contains("DBT_SOURCE_PROJECT_REF_UNRESOLVED", "DBT_SOURCE_PROJECT_DYNAMIC_REFERENCE");
    }

    @Test
    void enforcedSourceOnlyFixtureIsImportableWithDeclaredFields() throws Exception {
        var result = service.inspect(archive("fx02-source-only-enforced"));

        assertThat(result.dbt().projectName()).isEqualTo("sprint83_source_only");
        assertThat(result.models())
            .singleElement()
            .satisfies(model -> {
                assertThat(model.dbtUniqueId()).isEqualTo("model.sprint83_source_only.orders");
                assertThat(model.columns())
                    .extracting("name", "dataType")
                    .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("order_id", "bigint"),
                        org.assertj.core.groups.Tuple.tuple("status", "varchar")
                    );
                assertThat(model.config())
                    .containsEntry("structureProvenance", "DECLARED")
                    .containsEntry("implementationOwnership", "DBT_MANAGED")
                    .containsEntry("sourceContractEnforced", true);
                assertThat(model.semantics().domainCode()).isEqualTo("SPRINT83");
                assertThat(model.conversion().reasonCodes()).containsExactly("SOURCE_SEMANTICS_INCOMPLETE");
            });
        assertThat(new DbtCompatibilityEvaluator().evaluate(result).importProjection())
            .isEqualTo(ImportProjectionCompatibility.IMPORTABLE);
    }

    @Test
    void threeWayFixtureKeepsStableIdentityWhileIncomingTechnicalChecksumChanges() throws Exception {
        var base = service.inspect(archive("fx04-three-way-drift/base"));
        var incoming = service.inspect(archive("fx04-three-way-drift/incoming"));
        var baseModel = base.models().get(0);
        var incomingModel = incoming.models().get(0);

        assertThat(base.dbt().projectName()).isEqualTo("sprint83_three_way");
        assertThat(incoming.dbt().projectName()).isEqualTo(base.dbt().projectName());
        assertThat(incomingModel.dbtUniqueId()).isEqualTo(baseModel.dbtUniqueId());
        assertThat(baseModel.semantics().domainCode()).isEqualTo("SPRINT83");
        assertThat(incomingModel.semantics().domainCode()).isEqualTo("SPRINT83");
        assertThat(incomingModel.sql().effectiveSqlChecksum()).isNotEqualTo(baseModel.sql().effectiveSqlChecksum());
        assertThat(incoming.packageChecksum()).isNotEqualTo(base.packageChecksum());
    }

    @Test
    void inventoryPinsOfflineAndNonCustomerBoundaries() throws Exception {
        var inventory = objectMapper.readTree(resource("inventory.json").toFile());
        var maliciousCases = objectMapper.readTree(resource("fx05-malicious/cases.json").toFile());

        assertThat(inventory.path("containsCustomerData").asBoolean()).isFalse();
        assertThat(inventory.path("networkRequired").asBoolean()).isFalse();
        assertThat(inventory.path("materialization").asText()).isEqualTo("NOT_CERTIFIED");
        assertThat(inventory.path("fixtures")).hasSize(5);
        assertThat(maliciousCases.path("cases")).hasSize(4);
    }

    private static MockMultipartFile archive(String fixtureName) throws Exception {
        Path root = resource(fixtureName);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes)) {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.filter(Files::isRegularFile).sorted(Comparator.naturalOrder()).toList()) {
                    String entryName = root.relativize(path).toString().replace('\\', '/');
                    ZipEntry entry = new ZipEntry(entryName);
                    entry.setTime(0L);
                    output.putNextEntry(entry);
                    output.write(Files.readAllBytes(path));
                    output.closeEntry();
                }
            }
        }
        return new MockMultipartFile("archive", fixtureName + ".zip", "application/zip", bytes.toByteArray());
    }

    private static Path resource(String relativePath) throws Exception {
        URI uri = Sprint83DbtFixtureContractTest.class.getClassLoader().getResource(ROOT + "/" + relativePath).toURI();
        return Path.of(uri);
    }
}
