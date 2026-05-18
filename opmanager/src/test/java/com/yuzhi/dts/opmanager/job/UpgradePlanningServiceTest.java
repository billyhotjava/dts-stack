package com.yuzhi.dts.opmanager.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.opmanager.config.OpManagerProperties;
import com.yuzhi.dts.opmanager.packageinfo.UpgradePackageService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UpgradePlanningServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void createsPlanByManifestPackageIdWhenRegistrationIdIsUnknown() throws Exception {
        Path incoming = tempDir.resolve("incoming");
        Path packageRoot = incoming.resolve("dts-2.2.4-arm64");
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
                { "path": "dts-stack/imgversion.conf" }
              ]
            }
            """
        );

        OpManagerProperties properties = new OpManagerProperties();
        properties.setDataDir(tempDir.resolve("state"));
        properties.setPackageRoots(List.of(incoming));
        ObjectMapper objectMapper = new ObjectMapper();
        UpgradePackageService packageService = new UpgradePackageService(properties, objectMapper);
        packageService.registerServerPath(packageRoot);

        UpgradePlanningService planningService = new UpgradePlanningService(packageService, new FileJobStore(properties, objectMapper));

        UpgradeJob job = planningService.createPlan("dts-2.2.4-arm64", "package id lookup");

        assertThat(job.packageId()).isEqualTo("dts-2.2.4-arm64");
        assertThat(job.packageRegistrationId()).startsWith("dts-2.2.4-arm64-");
    }
}
