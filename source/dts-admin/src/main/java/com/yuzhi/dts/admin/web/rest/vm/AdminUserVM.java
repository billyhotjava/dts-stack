package com.yuzhi.dts.admin.web.rest.vm;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class AdminUserVM {

    private Long id;
    private String keycloakId;
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private String personSecurityLevel;
    private List<String> realmRoles = new ArrayList<>();
    private List<String> groupPaths = new ArrayList<>();
    private boolean enabled;
    private Instant lastSyncAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKeycloakId() {
        return keycloakId;
    }

    public void setKeycloakId(String keycloakId) {
        this.keycloakId = keycloakId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPersonSecurityLevel() {
        return personSecurityLevel;
    }

    public void setPersonSecurityLevel(String personSecurityLevel) {
        this.personSecurityLevel = personSecurityLevel;
    }

    public List<String> getRealmRoles() {
        return realmRoles;
    }

    public void setRealmRoles(List<String> realmRoles) {
        this.realmRoles = realmRoles == null ? new ArrayList<>() : realmRoles;
    }

    public List<String> getGroupPaths() {
        return groupPaths;
    }

    public void setGroupPaths(List<String> groupPaths) {
        this.groupPaths = groupPaths == null ? new ArrayList<>() : groupPaths;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getLastSyncAt() {
        return lastSyncAt;
    }

    public void setLastSyncAt(Instant lastSyncAt) {
        this.lastSyncAt = lastSyncAt;
    }
}
