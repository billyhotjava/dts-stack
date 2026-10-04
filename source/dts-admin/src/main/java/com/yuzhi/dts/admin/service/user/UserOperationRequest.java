package com.yuzhi.dts.admin.service.user;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserOperationRequest {

    private String username;
    private String fullName;
    private String email;
    private String phone;
    private String personSecurityLevel;
    private List<String> realmRoles = new ArrayList<>();
    private List<String> groupPaths = new ArrayList<>();
    private Boolean enabled;
    private String reason;
    private Map<String, List<String>> attributes = new HashMap<>();
    private boolean realmRolesSpecified;
    private boolean groupPathsSpecified;

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
        this.realmRolesSpecified = true;
        this.realmRoles = filterDefaultRoles(realmRoles);
    }

    public boolean isRealmRolesSpecified() {
        return realmRolesSpecified;
    }

    public void markRealmRolesUnspecified() {
        this.realmRolesSpecified = false;
        this.realmRoles = new ArrayList<>();
    }

    public List<String> getGroupPaths() {
        return groupPaths;
    }

    public void setGroupPaths(List<String> groupPaths) {
        this.groupPathsSpecified = true;
        this.groupPaths = groupPaths == null ? new ArrayList<>() : groupPaths;
    }

    public boolean isGroupPathsSpecified() {
        return groupPathsSpecified;
    }

    public void markGroupPathsUnspecified() {
        this.groupPathsSpecified = false;
        this.groupPaths = new ArrayList<>();
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Map<String, List<String>> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, List<String>> attributes) {
        this.attributes = attributes == null ? new HashMap<>() : attributes;
    }

    private List<String> filterDefaultRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> filtered = new ArrayList<>();
        for (String role : roles) {
            if (role == null) {
                continue;
            }
            String lower = role.trim().toLowerCase();
            if (lower.isEmpty()) {
                continue;
            }
            if (lower.startsWith("default-roles-")) {
                continue;
            }
            if ("offline_access".equals(lower) || "uma_authorization".equals(lower)) {
                continue;
            }
            filtered.add(role);
        }
        return filtered;
    }
}
