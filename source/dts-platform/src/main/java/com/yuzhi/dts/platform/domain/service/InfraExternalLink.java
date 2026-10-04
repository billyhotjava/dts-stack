package com.yuzhi.dts.platform.domain.service;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "infra_external_link")
public class InfraExternalLink extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "entry_key", length = 64, nullable = false, unique = true)
    private String entryKey;

    @Column(name = "name", length = 128)
    private String name;

    @Column(name = "url", length = 2048)
    private String url;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "status_api_enabled", nullable = false)
    private Boolean statusApiEnabled = Boolean.FALSE;

    @Column(name = "status_api_url", length = 2048)
    private String statusApiUrl;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEntryKey() {
        return entryKey;
    }

    public void setEntryKey(String entryKey) {
        this.entryKey = entryKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getStatusApiEnabled() {
        return statusApiEnabled;
    }

    public void setStatusApiEnabled(Boolean statusApiEnabled) {
        this.statusApiEnabled = statusApiEnabled;
    }

    public String getStatusApiUrl() {
        return statusApiUrl;
    }

    public void setStatusApiUrl(String statusApiUrl) {
        this.statusApiUrl = statusApiUrl;
    }
}
