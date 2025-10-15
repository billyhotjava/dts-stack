package com.yuzhi.dts.admin.service;

import com.yuzhi.dts.admin.domain.OrganizationNode;
import com.yuzhi.dts.admin.repository.OrganizationRepository;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakGroupDTO;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAdminClient;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAuthService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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

    private static final Duration PROVISIONING_RETRY_BACKOFF = Duration.ofSeconds(30);

    private final OrganizationRepository repository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final KeycloakAuthService keycloakAuthService;
    private final String managementClientId;
    private final String managementClientSecret;
    private final boolean groupProvisioningEnabled;
    private final String defaultRootName;
    private final String defaultUnassignedName;
    private final String defaultUnassignedDescription;
    private final String defaultUnassignedDataLevel;
    private final AtomicLong provisioningRetryAfter = new AtomicLong(0L);
    private final AtomicBoolean resyncPending = new AtomicBoolean(false);

    public OrganizationService(
        OrganizationRepository repository,
        KeycloakAdminClient keycloakAdminClient,
        KeycloakAuthService keycloakAuthService,
        @Value("${dts.keycloak.admin-client-id:${OAUTH2_ADMIN_CLIENT_ID:}}") String managementClientId,
        @Value("${dts.keycloak.admin-client-secret:${OAUTH2_ADMIN_CLIENT_SECRET:}}") String managementClientSecret,
        @Value("${dts.keycloak.group-provisioning-enabled:false}") boolean groupProvisioningEnabled,
        @Value("${dts.organization.default-root-name:}") String defaultRootName,
        @Value("${dts.organization.unassigned-name:}") String defaultUnassignedName,
        @Value("${dts.organization.unassigned-description:}") String defaultUnassignedDescription,
        @Value("${dts.organization.unassigned-data-level:}") String defaultUnassignedDataLevel
    ) {
        this.repository = repository;
        this.keycloakAdminClient = keycloakAdminClient;
        this.keycloakAuthService = keycloakAuthService;
        this.managementClientId = managementClientId == null ? "" : managementClientId.trim();
        this.managementClientSecret = managementClientSecret == null ? "" : managementClientSecret.trim();
        this.groupProvisioningEnabled = groupProvisioningEnabled;
        this.defaultRootName = StringUtils.trimToEmpty(defaultRootName);
        this.defaultUnassignedName = StringUtils.trimToEmpty(defaultUnassignedName);
        this.defaultUnassignedDescription = StringUtils.trimToEmpty(defaultUnassignedDescription);
        this.defaultUnassignedDataLevel = normalizeDataLevel(defaultUnassignedDataLevel);
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
        OrganizationNode parent = null;
        if (parentId == null) {
            // Only one root organization is allowed
            if (!repository.findByParentIsNullOrderByIdAsc().isEmpty()) {
                throw new IllegalArgumentException("仅允许存在一个根部门");
            }
        }
        if (parentId != null) {
            parent = repository.findById(parentId).orElseThrow();
        }

        OrganizationNode entity = new OrganizationNode();
        entity.setName(name);
        entity.setDataLevel(determineDataLevel(parent, null));
        entity.setContact(null);
        entity.setPhone(null);
        entity.setDescription(description);

        if (parent != null) {
            entity.setParent(parent);
            if (parent.getChildren() == null) {
                parent.setChildren(new ArrayList<>());
            }
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
                entity.setDataLevel(determineDataLevel(newParent, entity.getDataLevel()));
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
        OrganizationNode existingRoot = repository.findByParentIsNullOrderByIdAsc().stream().findFirst().orElse(null);
        if (StringUtils.isBlank(defaultRootName)) {
            return existingRoot;
        }
        OrganizationNode root = repository.findFirstByNameAndParentIsNull(defaultRootName).orElse(existingRoot);
        if (root == null) {
            root = create(defaultRootName, null, null);
        }
        if (StringUtils.isBlank(defaultUnassignedName)) {
            return root;
        }
        OrganizationNode finalRoot = root;
        OrganizationNode node = repository
            .findFirstByParentIdAndName(root.getId(), defaultUnassignedName)
            .orElseGet(() -> create(defaultUnassignedName, finalRoot.getId(), defaultUnassignedDescription));

        boolean dirty = false;
        if (StringUtils.isNotBlank(defaultUnassignedDataLevel) && !Objects.equals(node.getDataLevel(), defaultUnassignedDataLevel)) {
            node.setDataLevel(defaultUnassignedDataLevel);
            dirty = true;
        }
        if (StringUtils.isNotBlank(defaultUnassignedDescription) && !Objects.equals(node.getDescription(), defaultUnassignedDescription)) {
            node.setDescription(defaultUnassignedDescription);
            dirty = true;
        }
        if (dirty) {
            node = repository.save(node);
        }
        if (isKeycloakSyncEnabled()) {
            String token = resolveManagementToken();
            ensureGroupSynced(root, token, null);
            synchronizeGroup(root, token);
            ensureGroupSynced(node, token, root);
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
        node.setParent(newParent);
        if (currentParent != null && currentParent.getChildren() != null) {
            currentParent.getChildren().removeIf(child -> Objects.equals(child.getId(), node.getId()));
        }
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
        if (!groupProvisioningEnabled) {
            return false;
        }
        if (StringUtils.isBlank(managementClientId) || StringUtils.isBlank(managementClientSecret)) {
            return false;
        }
        long retryAfter = provisioningRetryAfter.get();
        if (retryAfter > 0) {
            long now = System.currentTimeMillis();
            if (now < retryAfter) {
                resyncPending.set(true);
                return false;
            }
        }
        return true;
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
                    markProvisioningHealthy();
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

        // Reuse existing Keycloak groups when possible to avoid duplicate-creation failures.
        String expectedPath = buildKeycloakGroupPath(node);
        if (StringUtils.isNotBlank(expectedPath)) {
            try {
                keycloakAdminClient
                    .findGroupByPath(expectedPath, token)
                    .ifPresent(existing -> {
                        node.setKeycloakGroupId(existing.getId());
                        repository.save(node);
                        markProvisioningHealthy();
                        LOG.info(
                            "Reused Keycloak group {} for organization {} (id={}) via path {}",
                            existing.getId(),
                            node.getName(),
                            node.getId(),
                            expectedPath
                        );
                    });
                if (StringUtils.isNotBlank(node.getKeycloakGroupId())) {
                    return;
                }
            } catch (RuntimeException ex) {
                LOG.warn("Failed probing Keycloak group by path {}: {}", expectedPath, ex.getMessage());
            }
        }

        LOG.debug(
            "Skip auto-provisioning Keycloak group for organization {} (id={}); set dts.keycloak.group-provisioning-enabled=true to enable creation",
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
            markProvisioningHealthy();
        } catch (RuntimeException ex) {
            suppressProvisioning("update", node, ex);
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
                markProvisioningHealthy();
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
            markProvisioningHealthy();
            LOG.info(
                "Moved Keycloak group {} under parent {} (org id={})",
                node.getKeycloakGroupId(),
                parentGroupId,
                node.getId()
            );
        } catch (RuntimeException ex) {
            suppressProvisioning("move", node, ex);
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

    private String buildKeycloakGroupPath(OrganizationNode node) {
        if (node == null) {
            return null;
        }
        List<String> segments = new ArrayList<>();
        OrganizationNode cursor = node;
        while (cursor != null) {
            String name = StringUtils.trimToNull(cursor.getName());
            if (name != null) {
                segments.add(0, name);
            }
            cursor = cursor.getParent();
        }
        if (segments.isEmpty()) {
            return null;
        }
        return "/" + String.join("/", segments);
    }

    private String determineDataLevel(OrganizationNode parent, String fallback) {
        String candidate = parent != null ? parent.getDataLevel() : fallback;
        String normalized = normalizeDataLevel(candidate);
        if (StringUtils.isNotBlank(normalized)) {
            return normalized;
        }
        return defaultUnassignedDataLevel;
    }

    private String normalizeDataLevel(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private void markProvisioningHealthy() {
        long previous = provisioningRetryAfter.getAndSet(0L);
        long now = System.currentTimeMillis();
        if (previous > now) {
            LOG.info("Re-enabled Keycloak group provisioning after successful operation");
            if (resyncPending.compareAndSet(true, false)) {
                try {
                    pushTreeToKeycloak();
                } catch (RuntimeException ex) {
                    LOG.warn("Failed to resync organization tree after Keycloak recovery: {}", ex.getMessage());
                    resyncPending.set(true);
                    provisioningRetryAfter.compareAndSet(0L, now + PROVISIONING_RETRY_BACKOFF.toMillis());
                }
            }
        }
    }

    private void suppressProvisioning(String action, OrganizationNode node, RuntimeException ex) {
        long now = System.currentTimeMillis();
        long retryUntil = now + PROVISIONING_RETRY_BACKOFF.toMillis();
        resyncPending.set(true);
        long previous = provisioningRetryAfter.getAndUpdate(existing -> existing <= now ? retryUntil : Math.max(existing, retryUntil));
        if (previous <= now) {
            LOG.error(
                "Temporarily disabling Keycloak group provisioning after {} failure for organization {} (id={}); will retry after {}s",
                action,
                node != null ? node.getName() : "unknown",
                node != null ? node.getId() : null,
                PROVISIONING_RETRY_BACKOFF.toSeconds(),
                ex
            );
        } else {
            LOG.warn(
                "Keycloak group provisioning still suppressed; latest {} failure for organization {} (id={}): {}",
                action,
                node != null ? node.getName() : "unknown",
                node != null ? node.getId() : null,
                ex.getMessage()
            );
            LOG.debug("Suppressed Keycloak provisioning failure details", ex);
        }
    }
}
