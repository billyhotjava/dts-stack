package com.yuzhi.dts.opmanager.runtime;

public record RuntimeStatus(
    String osName,
    String osArch,
    String javaVersion,
    String dataDir,
    String targetStackDir,
    boolean dockerEnabled,
    boolean dockerAvailable,
    String dockerVersion,
    boolean composeAvailable,
    String composeVersion,
    String portainerUrl
) {}
