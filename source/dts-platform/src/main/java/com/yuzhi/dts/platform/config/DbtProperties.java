package com.yuzhi.dts.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.dbt")
public class DbtProperties {

    private boolean enabled = true;
    private String configPath = "/opt/dts/upload/dbt-config.json";
    private String projectDir = "/opt/dts/dbt";
    private String profilesDir = "/opt/dts/dbt-profiles";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getConfigPath() {
        return configPath;
    }

    public void setConfigPath(String configPath) {
        this.configPath = configPath;
    }

    public String getProjectDir() {
        return projectDir;
    }

    public void setProjectDir(String projectDir) {
        this.projectDir = projectDir;
    }

    public String getProfilesDir() {
        return profilesDir;
    }

    public void setProfilesDir(String profilesDir) {
        this.profilesDir = profilesDir;
    }
}
