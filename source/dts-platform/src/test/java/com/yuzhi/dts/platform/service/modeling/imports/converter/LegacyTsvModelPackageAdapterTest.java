package com.yuzhi.dts.platform.service.modeling.imports.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ConversionMode;
import com.yuzhi.dts.platform.service.modeling.imports.validator.ModelPackageValidator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyTsvModelPackageAdapterTest {

    private final LegacyTsvModelPackageAdapter adapter = new LegacyTsvModelPackageAdapter();

    @TempDir
    Path archiveRoot;

    @Test
    void convertsEnabledRowsIntoValidBlockedCandidatesWithStableChecksums() throws Exception {
        Files.createDirectories(archiveRoot.resolve("legacy/models"));
        Files.createDirectories(archiveRoot.resolve("shared"));
        Files.writeString(archiveRoot.resolve("legacy/models/order_detail.sql"), "select 1 as order_id");
        Files.writeString(archiveRoot.resolve("shared/customer.sql"), "select 2 as customer_id");

        String tsv = """
            name\tlayer\tsql_path\tsource_data_source_id\talias\tschema_name\tmaterialized\ttags\tstatus\tenabled\towner_dept\tdescription\tcsv_path
            Order Detail\tDWD\tmodels/order_detail.sql\tsource-a\t\tpublic\ttable\torders, core\tDRAFT\ttrue\tD1\t订单明细\t
            Customer\tADS\tshared/customer.sql\tsource-b\t\tpublic\tview\tcustomer\tDRAFT\t\tD1\t客户\t
            Disabled\tADS\tmissing.sql\tsource-c\t\tpublic\ttable\tignored\tDRAFT\tfalse\tD1\t禁用\t
            """;
        Files.writeString(archiveRoot.resolve("legacy/models.tsv"), tsv);

        var modelPackage = adapter.convertIfPresent(archiveRoot).orElseThrow();

        assertThat(modelPackage.packageId())
            .isEqualTo("legacy-" + ModelPackageChecksum.sha256(tsv.getBytes(StandardCharsets.UTF_8)).substring(0, 16));
        assertThat(modelPackage.packageChecksum()).isEqualTo(ModelPackageChecksum.compute(modelPackage));
        assertThat(modelPackage.dbt().projectName()).isEqualTo("legacy");
        assertThat(modelPackage.dbt().manifestVersion()).isEqualTo("legacy-tsv/v1");
        assertThat(modelPackage.models())
            .extracting(model -> model.dbtUniqueId())
            .containsExactly("model.legacy.order_detail", "model.legacy.customer");
        assertThat(modelPackage.models()).allSatisfy(model -> {
            assertThat(model.materialization()).isNull();
            assertThat(model.columns()).isEmpty();
            assertThat(model.tests()).isEmpty();
            assertThat(model.dependencies()).isEmpty();
            assertThat(model.semantics()).isNull();
            assertThat(model.conversion().mode()).isEqualTo(ConversionMode.BLOCKED);
            assertThat(model.conversion().reasonCodes())
                .containsExactly(
                    "LEGACY_MANIFEST_REQUIRED",
                    "SEMANTIC_METADATA_REQUIRED",
                    "DEPENDENCY_GRAPH_UNVERIFIED"
                );
            assertThat(model.sql().rawSql()).isEqualTo(model.sql().effectiveSql());
            assertThat(model.sql().rawSqlChecksum()).isEqualTo(ModelPackageChecksum.sha256Text(model.sql().rawSql()));
            assertThat(model.sql().effectiveSqlChecksum()).isEqualTo(ModelPackageChecksum.sha256Text(model.sql().effectiveSql()));
        });
        assertThat(modelPackage.models().getFirst().resourcePath()).isEqualTo("legacy/models/order_detail.sql");
        assertThat(modelPackage.models().getFirst().config())
            .containsEntry("legacyLayer", "DWD")
            .containsEntry("legacyMaterialized", "table")
            .containsEntry("legacySourceDataSourceId", "source-a");
        assertThat(modelPackage.models().getFirst().tags()).containsExactly("orders", "core");
        assertThat(modelPackage.models().get(1).resourcePath()).isEqualTo("shared/customer.sql");
        assertThat(modelPackage.issues()).hasSize(2).allSatisfy(issue -> {
            assertThat(issue.code()).isEqualTo("LEGACY_MANIFEST_REQUIRED");
            assertThat(issue.severity()).isEqualTo("ERROR");
            assertThat(issue.message()).contains("manifest", "meta.dts");
            assertThat(issue.recoveryAction()).contains("普通建模", "高级建模");
        });
        assertThat(new ModelPackageValidator(new ObjectMapper()).validate(modelPackage)).isEmpty();
    }

    @Test
    void returnsEmptyWhenModelsTsvIsAbsent() throws Exception {
        Files.writeString(archiveRoot.resolve("README.md"), "no legacy manifest");

        assertThat(adapter.convertIfPresent(archiveRoot)).isEmpty();
    }

    @Test
    void rejectsMissingAndUnsafeSqlPaths() throws Exception {
        Files.writeString(archiveRoot.resolve("models.tsv"), "name\tsql_path\nmissing\tmissing.sql\n");

        assertCode("LEGACY_SQL_MISSING");

        Files.writeString(archiveRoot.resolve("models.tsv"), "name\tsql_path\nunsafe\t../outside.sql\n");

        assertCode("LEGACY_SQL_PATH_INVALID");
    }

    @Test
    void rejectsDuplicateSanitizedModelIdentifiers() throws Exception {
        Files.writeString(archiveRoot.resolve("orders.sql"), "select 1");
        Files.writeString(
            archiveRoot.resolve("models.tsv"),
            """
            name\tsql_path
            Sales-Order\torders.sql
            sales order\torders.sql
            """
        );

        assertCode("LEGACY_MODEL_DUPLICATE");
    }

    private void assertCode(String expectedCode) {
        assertThatThrownBy(() -> adapter.convertIfPresent(archiveRoot))
            .isInstanceOf(LegacyTsvModelPackageAdapter.LegacyArchiveException.class)
            .satisfies(
                exception ->
                    assertThat(((LegacyTsvModelPackageAdapter.LegacyArchiveException) exception).code()).isEqualTo(expectedCode)
            );
    }
}
