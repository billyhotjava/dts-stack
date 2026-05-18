package com.yuzhi.dts.opmanager.configfiles;

import static org.assertj.core.api.Assertions.assertThat;

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
        Files.writeString(context.packageDir().resolve(".env"), "BASE_DOMAIN=package.local\nDB_PASSWORD=package-secret\nNEW_FLAG=true\n");
        Files.writeString(context.targetDir().resolve("docker-compose-app.yml"), "services:\n  app:\n    image: old\n");
        Files.writeString(context.packageDir().resolve("docker-compose-app.yml"), "services:\n  app:\n    image: new\n");
        registerPackage(context);

        ConfigPrecheckResponse response = context.service().precheck(context.registrationId());

        assertThat(response.files()).extracting(ConfigFileReview::path).contains(".env", "docker-compose-app.yml");
        ConfigFileReview env = response.files().stream().filter(file -> file.path().equals(".env")).findFirst().orElseThrow();
        assertThat(env.status()).isEqualTo(ConfigFileStatus.MODIFIED);
        assertThat(env.risk()).isEqualTo(ConfigRisk.HIGH);
        assertThat(env.allowedActions()).contains(ConfigApplyAction.MERGE_ENV_ADD_KEYS, ConfigApplyAction.WRITE_PACKAGE_COPY);
    }

    @Test
    void mergeEnvAddsOnlyMissingPackageKeysAndKeepsLocalValues() throws Exception {
        TestContext context = newContext();
        Files.writeString(context.targetDir().resolve(".env"), "BASE_DOMAIN=site.local\nDB_PASSWORD=local-secret\n");
        Files.writeString(context.packageDir().resolve(".env"), "BASE_DOMAIN=package.local\nDB_PASSWORD=package-secret\nNEW_FLAG=true\n");
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
        Files.writeString(context.packageDir().resolve("docker-compose-app.yml"), "services:\n  app:\n    image: new\n");
        registerPackage(context);

        ConfigApplyResult result = context.service().apply(context.registrationId(), "docker-compose-app.yml", ConfigApplyAction.WRITE_PACKAGE_COPY);

        assertThat(result.changed()).isTrue();
        assertThat(Files.readString(context.targetDir().resolve("docker-compose-app.yml"))).contains("image: old");
        assertThat(result.writtenPath()).contains("docker-compose-app.yml.opmanager-");
        assertThat(Files.readString(Path.of(result.writtenPath()))).contains("image: new");
    }

    private TestContext newContext() throws Exception {
        Path targetDir = tempDir.resolve("target");
        Path packageRoot = tempDir.resolve("packages/package-1");
        Files.createDirectories(targetDir);
        Files.createDirectories(packageRoot);
        Files.writeString(
            packageRoot.resolve("manifest.json"),
            """
            {
              "schemaVersion": 1,
              "packageId": "package-1",
              "product": "dts-stack",
              "version": "2.2.4",
              "targetArch": "arm64",
              "files": []
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
        return new TestContext(targetDir, packageRoot, packageService, service);
    }

    private void registerPackage(TestContext context) {
        context.setRegistrationId(context.packageService().registerServerPath(context.packageDir()).id());
    }

    private static final class TestContext {
        private final Path targetDir;
        private final Path packageDir;
        private final UpgradePackageService packageService;
        private final ProtectedConfigService service;
        private String registrationId;

        private TestContext(Path targetDir, Path packageDir, UpgradePackageService packageService, ProtectedConfigService service) {
            this.targetDir = targetDir;
            this.packageDir = packageDir;
            this.packageService = packageService;
            this.service = service;
        }

        Path targetDir() {
            return targetDir;
        }

        Path packageDir() {
            return packageDir;
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
