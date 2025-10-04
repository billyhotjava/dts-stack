package com.yuzhi.dts.admin.service.keycloak;

import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakGroupDTO;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakRoleDTO;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO;
import java.util.List;
import java.util.Optional;

public interface KeycloakAdminClient {

    List<KeycloakUserDTO> listUsers(int first, int max, String accessToken);

    Optional<KeycloakUserDTO> findByUsername(String username, String accessToken);

    Optional<KeycloakUserDTO> findById(String userId, String accessToken);

    KeycloakUserDTO createUser(KeycloakUserDTO payload, String accessToken);

    KeycloakUserDTO updateUser(String userId, KeycloakUserDTO payload, String accessToken);

    void deleteUser(String userId, String accessToken);

    void resetPassword(String userId, String newPassword, boolean temporary, String accessToken);

    List<KeycloakGroupDTO> listGroups(String accessToken);

    Optional<KeycloakGroupDTO> findGroup(String groupId, String accessToken);

    KeycloakGroupDTO createGroup(KeycloakGroupDTO payload, String parentGroupId, String accessToken);

    KeycloakGroupDTO updateGroup(String groupId, KeycloakGroupDTO payload, String accessToken);

    void moveGroup(String groupId, String groupName, String parentGroupId, String accessToken);

    void deleteGroup(String groupId, String accessToken);

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
}
