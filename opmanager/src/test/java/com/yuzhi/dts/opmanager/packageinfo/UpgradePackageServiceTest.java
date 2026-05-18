package com.yuzhi.dts.opmanager.packageinfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.opmanager.config.OpManagerProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UpgradePackageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void validatesDirectoryPackageManifest() throws Exception {
        Path packageRoot = tempDir.resolve("incoming/dts-2.2.4-arm64");
        Files.createDirectories(packageRoot.resolve("dts-stack"));
        Files.writeString(packageRoot.resolve("dts-stack/imgversion.conf"), "DTS_ADMIN_IMAGE=dts-admin:2.2.4\n");
        Files.writeString(
            packageRoot.resolve("manifest.json"),
            """
            {
              "schemaVersion": 1,
              "packageId": "dts-2.2.4-arm64",
              "product": "dts-stack",
              "version": "2.2.4",
              "targetArch": "arm64",
              "files": [
                { "path": "dts-stack/imgversion.conf", "size": 32 }
              ]
            }
            """
        );

        UpgradePackageService service = newService(tempDir.resolve("incoming"));

        PackageRegistration registration = service.registerServerPath(packageRoot);

        assertThat(registration.validation().valid()).isTrue();
        assertThat(registration.validation().packageId()).isEqualTo("dts-2.2.4-arm64");
        assertThat(registration.validation().version()).isEqualTo("2.2.4");
        assertThat(registration.validation().messages()).isEmpty();
    }

    @Test
    void rejectsManifestPathTraversal() throws Exception {
        Path packageRoot = tempDir.resolve("incoming/bad-package");
        Files.createDirectories(packageRoot);
        Files.writeString(
            packageRoot.resolve("manifest.json"),
            """
            {
              "schemaVersion": 1,
              "packageId": "bad",
              "product": "dts-stack",
              "version": "2.2.4",
              "targetArch": "arm64",
              "files": [
                { "path": "../outside.txt" }
              ]
            }
            """
        );

        UpgradePackageService service = newService(tempDir.resolve("incoming"));

        PackageRegistration registration = service.registerServerPath(packageRoot);

        assertThat(registration.validation().valid()).isFalse();
        assertThat(registration.validation().messages()).anyMatch(message -> message.contains("unsafe manifest path"));
    }

    @Test
    void rejectsServerPathOutsideAllowList() {
        UpgradePackageService service = newService(tempDir.resolve("allowed"));

        assertThatThrownBy(() -> service.registerServerPath(tempDir.resolve("outside")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not under an allowed package root");
    }

    private UpgradePackageService newService(Path packageRoot) {
        OpManagerProperties properties = new OpManagerProperties();
        properties.setDataDir(tempDir.resolve("state"));
        properties.setPackageRoots(List.of(packageRoot));
        return new UpgradePackageService(properties, new ObjectMapper());
    }
}
