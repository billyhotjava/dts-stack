package com.yuzhi.dts.admin.service.keycloak;

import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakGroupDTO;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakRoleDTO;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO;
import com.yuzhi.dts.admin.service.inmemory.InMemoryStores;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class InMemoryKeycloakAdminClient implements KeycloakAdminClient {

    private final InMemoryStores stores;

    public InMemoryKeycloakAdminClient(InMemoryStores stores) {
        this.stores = stores;
    }

    @Override
    public List<KeycloakUserDTO> listUsers(int first, int max, String accessToken) {
        return stores.listUsers(first, max);
    }

    @Override
    public Optional<KeycloakUserDTO> findByUsername(String username, String accessToken) {
        return stores.findUserByUsername(username);
    }

    @Override
    public Optional<KeycloakUserDTO> findById(String userId, String accessToken) {
        return Optional.ofNullable(stores.findUserById(userId));
    }

    @Override
    public KeycloakUserDTO createUser(KeycloakUserDTO payload, String accessToken) {
        ensureUniqueUsername(payload.getUsername());
        return stores.createUser(copyUser(payload));
    }

    @Override
    public KeycloakUserDTO updateUser(String userId, KeycloakUserDTO payload, String accessToken) {
        KeycloakUserDTO existing = stores.findUserById(userId);
        if (existing == null) {
            throw new IllegalArgumentException("Keycloak user not found: " + userId);
        }
        if (payload.getUsername() != null && !payload.getUsername().equalsIgnoreCase(existing.getUsername())) {
            ensureUniqueUsername(payload.getUsername());
        }
        merge(existing, payload);
        stores.users.put(existing.getId(), existing);
        return existing;
    }

    @Override
    public void deleteUser(String userId, String accessToken) {
        if (stores.users.remove(userId) == null) {
            throw new IllegalArgumentException("Keycloak user not found: " + userId);
        }
    }

    @Override
    public void resetPassword(String userId, String newPassword, boolean temporary, String accessToken) {
        if (!stores.users.containsKey(userId)) {
            throw new IllegalArgumentException("Keycloak user not found: " + userId);
        }
        // In-memory stub does not store password; nothing to do.
    }

    @Override
    public List<KeycloakGroupDTO> listGroups(String accessToken) {
        List<KeycloakGroupDTO> roots = new ArrayList<>();
        for (KeycloakGroupDTO group : stores.groups.values()) {
            if (!stores.groupParents.containsKey(group.getId())) {
                roots.add(copyGroupTree(group));
            }
        }
        return roots;
    }

    @Override
    public Optional<KeycloakGroupDTO> findGroup(String groupId, String accessToken) {
        KeycloakGroupDTO group = stores.groups.get(groupId);
        return group == null ? Optional.empty() : Optional.of(copyGroupTree(group));
    }

    @Override
    public KeycloakGroupDTO createGroup(KeycloakGroupDTO payload, String parentGroupId, String accessToken) {
        KeycloakGroupDTO group = new KeycloakGroupDTO();
        group.setId(UUID.randomUUID().toString());
        group.setName(payload.getName());
        group.setAttributes(copyAttributes(payload.getAttributes()));
        group.setRealmRoles(
            payload.getRealmRoles() == null ? new ArrayList<>() : new ArrayList<>(payload.getRealmRoles())
        );
        group.setClientRoles(copyAttributes(payload.getClientRoles()));
        stores.groups.put(group.getId(), group);
        if (StringUtils.isNotBlank(parentGroupId)) {
            stores.groupParents.put(group.getId(), parentGroupId);
            KeycloakGroupDTO parent = stores.groups.get(parentGroupId);
            if (parent != null) {
                parent.getSubGroups().add(group);
            }
        } else {
            stores.groupParents.remove(group.getId());
        }
        return copyGroupTree(group);
    }

    @Override
    public KeycloakGroupDTO updateGroup(String groupId, KeycloakGroupDTO payload, String accessToken) {
        KeycloakGroupDTO existing = stores.groups.get(groupId);
        if (existing == null) {
            throw new IllegalArgumentException("Keycloak group not found: " + groupId);
        }
        if (payload.getName() != null) {
            existing.setName(payload.getName());
        }
        if (payload.getAttributes() != null && !payload.getAttributes().isEmpty()) {
            existing.setAttributes(copyAttributes(payload.getAttributes()));
        }
        return copyGroupTree(existing);
    }

    @Override
    public void deleteGroup(String groupId, String accessToken) {
        KeycloakGroupDTO existing = stores.groups.get(groupId);
        if (existing == null) {
            throw new IllegalArgumentException("Keycloak group not found: " + groupId);
        }
        List<KeycloakGroupDTO> children = new ArrayList<>(existing.getSubGroups());
        for (KeycloakGroupDTO child : children) {
            deleteGroup(child.getId(), accessToken);
        }
        String parentId = stores.groupParents.remove(groupId);
        stores.groups.remove(groupId);
        if (parentId != null) {
            KeycloakGroupDTO parent = stores.groups.get(parentId);
            if (parent != null) {
                parent.getSubGroups().removeIf(group -> groupId.equals(group.getId()));
            }
        }
    }

    @Override
    public Optional<KeycloakRoleDTO> findRealmRole(String roleName, String accessToken) {
        if (roleName == null || roleName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(stores.roles.get(roleName));
    }

    @Override
    public KeycloakRoleDTO upsertRealmRole(KeycloakRoleDTO role, String accessToken) {
        if (role == null || role.getName() == null || role.getName().isBlank()) {
            throw new IllegalArgumentException("角色名称不能为空");
        }
        return stores.upsertRole(role);
    }

    @Override
    public List<KeycloakRoleDTO> listRealmRoles(String accessToken) {
        return stores.listRoles();
    }

    private void ensureUniqueUsername(String username) {
        if (username == null) {
            return;
        }
        stores
            .findUserByUsername(username)
            .ifPresent(u -> {
                throw new IllegalStateException("用户名已存在: " + username);
            });
    }

    private KeycloakUserDTO copyUser(KeycloakUserDTO source) {
        KeycloakUserDTO copy = new KeycloakUserDTO();
        merge(copy, source);
        return copy;
    }

    private void merge(KeycloakUserDTO target, KeycloakUserDTO source) {
        if (source.getUsername() != null) target.setUsername(source.getUsername());
        if (source.getEmail() != null) target.setEmail(source.getEmail());
        if (source.getFirstName() != null) target.setFirstName(source.getFirstName());
        if (source.getLastName() != null) target.setLastName(source.getLastName());
        if (source.getEnabled() != null) target.setEnabled(source.getEnabled());
        if (source.getEmailVerified() != null) target.setEmailVerified(source.getEmailVerified());
        if (source.getAttributes() != null && !source.getAttributes().isEmpty()) {
            Map<String, List<String>> attrs = new ConcurrentHashMap<>(source.getAttributes());
            target.setAttributes(attrs);
        }
        if (source.getGroups() != null && !source.getGroups().isEmpty()) {
            target.setGroups(List.copyOf(source.getGroups()));
        }
        if (source.getRealmRoles() != null && !source.getRealmRoles().isEmpty()) {
            target.setRealmRoles(List.copyOf(source.getRealmRoles()));
        }
        if (source.getClientRoles() != null && !source.getClientRoles().isEmpty()) {
            target.setClientRoles(new ConcurrentHashMap<>(source.getClientRoles()));
        }
        if (source.getCreatedTimestamp() != null) {
            target.setCreatedTimestamp(source.getCreatedTimestamp());
        }
        if (target.getCreatedTimestamp() == null) {
            target.setCreatedTimestamp(System.currentTimeMillis());
        }
        if (target.getEnabled() == null) {
            target.setEnabled(Boolean.TRUE);
        }
    }

    private Map<String, List<String>> copyAttributes(Map<String, List<String>> source) {
        Map<String, List<String>> attributes = new LinkedHashMap<>();
        if (source != null) {
            source.forEach((key, value) -> attributes.put(key, value == null ? new ArrayList<>() : new ArrayList<>(value)));
        }
        return attributes;
    }

    private KeycloakGroupDTO copyGroupTree(KeycloakGroupDTO source) {
        KeycloakGroupDTO copy = new KeycloakGroupDTO();
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setPath(source.getPath());
        copy.setAttributes(copyAttributes(source.getAttributes()));
        copy.setRealmRoles(
            source.getRealmRoles() == null ? new ArrayList<>() : new ArrayList<>(source.getRealmRoles())
        );
        copy.setClientRoles(copyAttributes(source.getClientRoles()));
        List<KeycloakGroupDTO> children = new ArrayList<>();
        if (source.getSubGroups() != null) {
            for (KeycloakGroupDTO child : source.getSubGroups()) {
                children.add(copyGroupTree(child));
            }
        }
        copy.setSubGroups(children);
        return copy;
    }
}
