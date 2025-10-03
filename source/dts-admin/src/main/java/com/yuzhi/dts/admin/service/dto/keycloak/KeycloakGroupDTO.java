package com.yuzhi.dts.admin.service.dto.keycloak;

import java.util.*;

public class KeycloakGroupDTO {
    private String id;
    private String name;
    private String path;
    private String description;
    private Map<String, List<String>> attributes = new HashMap<>();
    private List<String> realmRoles = new ArrayList<>();
    private Map<String, List<String>> clientRoles = new HashMap<>();
    private List<KeycloakGroupDTO> subGroups = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Map<String, List<String>> getAttributes() { return attributes; }
    public void setAttributes(Map<String, List<String>> attributes) { this.attributes = attributes; }
    public List<String> getRealmRoles() { return realmRoles; }
    public void setRealmRoles(List<String> realmRoles) { this.realmRoles = realmRoles; }
    public Map<String, List<String>> getClientRoles() { return clientRoles; }
    public void setClientRoles(Map<String, List<String>> clientRoles) { this.clientRoles = clientRoles; }
    public List<KeycloakGroupDTO> getSubGroups() { return subGroups; }
    public void setSubGroups(List<KeycloakGroupDTO> subGroups) { this.subGroups = subGroups; }
}
