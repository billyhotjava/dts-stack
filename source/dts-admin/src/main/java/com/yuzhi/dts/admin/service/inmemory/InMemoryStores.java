package com.yuzhi.dts.admin.service.inmemory;

import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakGroupDTO;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakRoleDTO;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO;
import com.yuzhi.dts.admin.service.dto.keycloak.ApprovalDTOs;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class InMemoryStores {

    public final Map<String, KeycloakUserDTO> users = new ConcurrentHashMap<>();
    public final Map<String, KeycloakRoleDTO> roles = new ConcurrentHashMap<>();
    public final Map<String, KeycloakGroupDTO> groups = new ConcurrentHashMap<>();
    public final Map<String, String> groupParents = new ConcurrentHashMap<>();

    public final Map<Long, ApprovalDTOs.ApprovalRequestDetail> approvals = new ConcurrentHashMap<>();

    private static String uid() { return UUID.randomUUID().toString(); }

    @PostConstruct
    public void seed() {
        // Switch to a clean cache for real Keycloak integration: keep maps empty at startup.
        users.clear();
        roles.clear();
        groups.clear();
        groupParents.clear();
        approvals.clear();
    }

    public KeycloakUserDTO findUserById(String id) { return users.get(id); }

    public Optional<KeycloakUserDTO> findUserByUsername(String username) {
        return users.values().stream().filter(u -> username.equalsIgnoreCase(u.getUsername())).findFirst();
    }

    public List<KeycloakUserDTO> listUsers(int first, int max) {
        return users.values().stream().skip(first).limit(max).toList();
    }

    public KeycloakUserDTO createUser(KeycloakUserDTO dto) {
        dto.setId(uid());
        if (dto.getEnabled() == null) dto.setEnabled(true);
        if (dto.getCreatedTimestamp() == null) dto.setCreatedTimestamp(System.currentTimeMillis());
        users.put(dto.getId(), dto);
        return dto;
    }

    public KeycloakRoleDTO upsertRole(KeycloakRoleDTO dto) {
        if (dto.getId() == null) dto.setId(uid());
        roles.put(dto.getName(), dto);
        return dto;
    }

    public List<KeycloakRoleDTO> listRoles() { return new ArrayList<>(roles.values()); }

    public boolean deleteRole(String name) { return roles.remove(name) != null; }

    public long nextApprovalId() { return Math.abs(ThreadLocalRandom.current().nextLong(1000, 999999)); }
}
