package com.yuzhi.dts.admin.service.infra.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;

public class UpsertInfraDataSourcePayload {

    @NotBlank
    private String name;

    @NotBlank
    private String type;

    @NotBlank
    private String jdbcUrl;

    private String username;

    private String description;

    private Map<String, Object> props;

    private Map<String, Object> secrets;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getProps() {
        if (props == null) {
            props = new HashMap<>();
        }
        return props;
    }

    public void setProps(Map<String, Object> props) {
        this.props = props != null ? new HashMap<>(props) : new HashMap<>();
    }

    public Map<String, Object> getSecrets() {
        if (secrets == null) {
            secrets = new HashMap<>();
        }
        return secrets;
    }

    public void setSecrets(Map<String, Object> secrets) {
        this.secrets = secrets != null ? new HashMap<>(secrets) : new HashMap<>();
    }
}
