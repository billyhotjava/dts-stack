package com.yuzhi.dts.admin.service.dto.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.*;

public class KeycloakUserDTO {
    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    private Boolean enabled;
    private Boolean emailVerified;
    private Map<String, List<String>> attributes = new HashMap<>();
    private List<String> groups = new ArrayList<>();
    private List<String> realmRoles = new ArrayList<>();
    private boolean realmRolesSpecified;
    private Map<String, List<String>> clientRoles = new HashMap<>();
    private Long createdTimestamp;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public Map<String, List<String>> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, List<String>> attributes) {
        this.attributes = attributes;
    }

    public List<String> getGroups() {
        return groups;
    }

    public void setGroups(List<String> groups) {
        this.groups = groups;
    }

    public List<String> getRealmRoles() {
        return realmRoles;
    }

    @JsonSetter("realmRoles")
    public void setRealmRoles(List<String> realmRoles) {
        this.realmRolesSpecified = true;
        this.realmRoles = realmRoles;
    }

    @JsonIgnore
    public boolean isRealmRolesSpecified() {
        return realmRolesSpecified;
    }

    public Map<String, List<String>> getClientRoles() {
        return clientRoles;
    }

    public void setClientRoles(Map<String, List<String>> clientRoles) {
        this.clientRoles = clientRoles;
    }

    public Long getCreatedTimestamp() {
        return createdTimestamp;
    }

    public void setCreatedTimestamp(Long createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }
}
