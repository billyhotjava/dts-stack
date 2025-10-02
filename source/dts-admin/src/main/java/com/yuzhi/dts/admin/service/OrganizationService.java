package com.yuzhi.dts.admin.service;

import com.yuzhi.dts.admin.domain.OrganizationNode;
import com.yuzhi.dts.admin.repository.OrganizationRepository;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakGroupDTO;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAdminClient;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAuthService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrganizationService {

    private static final Logger LOG = LoggerFactory.getLogger(OrganizationService.class);

    private final OrganizationRepository repository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final KeycloakAuthService keycloakAuthService;
    private final String managementClientId;
    private final String managementClientSecret;

    public OrganizationService(
        OrganizationRepository repository,
        KeycloakAdminClient keycloakAdminClient,
        KeycloakAuthService keycloakAuthService,
        @Value("${dts.keycloak.admin-client-id:${OAUTH2_ADMIN_CLIENT_ID:}}") String managementClientId,
        @Value("${dts.keycloak.admin-client-secret:${OAUTH2_ADMIN_CLIENT_SECRET:}}") String managementClientSecret
    ) {
        this.repository = repository;
        this.keycloakAdminClient = keycloakAdminClient;
        this.keycloakAuthService = keycloakAuthService;
        this.managementClientId = managementClientId == null ? "" : managementClientId.trim();
        this.managementClientSecret = managementClientSecret == null ? "" : managementClientSecret.trim();
    }

    public List<OrganizationNode> findTree() {
        List<OrganizationNode> roots = repository.findByParentIsNullOrderByIdAsc();
        roots.forEach(this::touch);
        return roots;
    }

    private void touch(OrganizationNode node) {
        if (node.getChildren() != null) {
            node.getChildren().forEach(this::touch);
        }
    }

    public OrganizationNode create(
        String name,
        String dataLevel,
        Long parentId,
        String contact,
        String phone,
        String description
    ) {
        OrganizationNode entity = new OrganizationNode();
        entity.setName(name);
        entity.setDataLevel(dataLevel);
        entity.setContact(contact);
        entity.setPhone(phone);
        entity.setDescription(description);

        OrganizationNode parent = null;
        if (parentId != null) {
            parent = repository.findById(parentId).orElseThrow();
            entity.setParent(parent);
            parent.getChildren().add(entity);
        }

        if (isKeycloakSyncEnabled()) {
            String token = resolveManagementToken();
            if (parent != null) {
                ensureGroupSynced(parent, token);
            }
            ensureGroupSynced(entity, token, parent);
        }

        return repository.save(entity);
    }

    public Optional<OrganizationNode> update(
        Long id,
        String name,
        String dataLevel,
        String contact,
        String phone,
        String description
    ) {
        return repository
            .findById(id)
            .map(entity -> {
                entity.setName(name);
                entity.setDataLevel(dataLevel);
                entity.setContact(contact);
                entity.setPhone(phone);
                entity.setDescription(description);
                OrganizationNode saved = repository.save(entity);
                if (isKeycloakSyncEnabled()) {
                    String token = resolveManagementToken();
                    ensureGroupSynced(saved, token, saved.getParent());
                    synchronizeGroup(saved, token);
                }
                return saved;
            });
    }

    public void delete(Long id) {
        repository
            .findById(id)
            .ifPresent(entity -> {
                if (isKeycloakSyncEnabled()) {
                    String token = resolveManagementToken();
                    deleteKeycloakGroupRecursive(entity, token);
                }
                OrganizationNode parent = entity.getParent();
                if (parent != null && parent.getChildren() != null) {
                    parent.getChildren().remove(entity);
                }
                repository.delete(entity);
            });
    }

    private boolean isKeycloakSyncEnabled() {
        return StringUtils.isNotBlank(managementClientId) && StringUtils.isNotBlank(managementClientSecret);
    }

    private String resolveManagementToken() {
        try {
            var token = keycloakAuthService.obtainClientCredentialsToken(managementClientId, managementClientSecret);
            if (token == null || StringUtils.isBlank(token.accessToken())) {
                throw new IllegalStateException("Keycloak 管理客户端未返回 access_token");
            }
            return token.accessToken();
        } catch (Exception ex) {
            throw new IllegalStateException("获取 Keycloak 管理客户端访问令牌失败: " + ex.getMessage(), ex);
        }
    }

    private void ensureGroupSynced(OrganizationNode node, String token) {
        ensureGroupSynced(node, token, node.getParent());
    }

    private void ensureGroupSynced(OrganizationNode node, String token, OrganizationNode parent) {
        if (!isKeycloakSyncEnabled()) {
            return;
        }
        if (node == null) {
            return;
        }
        if (StringUtils.isNotBlank(node.getKeycloakGroupId())) {
            return;
        }
        OrganizationNode resolvedParent = parent != null ? parent : node.getParent();
        if (resolvedParent != null) {
            ensureGroupSynced(resolvedParent, token);
        }
        String parentGroupId = resolvedParent != null ? resolvedParent.getKeycloakGroupId() : null;
        KeycloakGroupDTO created = keycloakAdminClient.createGroup(toKeycloakGroupDto(node), parentGroupId, token);
        node.setKeycloakGroupId(created.getId());
        repository.save(node);
        LOG.info(
            "Created Keycloak group {} for organization {} (id={})",
            created.getId(),
            node.getName(),
            node.getId()
        );
    }

    private void synchronizeGroup(OrganizationNode node, String token) {
        if (node == null || StringUtils.isBlank(node.getKeycloakGroupId())) {
            return;
        }
        try {
            keycloakAdminClient.updateGroup(node.getKeycloakGroupId(), toKeycloakGroupDto(node), token);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("同步组织至 Keycloak 失败: " + ex.getMessage(), ex);
        }
    }

    private void deleteKeycloakGroupRecursive(OrganizationNode node, String token) {
        if (node.getChildren() != null) {
            // copy to avoid ConcurrentModification when JPA orphan removal kicks in
            List<OrganizationNode> childrenSnapshot = new ArrayList<>(node.getChildren());
            for (OrganizationNode child : childrenSnapshot) {
                deleteKeycloakGroupRecursive(child, token);
            }
        }
        if (StringUtils.isNotBlank(node.getKeycloakGroupId())) {
            try {
                keycloakAdminClient.deleteGroup(node.getKeycloakGroupId(), token);
                LOG.info(
                    "Deleted Keycloak group {} for organization {} (id={})",
                    node.getKeycloakGroupId(),
                    node.getName(),
                    node.getId()
                );
            } catch (RuntimeException ex) {
                throw new IllegalStateException("删除 Keycloak 组失败: " + ex.getMessage(), ex);
            }
        }
        node.setKeycloakGroupId(null);
    }

    private KeycloakGroupDTO toKeycloakGroupDto(OrganizationNode node) {
        KeycloakGroupDTO dto = new KeycloakGroupDTO();
        dto.setId(node.getKeycloakGroupId());
        dto.setName(node.getName());
        dto.setAttributes(buildGroupAttributes(node));
        return dto;
    }

    private Map<String, List<String>> buildGroupAttributes(OrganizationNode node) {
        Map<String, List<String>> attributes = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(node.getDataLevel())) {
            attributes.put("data_level", List.of(node.getDataLevel().toUpperCase(Locale.ROOT)));
        }
        if (StringUtils.isNotBlank(node.getContact())) {
            attributes.put("contact", List.of(node.getContact()));
        }
        if (StringUtils.isNotBlank(node.getPhone())) {
            attributes.put("phone", List.of(node.getPhone()));
        }
        if (StringUtils.isNotBlank(node.getDescription())) {
            attributes.put("description", List.of(node.getDescription()));
        }
        if (node.getId() != null) {
            attributes.put("dts_org_id", List.of(String.valueOf(node.getId())));
        }
        return attributes;
    }
}

