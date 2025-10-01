package com.yuzhi.dts.admin.service.keycloak;

import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO;
import com.yuzhi.dts.admin.service.inmemory.InMemoryStores;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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
}
