package com.yuzhi.dts.opmanager.configfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.opmanager.config.OpManagerProperties;
import com.yuzhi.dts.opmanager.packageinfo.UpgradePackageService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProtectedConfigServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void precheckFindsProtectedConfigDifferences() throws Exception {
        TestContext context = newContext();
        Files.writeString(context.targetDir().resolve(".env"), "BASE_DOMAIN=site.local\nDB_PASSWORD=local-secret\n");
        Files.writeString(context.packageStackDir().resolve(".env"), "BASE_DOMAIN=package.local\nDB_PASSWORD=package-secret\nNEW_FLAG=true\n");
        Files.writeString(context.targetDir().resolve("docker-compose-app.yml"), "services:\n  app:\n    image: old\n");
        Files.writeString(context.packageStackDir().resolve("docker-compose-app.yml"), "services:\n  app:\n    image: new\n");
        registerPackage(context);

        ConfigPrecheckResponse response = context.service().precheck(context.registrationId());

        assertThat(response.files()).extracting(ConfigFileReview::path).contains(".env", "docker-compose-app.yml");
        assertThat(response.packageStackDir()).isEqualTo(context.packageStackDir().toAbsolutePath().normalize().toString());
        ConfigFileReview env = response.files().stream().filter(file -> file.path().equals(".env")).findFirst().orElseThrow();
        assertThat(env.status()).isEqualTo(ConfigFileStatus.MODIFIED);
        assertThat(env.risk()).isEqualTo(ConfigRisk.HIGH);
        assertThat(env.allowedActions()).contains(ConfigApplyAction.MERGE_ENV_ADD_KEYS, ConfigApplyAction.WRITE_PACKAGE_COPY, ConfigApplyAction.USE_PACKAGE);
    }

    @Test
    void mergeEnvAddsOnlyMissingPackageKeysAndKeepsLocalValues() throws Exception {
        TestContext context = newContext();
        Files.writeString(context.targetDir().resolve(".env"), "BASE_DOMAIN=site.local\nDB_PASSWORD=local-secret\n");
        Files.writeString(context.packageStackDir().resolve(".env"), "BASE_DOMAIN=package.local\nDB_PASSWORD=package-secret\nNEW_FLAG=true\n");
        registerPackage(context);

        ConfigApplyResult result = context.service().apply(context.registrationId(), ".env", ConfigApplyAction.MERGE_ENV_ADD_KEYS);

        assertThat(result.changed()).isTrue();
        assertThat(Files.readString(context.targetDir().resolve(".env"))).contains("BASE_DOMAIN=site.local").contains("DB_PASSWORD=local-secret").contains("NEW_FLAG=true");
        assertThat(Files.readString(context.targetDir().resolve(".env"))).doesNotContain("BASE_DOMAIN=package.local");
        assertThat(Files.exists(Path.of(result.backupPath()))).isTrue();
    }

    @Test
    void packageCopyDoesNotOverwriteProtectedComposeFile() throws Exception {
        TestContext context = newContext();
        Files.writeString(context.targetDir().resolve("docker-compose-app.yml"), "services:\n  app:\n    image: old\n");
        Files.writeString(context.packageStackDir().resolve("docker-compose-app.yml"), "services:\n  app:\n    image: new\n");
        registerPackage(context);

        ConfigApplyResult result = context.service().apply(context.registrationId(), "docker-compose-app.yml", ConfigApplyAction.WRITE_PACKAGE_COPY);

        assertThat(result.changed()).isTrue();
        assertThat(Files.readString(context.targetDir().resolve("docker-compose-app.yml"))).contains("image: old");
        assertThat(result.writtenPath()).contains("docker-compose-app.yml.opmanager-");
        assertThat(Files.readString(Path.of(result.writtenPath()))).contains("image: new");
    }

    @Test
    void usePackageBacksUpAndOverwritesProtectedEnvFile() throws Exception {
        TestContext context = newContext();
        Files.writeString(context.targetDir().resolve(".env"), "BASE_DOMAIN=site.local\nDB_PASSWORD=local-secret\n");
        Files.writeString(context.packageStackDir().resolve(".env"), "BASE_DOMAIN=package.local\nDB_PASSWORD=package-secret\n");
        registerPackage(context);

        ConfigApplyResult result = context.service().apply(context.registrationId(), ".env", ConfigApplyAction.USE_PACKAGE);

        assertThat(result.changed()).isTrue();
        assertThat(result.backupPath()).isNotBlank();
        assertThat(Files.readString(Path.of(result.backupPath()))).contains("BASE_DOMAIN=site.local");
        assertThat(Files.readString(context.targetDir().resolve(".env"))).contains("BASE_DOMAIN=package.local").contains("DB_PASSWORD=package-secret");
    }

    @Test
    void packageLineApplyReplacesOnlySelectedLine() throws Exception {
        TestContext context = newContext();
        Files.writeString(context.targetDir().resolve(".env"), "BASE_DOMAIN=site.local\nDB_PASSWORD=local-secret\nKEEP_LOCAL=true\n");
        Files.writeString(context.packageStackDir().resolve(".env"), "BASE_DOMAIN=package.local\nDB_PASSWORD=package-secret\nKEEP_LOCAL=true\n");
        registerPackage(context);

        ConfigApplyResult result = context
            .service()
            .applyPackageLine(context.registrationId(), ".env", 1, 1, null, "BASE_DOMAIN=site.local", "BASE_DOMAIN=package.local");

        assertThat(result.changed()).isTrue();
        assertThat(Files.readString(context.targetDir().resolve(".env")))
            .contains("BASE_DOMAIN=package.local")
            .contains("DB_PASSWORD=local-secret")
            .contains("KEEP_LOCAL=true");
        assertThat(Files.readString(Path.of(result.backupPath()))).contains("BASE_DOMAIN=site.local");
    }

    @Test
    void packageLineApplyInsertsPackageOnlyLineAtSelectedPosition() throws Exception {
        TestContext context = newContext();
        Files.writeString(context.targetDir().resolve(".env"), "BASE_DOMAIN=site.local\nKEEP_LOCAL=true\n");
        Files.writeString(context.packageStackDir().resolve(".env"), "BASE_DOMAIN=site.local\nNEW_FLAG=true\nKEEP_LOCAL=true\n");
        registerPackage(context);

        ConfigApplyResult result = context.service().applyPackageLine(context.registrationId(), ".env", null, 2, 1, null, "NEW_FLAG=true");

        assertThat(result.changed()).isTrue();
        assertThat(Files.readAllLines(context.targetDir().resolve(".env"))).containsExactly("BASE_DOMAIN=site.local", "NEW_FLAG=true", "KEEP_LOCAL=true");
    }

    @Test
    void packageLineApplyRemovesLocalOnlyLine() throws Exception {
        TestContext context = newContext();
        Files.writeString(context.targetDir().resolve(".env"), "BASE_DOMAIN=site.local\nLOCAL_ONLY=true\nKEEP_LOCAL=true\n");
        Files.writeString(context.packageStackDir().resolve(".env"), "BASE_DOMAIN=site.local\nKEEP_LOCAL=true\n");
        registerPackage(context);

        ConfigApplyResult result = context.service().applyPackageLine(context.registrationId(), ".env", 2, null, null, "LOCAL_ONLY=true", null);

        assertThat(result.changed()).isTrue();
        assertThat(Files.readAllLines(context.targetDir().resolve(".env"))).containsExactly("BASE_DOMAIN=site.local", "KEEP_LOCAL=true");
    }

    @Test
    void packageLineApplyRejectsStaleLocalLine() throws Exception {
        TestContext context = newContext();
        Files.writeString(context.targetDir().resolve(".env"), "BASE_DOMAIN=site.local\n");
        Files.writeString(context.packageStackDir().resolve(".env"), "BASE_DOMAIN=package.local\n");
        registerPackage(context);

        assertThatThrownBy(() -> context.service().applyPackageLine(context.registrationId(), ".env", 1, 1, null, "BASE_DOMAIN=older.local", "BASE_DOMAIN=package.local"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("local line changed");
    }

    private TestContext newContext() throws Exception {
        Path targetDir = tempDir.resolve("target");
        Path packageRoot = tempDir.resolve("packages/package-1");
        Path packageStackDir = packageRoot.resolve("dts-stack");
        Files.createDirectories(targetDir);
        Files.createDirectories(packageRoot.resolve("images"));
        Files.createDirectories(packageStackDir);
        Files.createDirectories(packageRoot.resolve("misc"));
        Files.writeString(packageRoot.resolve("images/dts-admin.tar"), "fake image tar");
        Files.writeString(
            packageStackDir.resolve("imgversion.conf"),
            "IMAGE_DTS_ADMIN=dts-admin:2.2.4\n"
        );
        Files.writeString(
            packageRoot.resolve("manifest.json"),
            """
            {
              "schemaVersion": 1,
              "packageId": "package-1",
              "product": "dts-stack",
              "version": "2.2.4",
              "targetArch": "arm64",
              "files": [
                { "path": "dts-stack/imgversion.conf" }
              ]
            }
            """
        );
        OpManagerProperties properties = new OpManagerProperties();
        properties.setDataDir(tempDir.resolve("state"));
        properties.setPackageRoots(List.of(tempDir.resolve("packages")));
        properties.setTargetStackDir(targetDir);
        ObjectMapper objectMapper = new ObjectMapper();
        UpgradePackageService packageService = new UpgradePackageService(properties, objectMapper);
        ProtectedConfigService service = new ProtectedConfigService(properties, packageService);
        return new TestContext(targetDir, packageRoot, packageStackDir, packageService, service);
    }

    private void registerPackage(TestContext context) {
        context.setRegistrationId(context.packageService().registerServerPath(context.packageDir()).id());
    }

    private static final class TestContext {
        private final Path targetDir;
        private final Path packageDir;
        private final Path packageStackDir;
        private final UpgradePackageService packageService;
        private final ProtectedConfigService service;
        private String registrationId;

        private TestContext(Path targetDir, Path packageDir, Path packageStackDir, UpgradePackageService packageService, ProtectedConfigService service) {
            this.targetDir = targetDir;
            this.packageDir = packageDir;
            this.packageStackDir = packageStackDir;
            this.packageService = packageService;
            this.service = service;
        }

        Path targetDir() {
            return targetDir;
        }

        Path packageDir() {
            return packageDir;
        }

        Path packageStackDir() {
            return packageStackDir;
        }

        UpgradePackageService packageService() {
            return packageService;
        }

        ProtectedConfigService service() {
            return service;
        }

        String registrationId() {
            return registrationId;
        }

        void setRegistrationId(String registrationId) {
            this.registrationId = registrationId;
        }
    }
}
