package com.yuzhi.dts.admin.service.inmemory;

import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakGroupDTO;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakRoleDTO;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO;
import com.yuzhi.dts.admin.service.dto.keycloak.ApprovalDTOs;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class InMemoryStores {

    public final Map<String, KeycloakUserDTO> users = new ConcurrentHashMap<>();
    public final Map<String, KeycloakRoleDTO> roles = new ConcurrentHashMap<>();
    public final Map<String, KeycloakGroupDTO> groups = new ConcurrentHashMap<>();

    public final Map<Long, ApprovalDTOs.ApprovalRequestDetail> approvals = new ConcurrentHashMap<>();

    private static String uid() { return UUID.randomUUID().toString(); }

    @PostConstruct
    public void seed() {
        // Seed some roles
        for (String r : List.of("SYSADMIN", "OPADMIN", "AUTHADMIN", "AUDITADMIN", "DATA_STEWARD", "DATA_ANALYST")) {
            KeycloakRoleDTO role = new KeycloakRoleDTO();
            role.setId(uid());
            role.setName(r);
            roles.put(role.getName(), role);
        }

        // Seed triad users
        addUser("sysadmin", "sysadmin@example.com", List.of("SYSADMIN"));
        addUser("authadmin", "authadmin@example.com", List.of("AUTHADMIN"));
        addUser("auditadmin", "auditadmin@example.com", List.of("AUDITADMIN"));

        // Seed example approval
        ApprovalDTOs.ApprovalRequestDetail ar = new ApprovalDTOs.ApprovalRequestDetail();
        ar.id = 202405001L;
        ar.requester = "sysadmin";
        ar.type = "CREATE_USER";
        ar.reason = "demo request";
        ar.createdAt = Instant.now().toString();
        ar.status = "PENDING";
        ar.approver = "authadmin";
        ApprovalDTOs.ApprovalItem item = new ApprovalDTOs.ApprovalItem();
        item.id = 5101L;
        item.targetKind = "USER";
        item.targetId = "dataops";
        item.seqNumber = 1;
        item.payload = "{}";
        ar.items = List.of(item);
        approvals.put(ar.id, ar);
    }

    private void addUser(String username, String email, List<String> realmRoles) {
        KeycloakUserDTO u = new KeycloakUserDTO();
        u.setId(uid());
        u.setUsername(username);
        u.setEmail(email);
        u.setFirstName(username);
        u.setEnabled(true);
        u.setCreatedTimestamp(System.currentTimeMillis());
        u.setRealmRoles(new ArrayList<>(realmRoles));
        Map<String, List<String>> attrs = new HashMap<>();
        attrs.put("person_security_level", List.of("CORE"));
        attrs.put("data_levels", List.of("DATA_TOP_SECRET", "DATA_SECRET"));
        attrs.put("phone", List.of("13800000000"));
        u.setAttributes(attrs);
        u.setGroups(List.of("/数据与智能中心/平台组"));
        users.put(u.getId(), u);
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
