package com.yuzhi.dts.platform.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.platform.hive")
public class HiveExecutionProperties {

    /** Whether Hive execution is enabled. */
    private boolean enabled = false;

    /** JDBC URL for HiveServer2, e.g. jdbc:hive2://host:port/default. */
    private String jdbcUrl;

    /** Login username for Hive connection. */
    private String username;

    /** Login password for Hive connection (optional, depends on auth method). */
    private String password;

    /** Additional JDBC properties such as SSL/Kerberos flags. */
    private Map<String, String> properties = new HashMap<>();

    /** Default schema to use if dataset does not specify hiveDatabase. */
    private String defaultSchema = "default";

    /** When true, failures will be recorded but will not propagate as runtime exceptions. */
    private boolean tolerant = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public String getDefaultSchema() {
        return defaultSchema;
    }

    public void setDefaultSchema(String defaultSchema) {
        this.defaultSchema = defaultSchema;
    }

    public boolean isTolerant() {
        return tolerant;
    }

    public void setTolerant(boolean tolerant) {
        this.tolerant = tolerant;
    }
}
