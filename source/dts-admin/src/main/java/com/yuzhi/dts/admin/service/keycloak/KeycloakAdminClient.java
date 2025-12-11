package com.yuzhi.dts.admin.service.keycloak;

import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakGroupDTO;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakRoleDTO;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO;
import java.util.List;
import java.util.Optional;

public interface KeycloakAdminClient {

    List<KeycloakUserDTO> listUsers(int first, int max, String accessToken);

    /**
     * Fuzzy search users by keyword (username/fullName/email depending on Keycloak implementation).
     */
    List<KeycloakUserDTO> searchUsers(String keyword, String accessToken);

    Optional<KeycloakUserDTO> findByUsername(String username, String accessToken);

    Optional<KeycloakUserDTO> findById(String userId, String accessToken);

    KeycloakUserDTO createUser(KeycloakUserDTO payload, String accessToken);

    KeycloakUserDTO updateUser(String userId, KeycloakUserDTO payload, String accessToken);

    void deleteUser(String userId, String accessToken);

    void resetPassword(String userId, String newPassword, boolean temporary, String accessToken);

    List<KeycloakGroupDTO> listGroups(String accessToken);

    Optional<KeycloakGroupDTO> findGroup(String groupId, String accessToken);

    /**
     * Resolve a group by its full path (e.g., "/S10/部门A/科室B").
     */
    Optional<KeycloakGroupDTO> findGroupByPath(String path, String accessToken);

    KeycloakGroupDTO createGroup(KeycloakGroupDTO payload, String parentGroupId, String accessToken);

    KeycloakGroupDTO updateGroup(String groupId, KeycloakGroupDTO payload, String accessToken);

    void moveGroup(String groupId, String groupName, String parentGroupId, String accessToken);

    void deleteGroup(String groupId, String accessToken);

    /**
     * List groups the user currently belongs to.
     */
    List<KeycloakGroupDTO> listUserGroups(String userId, String accessToken);

    /**
     * Add/remove user to/from a group.
     */
    void addUserToGroup(String userId, String groupId, String accessToken);
    void removeUserFromGroup(String userId, String groupId, String accessToken);

    Optional<KeycloakRoleDTO> findRealmRole(String roleName, String accessToken);

    KeycloakRoleDTO upsertRealmRole(KeycloakRoleDTO role, String accessToken);

    List<KeycloakRoleDTO> listRealmRoles(String accessToken);

    /**
     * Assign realm roles to a user using Keycloak role-mappings endpoint.
     */
    void addRealmRolesToUser(String userId, List<String> roleNames, String accessToken);

    /**
     * Remove realm roles from a user using Keycloak role-mappings endpoint.
     */
    void removeRealmRolesFromUser(String userId, List<String> roleNames, String accessToken);

    /**
     * List realm role names mapped to a user.
     */
    List<String> listUserRealmRoles(String userId, String accessToken);

    /**
     * List users who have the specified realm role.
     */
    List<KeycloakUserDTO> listUsersByRealmRole(String roleName, String accessToken);

    /**
     * Delete a realm role by name.
     */
    void deleteRealmRole(String roleName, String accessToken);

    // ---- Client roles (for dts-system) ----

    /** Resolve client UUID by its clientId. */
    java.util.Optional<String> resolveClientUuid(String clientId, String accessToken);

    /** Find a client role by name under the given clientId. */
    java.util.Optional<KeycloakRoleDTO> findClientRole(String clientId, String roleName, String accessToken);

    /** Create or update a client role under the given clientId. */
    KeycloakRoleDTO upsertClientRole(String clientId, KeycloakRoleDTO role, String accessToken);

    /** Assign client roles to a user for the given clientId. */
    void addClientRolesToUser(String userId, String clientId, java.util.List<String> roleNames, String accessToken);

    /** Remove client roles from a user for the given clientId. */
    void removeClientRolesFromUser(String userId, String clientId, java.util.List<String> roleNames, String accessToken);
}
