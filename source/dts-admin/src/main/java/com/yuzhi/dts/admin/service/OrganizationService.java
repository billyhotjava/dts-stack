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
import java.util.Objects;
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

    private static final String UNASSIGNED_ORG_NAME = "待分配";
    private static final String UNASSIGNED_DESCRIPTION = "待分配用户暂存组织";
    private static final String UNASSIGNED_DATA_LEVEL = "DATA_INTERNAL";

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

    public OrganizationNode create(String name, Long parentId, String description) {
        OrganizationNode entity = new OrganizationNode();
        entity.setName(name);
        entity.setDataLevel(resolveInheritedLevel(parentId, null));
        entity.setContact(null);
        entity.setPhone(null);
        entity.setDescription(description);

        OrganizationNode parent = null;
        if (parentId != null) {
            parent = repository.findById(parentId).orElseThrow();
            entity.setParent(parent);
            parent.getChildren().add(entity);
        }

        OrganizationNode saved = repository.save(entity);

        if (isKeycloakSyncEnabled()) {
            String token = resolveManagementToken();
            if (parent != null) {
                ensureGroupSynced(parent, token);
            }
            ensureGroupSynced(saved, token, parent);
            synchronizeGroup(saved, token);
        }

        return saved;
    }

    public Optional<OrganizationNode> update(Long id, String name, String description, Long parentId) {
        return repository
            .findById(id)
            .map(entity -> {
                Long previousParentId = getId(entity.getParent());
                OrganizationNode newParent = resolveParent(parentId, entity);
                if (!Objects.equals(getId(entity.getParent()), getId(newParent))) {
                    reassignParent(entity, newParent);
                }
                entity.setName(name);
                entity.setDataLevel(resolveInheritedLevel(getId(newParent), entity.getDataLevel()));
                entity.setContact(null);
                entity.setPhone(null);
                entity.setDescription(description);
                OrganizationNode saved = repository.save(entity);
                if (isKeycloakSyncEnabled()) {
                    String token = resolveManagementToken();
                    ensureGroupSynced(saved, token, saved.getParent());
                    Long currentParentId = getId(saved.getParent());
                    if (!Objects.equals(previousParentId, currentParentId)) {
                        moveGroup(saved, token, currentParentId == null ? null : saved.getParent().getKeycloakGroupId());
                    }
                    synchronizeGroup(saved, token);
                }
                return saved;
            });
    }

    public OrganizationNode ensureUnassignedRoot() {
        OrganizationNode node =
            repository
                .findFirstByNameAndParentIsNull(UNASSIGNED_ORG_NAME)
                .orElseGet(() -> create(UNASSIGNED_ORG_NAME, UNASSIGNED_DATA_LEVEL, null, null, null, UNASSIGNED_DESCRIPTION));
        boolean dirty = false;
        if (!Objects.equals(node.getDataLevel(), UNASSIGNED_DATA_LEVEL)) {
            node.setDataLevel(UNASSIGNED_DATA_LEVEL);
            dirty = true;
        }
        if (!Objects.equals(node.getDescription(), UNASSIGNED_DESCRIPTION)) {
            node.setDescription(UNASSIGNED_DESCRIPTION);
            dirty = true;
        }
        if (dirty) {
            node = repository.save(node);
        }
        if (isKeycloakSyncEnabled()) {
            String token = resolveManagementToken();
            ensureGroupSynced(node, token, null);
            synchronizeGroup(node, token);
        }
        return node;
    }

    public void pushTreeToKeycloak() {
        if (!isKeycloakSyncEnabled()) {
            return;
        }
        String token = resolveManagementToken();
        for (OrganizationNode root : findTree()) {
            syncSubtree(root, token);
        }
    }

    private OrganizationNode resolveParent(Long parentId, OrganizationNode child) {
        if (parentId == null) {
            return null;
        }
        if (child.getId() != null && Objects.equals(child.getId(), parentId)) {
            throw new IllegalArgumentException("不能将组织设置为自己的父级");
        }
        OrganizationNode parent = repository
            .findById(parentId)
            .orElseThrow(() -> new IllegalArgumentException("指定的上级组织不存在: " + parentId));
        if (createsCycle(parent, child)) {
            throw new IllegalArgumentException("不能将组织移动到其子节点之下");
        }
        return parent;
    }

    private boolean createsCycle(OrganizationNode candidateParent, OrganizationNode child) {
        OrganizationNode cursor = candidateParent;
        while (cursor != null) {
            if (child.getId() != null && Objects.equals(child.getId(), cursor.getId())) {
                return true;
            }
            cursor = cursor.getParent();
        }
        return false;
    }

    private void reassignParent(OrganizationNode node, OrganizationNode newParent) {
        OrganizationNode currentParent = node.getParent();
        if (currentParent != null && currentParent.getChildren() != null) {
            currentParent.getChildren().removeIf(child -> Objects.equals(child.getId(), node.getId()));
        }
        node.setParent(newParent);
        if (newParent != null) {
            if (newParent.getChildren() == null) {
                newParent.setChildren(new ArrayList<>());
            }
            if (newParent.getChildren().stream().noneMatch(child -> Objects.equals(child.getId(), node.getId()))) {
                newParent.getChildren().add(node);
            }
        }
    }

    private Long getId(OrganizationNode node) {
        return node == null ? null : node.getId();
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
            try {
                if (keycloakAdminClient.findGroup(node.getKeycloakGroupId(), token).isPresent()) {
                    return;
                }
                LOG.warn(
                    "Keycloak group {} referenced by org {} (id={}) is missing, recreating",
                    node.getKeycloakGroupId(),
                    node.getName(),
                    node.getId()
                );
            } catch (RuntimeException ex) {
                LOG.warn(
                    "Failed to verify Keycloak group {} for org {} (id={}): {}",
                    node.getKeycloakGroupId(),
                    node.getName(),
                    node.getId(),
                    ex.getMessage()
                );
            }
            node.setKeycloakGroupId(null);
            repository.save(node);
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

    private void syncSubtree(OrganizationNode node, String token) {
        ensureGroupSynced(node, token, node.getParent());
        synchronizeGroup(node, token);
        if (node.getChildren() != null) {
            for (OrganizationNode child : node.getChildren()) {
                syncSubtree(child, token);
            }
        }
    }

    private void moveGroup(OrganizationNode node, String token, String parentGroupId) {
        if (StringUtils.isBlank(node.getKeycloakGroupId())) {
            return;
        }
        try {
            keycloakAdminClient.moveGroup(node.getKeycloakGroupId(), node.getName(), parentGroupId, token);
            LOG.info(
                "Moved Keycloak group {} under parent {} (org id={})",
                node.getKeycloakGroupId(),
                parentGroupId,
                node.getId()
            );
        } catch (RuntimeException ex) {
            throw new IllegalStateException("移动 Keycloak 组失败: " + ex.getMessage(), ex);
        }
    }

    private KeycloakGroupDTO toKeycloakGroupDto(OrganizationNode node) {
        KeycloakGroupDTO dto = new KeycloakGroupDTO();
        dto.setId(node.getKeycloakGroupId());
        dto.setName(node.getName());
        dto.setDescription(StringUtils.trimToNull(node.getDescription()));
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
        String description = StringUtils.trimToNull(node.getDescription());
        if (description != null) {
            attributes.put("description", List.of(description));
        } else {
            attributes.put("description", List.of());
        }
        if (node.getId() != null) {
            attributes.put("dts_org_id", List.of(String.valueOf(node.getId())));
        }
        return attributes;
    }

    private String normalizeDataLevel(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
