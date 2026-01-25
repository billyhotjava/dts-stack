package com.yuzhi.dts.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.addax")
public class AddaxProperties {

    private boolean enabled = true;
    private String jobDir;
    private String image;
    private String dagId;
    // 代码层 fallback，实际配置通过 Admin 集成配置界面管理
    private String defaultWriterType = "postgresqlwriter";
    private String defaultWriterJdbcUrl;
    private String defaultWriterUsername;
    private String defaultWriterPassword;
    private String defaultWriterSchema;

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

    public String getDagId() {
        return dagId;
    }

    public void setDagId(String dagId) {
        this.dagId = dagId;
    }

    public String getDefaultWriterType() {
        return defaultWriterType;
    }

    public void setDefaultWriterType(String defaultWriterType) {
        this.defaultWriterType = defaultWriterType;
    }

    public String getDefaultWriterJdbcUrl() {
        return defaultWriterJdbcUrl;
    }

    public void setDefaultWriterJdbcUrl(String defaultWriterJdbcUrl) {
        this.defaultWriterJdbcUrl = defaultWriterJdbcUrl;
    }

    public String getDefaultWriterUsername() {
        return defaultWriterUsername;
    }

    public void setDefaultWriterUsername(String defaultWriterUsername) {
        this.defaultWriterUsername = defaultWriterUsername;
    }

    public String getDefaultWriterPassword() {
        return defaultWriterPassword;
    }

    public void setDefaultWriterPassword(String defaultWriterPassword) {
        this.defaultWriterPassword = defaultWriterPassword;
    }

    public String getDefaultWriterSchema() {
        return defaultWriterSchema;
    }

    public void setDefaultWriterSchema(String defaultWriterSchema) {
        this.defaultWriterSchema = defaultWriterSchema;
    }
}
