package com.yuzhi.dts.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.addax")
public class AddaxProperties {

    private boolean enabled = true;
    private String jobDir;
    private String image;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getJobDir() {
        return jobDir;
    }

    public void setJobDir(String jobDir) {
        this.jobDir = jobDir;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }
}
