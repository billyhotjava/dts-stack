package com.yuzhi.dts.admin.service.keycloak;

import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO;
import java.util.List;
import java.util.Optional;

public interface KeycloakAdminClient {

    List<KeycloakUserDTO> listUsers(int first, int max);

    Optional<KeycloakUserDTO> findByUsername(String username);

    KeycloakUserDTO createUser(KeycloakUserDTO payload);

    KeycloakUserDTO updateUser(String userId, KeycloakUserDTO payload);

    void deleteUser(String userId);
}
