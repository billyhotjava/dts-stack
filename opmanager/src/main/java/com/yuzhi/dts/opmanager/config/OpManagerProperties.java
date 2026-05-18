package com.yuzhi.dts.opmanager.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "opmanager")
public class OpManagerProperties {

    private Path dataDir = Path.of("/var/lib/dts-opmanager");

    private List<Path> packageRoots = new ArrayList<>(List.of(Path.of("/var/lib/dts-opmanager/packages")));

    private Path targetStackDir = Path.of("/opt/dts-stack");

    private boolean dockerEnabled = true;

    private String portainerUrl = "";

    public Path getDataDir() {
        return dataDir;
    }

    public void setDataDir(Path dataDir) {
        this.dataDir = dataDir;
    }

    public List<Path> getPackageRoots() {
        return packageRoots;
    }

    public void setPackageRoots(List<Path> packageRoots) {
        this.packageRoots = packageRoots == null ? new ArrayList<>() : new ArrayList<>(packageRoots);
    }

    public Path getTargetStackDir() {
        return targetStackDir;
    }

    public void setTargetStackDir(Path targetStackDir) {
        this.targetStackDir = targetStackDir;
    }

    public boolean isDockerEnabled() {
        return dockerEnabled;
    }

    public void setDockerEnabled(boolean dockerEnabled) {
        this.dockerEnabled = dockerEnabled;
    }

    public String getPortainerUrl() {
        return portainerUrl;
    }

    public void setPortainerUrl(String portainerUrl) {
        this.portainerUrl = portainerUrl == null ? "" : portainerUrl;
    }

    public Path packageRegistryDir() {
        return dataDir.resolve("state/package-registry");
    }

    public Path uploadDir() {
        return dataDir.resolve("uploads");
    }

    public Path jobsDir() {
        return dataDir.resolve("jobs");
    }
}
