package com.yuzhi.dts.platform.service.modeling;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.StandardPackageManifestContract.Catalog;
import com.yuzhi.dts.platform.service.modeling.StandardPackageManifestContract.Dependency;
import com.yuzhi.dts.platform.service.modeling.StandardPackageManifestContract.InstalledPackage;
import com.yuzhi.dts.platform.service.modeling.StandardPackageManifestContract.Manifest;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StandardPackageManifestContractTest {

    private StandardPackageManifestContract contract;

    @BeforeEach
    void setUp() {
        contract = new StandardPackageManifestContract(new ObjectMapper());
    }

    @Test
    void parsePackage_acceptsValidV2Manifest() {
        Manifest manifest = contract.parsePackage(validManifestJson().getBytes(UTF_8));

        assertThat(manifest.packageCode()).isEqualTo("dts-core-person");
        assertThat(manifest.packageVersion()).isEqualTo("1.0.0");
        assertThat(manifest.dependencies()).containsExactly(new Dependency("dts-core-common-enum", "1.0.0"));
        assertThat(manifest.files()).containsEntry(
            "01-business-terms.csv",
            "8e5c85a5e62fc213bc207f6b9c73fb02cbd310d984c0317cd72f268a8043f661"
        );
    }

    @Test
    void parsePackage_rejectsUnsupportedSchemaWithStableCode() {
        assertContractCode(
            "MANIFEST_SCHEMA_UNSUPPORTED",
            () -> contract.parsePackage(validManifestJson().replace("\"schemaVersion\": \"2.0\"", "\"schemaVersion\": \"3.0\"").getBytes(UTF_8))
        );
    }

    @Test
    void parsePackage_rejectsInvalidSemanticVersion() {
        assertContractCode(
            "MANIFEST_VERSION_INVALID",
            () -> contract.parsePackage(validManifestJson().replace("\"packageVersion\": \"1.0.0\"", "\"packageVersion\": \"v1\"").getBytes(UTF_8))
        );
    }

    @Test
    void parsePackage_rejectsMissingRequiredField() {
        assertContractCode(
            "MANIFEST_REQUIRED_FIELD",
            () -> contract.parsePackage(validManifestJson().replace("\"industry\": \"COMMON\",", "").getBytes(UTF_8))
        );
    }

    @Test
    void parsePackage_rejectsNullDependencyWithStableCode() {
        assertContractCode(
            "MANIFEST_DEPENDENCY_INVALID",
            () ->
                contract.parsePackage(
                    validManifestJson()
                        .replace(
                            """
                            {"packageCode": "dts-core-common-enum", "minimumVersion": "1.0.0"}
                            """.trim(),
                            "null"
                        )
                        .getBytes(UTF_8)
                )
        );
    }

    @Test
    void parsePackage_rejectsNullFileChecksumWithStableCode() {
        assertContractCode(
            "MANIFEST_CHECKSUM_INVALID",
            () ->
                contract.parsePackage(
                    validManifestJson()
                        .replace(
                            "\"8e5c85a5e62fc213bc207f6b9c73fb02cbd310d984c0317cd72f268a8043f661\"",
                            "null"
                        )
                        .getBytes(UTF_8)
                )
        );
    }

    @Test
    void verifyFiles_acceptsDeclaredContentAndRejectsChecksumMismatch() {
        byte[] terms = "term_code,term_name\nBT_PERSON,人员\n".getBytes(UTF_8);
        Manifest manifest = manifest("dts-core-person", "1.0.0", List.of(), Map.of("01-business-terms.csv", sha256(terms)));

        contract.verifyFiles(manifest, Map.of("manifest.json", "{}".getBytes(UTF_8), "01-business-terms.csv", terms));

        assertContractCode(
            "MANIFEST_CHECKSUM_MISMATCH",
            () ->
                contract.verifyFiles(
                    manifest,
                    Map.of("manifest.json", "{}".getBytes(UTF_8), "01-business-terms.csv", "changed".getBytes(UTF_8))
                )
        );
    }

    @Test
    void verifyFiles_rejectsUndeclaredContent() {
        byte[] terms = "terms".getBytes(UTF_8);
        Manifest manifest = manifest("dts-core-person", "1.0.0", List.of(), Map.of("01-business-terms.csv", sha256(terms)));

        assertContractCode(
            "MANIFEST_FILE_UNDECLARED",
            () ->
                contract.verifyFiles(
                    manifest,
                    Map.of(
                        "manifest.json",
                        "{}".getBytes(UTF_8),
                        "01-business-terms.csv",
                        terms,
                        "02-data-elements.csv",
                        "elements".getBytes(UTF_8)
                    )
                )
        );
    }

    @Test
    void installationOrder_sortsDependenciesBeforeConsumers() {
        Manifest common = manifest("dts-core-common-enum", "1.0.0", List.of(), Map.of("03-reference-code-directories.csv", sha256("a")));
        Manifest person = manifest(
            "dts-core-person",
            "1.0.0",
            List.of(new Dependency("dts-core-common-enum", "1.0.0")),
            Map.of("01-business-terms.csv", sha256("b"))
        );

        assertThat(contract.installationOrder(new Catalog("2.0", List.of(person, common))))
            .extracting(Manifest::packageCode)
            .containsExactly("dts-core-common-enum", "dts-core-person");
    }

    @Test
    void installationOrder_rejectsMissingDependency() {
        Manifest person = manifest(
            "dts-core-person",
            "1.0.0",
            List.of(new Dependency("dts-core-common-enum", "1.0.0")),
            Map.of("01-business-terms.csv", sha256("b"))
        );

        assertContractCode(
            "MANIFEST_DEPENDENCY_MISSING",
            () -> contract.installationOrder(new Catalog("2.0", List.of(person)))
        );
    }

    @Test
    void installationOrder_rejectsDependencyCycle() {
        Manifest first = manifest(
            "dts-first",
            "1.0.0",
            List.of(new Dependency("dts-second", "1.0.0")),
            Map.of("01-business-terms.csv", sha256("a"))
        );
        Manifest second = manifest(
            "dts-second",
            "1.0.0",
            List.of(new Dependency("dts-first", "1.0.0")),
            Map.of("01-business-terms.csv", sha256("b"))
        );

        assertContractCode(
            "MANIFEST_DEPENDENCY_CYCLE",
            () -> contract.installationOrder(new Catalog("2.0", List.of(first, second)))
        );
    }

    @Test
    void requireInstallable_rejectsVersionRollbackAndSameVersionDrift() {
        Manifest installedManifest = manifest(
            "dts-core-person",
            "2.0.0",
            List.of(),
            Map.of("01-business-terms.csv", sha256("installed"))
        );
        Map<String, InstalledPackage> installed = Map.of(
            "dts-core-person",
            new InstalledPackage(
                installedManifest.packageCode(),
                installedManifest.packageVersion(),
                installedManifest.contentChecksum()
            )
        );

        assertContractCode(
            "MANIFEST_VERSION_ROLLBACK",
            () -> contract.requireInstallable(manifest("dts-core-person", "1.9.0", List.of(), Map.of("01-business-terms.csv", sha256("old"))), installed)
        );
        assertContractCode(
            "MANIFEST_VERSION_CONTENT_MISMATCH",
            () -> contract.requireInstallable(manifest("dts-core-person", "2.0.0", List.of(), Map.of("01-business-terms.csv", sha256("changed"))), installed)
        );
    }

    @Test
    void requireInstallable_acceptsEqualVersionWithEqualContent() {
        Manifest candidate = manifest(
            "dts-core-person",
            "2.0.0",
            List.of(),
            Map.of("01-business-terms.csv", sha256("same"))
        );
        Map<String, InstalledPackage> installed = Map.of(
            candidate.packageCode(),
            new InstalledPackage(candidate.packageCode(), candidate.packageVersion(), candidate.contentChecksum())
        );

        contract.requireInstallable(candidate, installed);
    }

    @Test
    void parseCatalogAndWritePackage_preserveValidatedContract() {
        Manifest person = contract.parsePackage(validManifestJson().getBytes(UTF_8));
        Manifest common = manifest(
            "dts-core-common-enum",
            "1.0.0",
            List.of(),
            Map.of("03-reference-code-directories.csv", sha256("common"))
        );
        String catalogJson = writeJson(new Catalog("2.0", List.of(person, common)));

        Catalog catalog = contract.parseCatalog(catalogJson.getBytes(UTF_8));
        Manifest roundTrip = contract.parsePackage(contract.writePackage(person));

        assertThat(roundTrip).isEqualTo(person);
        assertThat(catalog.packages()).hasSize(2);
    }

    @Test
    void adaptLegacyPackage_marksCompatibilityAndComputesChecksums() {
        Manifest legacy = contract.adaptLegacyPackage(
            "legacy-upload.zip",
            "legacy-upload.zip",
            "LEGACY_UPLOAD",
            Map.of("01-business-terms.csv", "terms".getBytes(UTF_8))
        );

        assertThat(legacy.schemaVersion()).isEqualTo("1.0");
        assertThat(legacy.packageCode()).isEqualTo("legacy-upload");
        assertThat(legacy.contentChecksum()).matches("[0-9a-f]{64}");
        assertThat(contract.summary(legacy)).containsEntry("compatibilityMode", "LEGACY_V1");
    }

    @Test
    void parseCatalogCompatible_adaptsV1ClasspathCatalog() {
        byte[] catalogJson = """
            {
              "packages": [
                {
                  "code": "legacy-common",
                  "name": "旧版通用包",
                  "category": "数据元"
                }
              ]
            }
            """.getBytes(UTF_8);
        Map<String, byte[]> entries = Map.of("02-data-elements.csv", "elements".getBytes(UTF_8));

        Catalog catalog = contract.parseCatalogCompatible(catalogJson, Map.of("legacy-common", entries));

        assertThat(catalog.schemaVersion()).isEqualTo("1.0");
        assertThat(catalog.packages()).singleElement().satisfies(pkg -> {
            assertThat(pkg.packageCode()).isEqualTo("legacy-common");
            assertThat(pkg.packageName()).isEqualTo("旧版通用包");
            assertThat(pkg.files()).containsKey("02-data-elements.csv");
            assertThat(contract.summary(pkg)).containsEntry("compatibilityMode", "LEGACY_V1");
        });
    }

    @Test
    void parseCatalogCompatible_rejectsExplicitUnsupportedSchema() {
        byte[] catalogJson = """
            {
              "schemaVersion": "3.0",
              "packages": [
                {"code": "legacy-common", "name": "旧版通用包", "category": "数据元"}
              ]
            }
            """.getBytes(UTF_8);

        assertContractCode("MANIFEST_SCHEMA_UNSUPPORTED", () -> contract.parseCatalogCompatible(catalogJson, Map.of()));
    }

    private void assertContractCode(String expectedCode, ThrowingOperation operation) {
        assertThatThrownBy(operation::run)
            .isInstanceOfSatisfying(
                StandardPackageContractException.class,
                error -> assertThat(error.code()).isEqualTo(expectedCode)
            );
    }

    private String validManifestJson() {
        return """
        {
          "schemaVersion": "2.0",
          "packageCode": "dts-core-person",
          "packageName": "人员基础标准",
          "packageVersion": "1.0.0",
          "category": "通用基础",
          "industry": "COMMON",
          "releasedAt": "2026-07-23T00:00:00Z",
          "effectiveFrom": "2026-07-23",
          "dependencies": [
            {"packageCode": "dts-core-common-enum", "minimumVersion": "1.0.0"}
          ],
          "replaces": [],
          "deprecated": false,
          "sourceRegisterRef": "SOURCE-REGISTER.json#dts-core-person",
          "licenseConclusion": "REVIEW_REQUIRED",
          "files": {
            "01-business-terms.csv": "8e5c85a5e62fc213bc207f6b9c73fb02cbd310d984c0317cd72f268a8043f661"
          }
        }
        """;
    }

    private Manifest manifest(
        String packageCode,
        String version,
        List<Dependency> dependencies,
        Map<String, String> files
    ) {
        return contract.parsePackage(
            """
            {
              "schemaVersion": "2.0",
              "packageCode": "%s",
              "packageName": "%s",
              "packageVersion": "%s",
              "category": "通用基础",
              "industry": "COMMON",
              "releasedAt": "2026-07-23T00:00:00Z",
              "effectiveFrom": "2026-07-23",
              "dependencies": %s,
              "replaces": [],
              "deprecated": false,
              "sourceRegisterRef": "SOURCE-REGISTER.json#%s",
              "licenseConclusion": "REVIEW_REQUIRED",
              "files": %s
            }
            """.formatted(
                packageCode,
                packageCode,
                version,
                writeJson(dependencies),
                packageCode,
                writeJson(files)
            )
                .getBytes(UTF_8)
        );
    }

    private String writeJson(Object value) {
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String sha256(String content) {
        return sha256(content.getBytes(UTF_8));
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run();
    }
}
