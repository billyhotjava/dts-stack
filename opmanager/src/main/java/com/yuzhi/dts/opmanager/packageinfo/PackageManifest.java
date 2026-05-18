package com.yuzhi.dts.opmanager.packageinfo;

import java.util.List;

public record PackageManifest(
    Integer schemaVersion,
    String packageId,
    String product,
    String version,
    String targetArch,
    String createdAt,
    List<PackageManifestFile> files,
    List<PackageManifestImage> images
) {}
