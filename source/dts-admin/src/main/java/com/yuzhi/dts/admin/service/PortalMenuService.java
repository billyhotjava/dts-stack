package com.yuzhi.dts.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.domain.PortalMenu;
import com.yuzhi.dts.admin.domain.PortalMenuVisibility;
import com.yuzhi.dts.admin.domain.SystemConfig;
import com.yuzhi.dts.admin.repository.PortalMenuRepository;
import com.yuzhi.dts.admin.repository.PortalMenuVisibilityRepository;
import com.yuzhi.dts.admin.repository.SystemConfigRepository;
import com.yuzhi.dts.admin.security.AuthoritiesConstants;
import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class PortalMenuService {

    private static final Logger log = LoggerFactory.getLogger(PortalMenuService.class);
    private static final String MENU_SEED_HASH_KEY = "portal.menu.seed.hash";

    // NOTE: security.threeAdmins 同理（已在管理端实现）。
    private static final Set<String> DISABLED_SECTIONS = Set.of("iam");
    private static final Set<String> LEGACY_SECTION_KEYS = Set.of(
        "workspace",
        "domain",
        "standard",
        "asset",
        "model",
        "job",
        "quality",
        "analytics",
        "operations",
        "catalog",
        "modeling",
        "governance",
        "explore",
        "visualization",
        "foundation",
        "security",
        "ops",
        "services",
        "bi",
        "metrics",
        "app-pack",
        "iam"
    );
    private static final Set<String> BASE_READ_SECTIONS = Set.of("workbench", "portal", "services", "visual-analytics");
    private static final Set<String> WRITE_SECTIONS = Set.of("studio", "governance");
    private static final Set<String> FOUNDATION_SECTIONS = Set.of("resource", "ops");
    private static final Set<String> IAM_SECTIONS = Set.of();
    private static final Map<String, String> MENU_COMPONENTS = Map.ofEntries(
        Map.entry("workbench.overview", "/pages/workbench"),
        Map.entry("workbench.todo", "/pages/workbench/WorkflowCenterPage"),
        // P1-7: workbench.favorites removed in Sprint-15 / F2 alongside the
        // portal_user_favorite table. Keeping the menu key alive surfaced an
        // orphan menu item even though the front-end page was deleted.
        Map.entry("workbench.data-management", "/pages/workbench/DataManagementWorkbenchPage"),
        Map.entry("resource.accessOverview", "/pages/foundation/access/AccessWorkspacePage"),
        Map.entry("resource.databaseAccess", "/pages/foundation/access/AccessWorkspacePage"),
        Map.entry("resource.apiAccess", "/pages/foundation/access/AccessWorkspacePage"),
        Map.entry("resource.fileAccess", "/pages/foundation/access/AccessWorkspacePage"),
        Map.entry("resource.accessDefaults", "/pages/foundation/access/AccessDefaultsPage"),
        Map.entry("resource.runtime.connectors", "/pages/foundation/ConnectorRegistryPage"),
        Map.entry("resource.runtime.jdbcDrivers", "/pages/foundation/JdbcDriversPage"),
        Map.entry("studio.projects", "/pages/modeling/ModelTemplatesPage"),
        Map.entry("studio.sql", "/pages/modeling/SqlModelingPage"),
        Map.entry("studio.scripts", "/pages/explore/etl/ScriptStudioPage"),
        Map.entry("studio.orchestration", "/pages/foundation/access/LegacyOrchestrationRedirect"),
        Map.entry("studio.adhoc", "/pages/explore/SqlIdePage"),
        Map.entry("studio.dbt-files", "/pages/modeling/ModelingCompatibilityPage"),
        Map.entry("governance.subjects", "/pages/governance/SubjectAreasPage"),
        Map.entry("governance.standards.glossary", "/pages/governance/GlossaryPage"),
        Map.entry("governance.standards.elements", "/pages/governance/ElementsPage"),
        Map.entry("governance.standards.reference", "/pages/governance/ReferenceCodesPage"),
        Map.entry("governance.templates", "/pages/governance/TemplatesPage"),
        Map.entry("governance.indicators", "/pages/governance/IndicatorsPage"),
        Map.entry("governance.qualityRules", "/pages/governance/QualityRulesPage"),
        Map.entry("governance.qualityReport", "/pages/catalog/QualityPage"),
        Map.entry("governance.classification", "/pages/security/data-security"),
        Map.entry("portal.map", "/pages/catalog/DatasetsPage"),
        Map.entry("portal.search", "/pages/catalog/DataSearchPage"),
        Map.entry("portal.detail", "/pages/catalog/AssetDetailPage"),
        Map.entry("portal.lineage", "/pages/catalog/LineagePage"),
        Map.entry("portal.permission", "/pages/security/DatasetAccessApprovalPage"),
        Map.entry("ops.overview", "/pages/ops/OpsOverviewPage"),
        Map.entry("ops.instances", "/pages/ops/OpsInstancesPage"),
        Map.entry("ops.alerts", "/pages/ops/OpsAlertLogPage"),
        Map.entry("ops.backfill", "/pages/ops/OpsBackfillPage"),
        Map.entry("services.api", "/pages/services/ApiServicesPage"),
        Map.entry("services.consumption", "/pages/workbench/DataManagementWorkbenchPage"),
        Map.entry("services.outbound", "/pages/services/DataProductsPage"),
        Map.entry("services.exchange", "/pages/services/TokensPage"),
        Map.entry("visual-analytics.reports", "/pages/visualization/ReportsPage")
    );

    private final PortalMenuRepository menuRepo;
    private final PortalMenuVisibilityRepository visibilityRepo;
    private final SystemConfigRepository systemConfigRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate menuMutationTx;

    private volatile MenuSeed cachedSeed;
    private volatile String cachedSeedHash;
    private final Map<String, String> titleKeyCache = new ConcurrentHashMap<>();

    public PortalMenuService(
        PortalMenuRepository menuRepo,
        PortalMenuVisibilityRepository visibilityRepo,
        SystemConfigRepository systemConfigRepository,
        ObjectMapper objectMapper,
        PlatformTransactionManager transactionManager
    ) {
        this.menuRepo = menuRepo;
        this.visibilityRepo = visibilityRepo;
        this.systemConfigRepository = systemConfigRepository;
        this.objectMapper = objectMapper;
        this.menuMutationTx = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
        this.menuMutationTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.menuMutationTx.setReadOnly(false);
    }

    public List<PortalMenu> findTree() {
        ensureSeedMenus();
        return runSafely(
            () -> {
                List<PortalMenu> roots = menuRepo.findByDeletedFalseAndParentIsNullOrderBySortOrderAscIdAsc();
                roots.forEach(this::touch);
                return roots
                    .stream()
                    .filter(menu -> !isDisabledMenu(menu))
                    .collect(Collectors.toCollection(ArrayList::new));
            },
            java.util.Collections.<PortalMenu>emptyList(),
            "menu tree"
        );
    }

    public List<PortalMenu> findTreeForAudience(Set<String> roleCodes, Set<String> permissionCodes, String maxDataLevel) {
        ensureSeedMenus();
        List<PortalMenu> roots = findTree();
        return roots
            .stream()
            .map(menu -> filterMenu(menu, roleCodes, permissionCodes, maxDataLevel))
            .filter(Objects::nonNull)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<PortalMenu> findDeletedMenus() {
        return runSafely(
            () -> {
                List<PortalMenu> deleted = menuRepo.findByDeletedTrueOrderBySortOrderAscIdAsc();
                deleted.forEach(this::touch);
                return deleted
                    .stream()
                    .filter(menu -> !isDisabledMenu(menu))
                    .collect(Collectors.toCollection(ArrayList::new));
            },
            java.util.Collections.<PortalMenu>emptyList(),
            "deleted menu list"
        );
    }

    @Transactional(readOnly = true)
    public Optional<String> resolveTitleByKey(String titleKey) {
        if (!StringUtils.hasText(titleKey)) {
            return Optional.empty();
        }
        String normalized = titleKey.trim();
        String resolved = titleKeyCache.computeIfAbsent(normalized, this::lookupTitleByKey);
        if (StringUtils.hasText(resolved)) {
            return Optional.of(resolved);
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<String> resolveDisplayName(PortalMenu menu) {
        if (menu == null) {
            return Optional.empty();
        }
        Optional<String> fromMetadata = resolveTitleFromMetadata(menu.getMetadata());
        if (fromMetadata.isPresent()) {
            return fromMetadata;
        }
        String titleKey = extractTitleKey(menu);
        if (StringUtils.hasText(titleKey)) {
            Optional<String> resolved = resolveTitleByKey(titleKey);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        String normalizedName = normalizeCodeSynonyms(menu.getName());
        if (StringUtils.hasText(normalizedName)) {
            Optional<String> resolved = resolveTitleByKey(normalizedName);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        if (StringUtils.hasText(menu.getName())) {
            Optional<String> resolved = resolveTitleByKey(menu.getName());
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        Optional<String> fromPath = resolveTitleByPath(menu.getPath());
        if (fromPath.isPresent()) {
            return fromPath;
        }
        return Optional.empty();
    }

    public List<PortalMenu> findAllMenusOrdered() {
        ensureSeedMenus();
        return runSafely(
            () -> {
                List<PortalMenu> all = menuRepo.findAllByOrderBySortOrderAscIdAsc();
                all.forEach(this::touch);
                return all
                    .stream()
                    .filter(menu -> !isDisabledMenu(menu))
                    .collect(Collectors.toCollection(ArrayList::new));
            },
            java.util.Collections.<PortalMenu>emptyList(),
            "full menu list"
        );
    }

    private <T> T runSafely(java.util.concurrent.Callable<? extends T> action, T fallback, String label) {
        try {
            return action.call();
        } catch (DataAccessException ex) {
            log.warn("Skip {} due to schema issue: {}", label, ex.getMostSpecificCause().getMessage());
        } catch (Exception ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof DataAccessException dae) {
                log.warn("Skip {} due to schema issue: {}", label, dae.getMostSpecificCause().getMessage());
            } else {
                log.warn("Skip {} due to unexpected error: {}", label, ex.getMessage());
                log.debug("Failed {} stack", label, ex);
            }
        }
        return fallback;
    }

    private void touch(PortalMenu menu) {
        if (menu.getVisibilities() != null) {
            menu.getVisibilities().size();
        }
        if (menu.getChildren() != null) {
            menu.getChildren().forEach(this::touch);
        }
    }

    private PortalMenu filterMenu(PortalMenu menu, Set<String> roleCodes, Set<String> permissionCodes, String maxDataLevel) {
        if (isDisabledMenu(menu)) {
            return null;
        }
        List<PortalMenu> filteredChildren = menu
            .getChildren()
            .stream()
            .map(child -> filterMenu(child, roleCodes, permissionCodes, maxDataLevel))
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(ArrayList::new));

        boolean visible = isMenuVisible(menu, roleCodes, permissionCodes, maxDataLevel);
        if (!visible && filteredChildren.isEmpty()) {
            return null;
        }

        PortalMenu clone = cloneMenu(menu);
        if (!filteredChildren.isEmpty()) {
            for (PortalMenu child : filteredChildren) {
                child.setParent(clone);
            }
            clone.setChildren(filteredChildren);
        } else {
            clone.setChildren(new ArrayList<>());
        }
        return clone;
    }

    private boolean isMenuVisible(PortalMenu menu, Set<String> roleCodes, Set<String> permissionCodes, String maxDataLevel) {
        // 强约束：基础数据功能（foundation）仅对 OP_ADMIN 开放
        try {
            String section = extractSectionKey(menu);
            if ("foundation".equalsIgnoreCase(section)) {
                if (roleCodes == null || !roleCodes.contains(AuthoritiesConstants.OP_ADMIN)) {
                    return false;
                }
            }
        } catch (Exception ignore) {}
        List<PortalMenuVisibility> visibilities = menu.getVisibilities();
        if (visibilities == null || visibilities.isEmpty()) {
            return false;
        }

        for (PortalMenuVisibility visibility : visibilities) {
            if (!matchesRole(visibility, roleCodes)) {
                continue;
            }
            if (!matchesPermission(visibility, permissionCodes)) {
                continue;
            }
            if (!matchesDataLevel(visibility, maxDataLevel)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private boolean matchesRole(PortalMenuVisibility visibility, Set<String> roleCodes) {
        String rawRequiredRole = visibility.getRoleCode();
        if (!StringUtils.hasText(rawRequiredRole)) {
            return true;
        }
        if (CollectionUtils.isEmpty(roleCodes)) {
            return false;
        }

        String normalizedRequiredRole = normalizeRoleCode(rawRequiredRole);
        if (!StringUtils.hasText(normalizedRequiredRole)) {
            return false;
        }
        if (AuthoritiesConstants.USER.equalsIgnoreCase(normalizedRequiredRole)) {
            return false;
        }

        // Build a normalized audience set (ROLE_* upper case) for comparison
        LinkedHashSet<String> normalizedAudience = new LinkedHashSet<>();
        for (String role : roleCodes) {
            if (!StringUtils.hasText(role)) {
                continue;
            }
            String normalized = normalizeRoleCode(role);
            if (StringUtils.hasText(normalized)) {
                normalizedAudience.add(normalized);
            }
        }

        if (normalizedAudience.contains(normalizedRequiredRole)) {
            return true;
        }
        if (normalizedAudience.contains(rawRequiredRole.trim().toUpperCase(Locale.ROOT))) {
            return true;
        }

        String canonicalRequired = stripRolePrefix(normalizedRequiredRole);
        if (StringUtils.hasText(canonicalRequired)) {
            if (roleCodes.contains(canonicalRequired)) {
                return true;
            }
            for (String audience : normalizedAudience) {
                if (canonicalRequired.equalsIgnoreCase(stripRolePrefix(audience))) {
                    return true;
                }
            }
        }

        // Governance triad are also allowed to bypass explicit constraints
        if (
            roleCodes.contains(AuthoritiesConstants.SYS_ADMIN) ||
            roleCodes.contains(AuthoritiesConstants.AUTH_ADMIN) ||
            roleCodes.contains(AuthoritiesConstants.AUDITOR_ADMIN)
        ) {
            return true;
        }
        return false;
    }

    private boolean matchesPermission(PortalMenuVisibility visibility, Set<String> permissionCodes) {
        if (!StringUtils.hasText(visibility.getPermissionCode())) {
            return true;
        }
        if (CollectionUtils.isEmpty(permissionCodes)) {
            return false;
        }
        return permissionCodes.contains(visibility.getPermissionCode());
    }

    private boolean matchesDataLevel(PortalMenuVisibility visibility, String maxDataLevel) {
        if (
            !StringUtils.hasText(visibility.getDataLevel()) ||
            visibility.getDataLevel().equalsIgnoreCase(SecurityLevelCatalog.DEFAULT_DATA_SECURITY_LEVEL.code())
        ) {
            return true;
        }
        if (!StringUtils.hasText(maxDataLevel)) {
            return false;
        }
        return dataLevelPriority(maxDataLevel) >= dataLevelPriority(visibility.getDataLevel());
    }

    private int dataLevelPriority(String level) {
        String normalized = SecurityLevelCatalog.normalizeMaxDataCode(level);
        return normalized == null ? -1 : SecurityLevelCatalog.dataRank(normalized);
    }

    private PortalMenu cloneMenu(PortalMenu source) {
        PortalMenu copy = new PortalMenu();
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setPath(source.getPath());
        copy.setComponent(source.getComponent());
        copy.setIcon(source.getIcon());
        copy.setSortOrder(source.getSortOrder());
        copy.setMetadata(source.getMetadata());
        copy.setSecurityLevel(source.getSecurityLevel());
        copy.setDeleted(source.isDeleted());

        if (source.getVisibilities() != null) {
            List<PortalMenuVisibility> clonedVis = new ArrayList<>();
            for (PortalMenuVisibility visibility : source.getVisibilities()) {
                PortalMenuVisibility copyVis = new PortalMenuVisibility();
                copyVis.setId(visibility.getId());
                copyVis.setRoleCode(visibility.getRoleCode());
                copyVis.setPermissionCode(visibility.getPermissionCode());
                copyVis.setDataLevel(visibility.getDataLevel());
                copyVis.setMenu(copy);
                clonedVis.add(copyVis);
            }
            copy.setVisibilities(clonedVis);
        }
        return copy;
    }

    public void replaceVisibilities(PortalMenu menu, List<PortalMenuVisibility> visibilities) {
        if (menu == null || menu.getId() == null) {
            throw new IllegalArgumentException("menu must be persisted before updating visibilities");
        }
        PortalMenu managed = menuRepo
            .findById(menu.getId())
            .orElseThrow(() -> new IllegalArgumentException("No portal menu found for id " + menu.getId()));

        managed.getVisibilities().clear();

        List<PortalMenuVisibility> effective;
        if (CollectionUtils.isEmpty(visibilities)) {
            effective = defaultVisibilities(managed);
        } else {
            effective = visibilities
                .stream()
                .map(v -> copyVisibility(managed, v))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
            if (effective.isEmpty()) {
                effective = defaultVisibilities(managed);
            }
        }

        for (PortalMenuVisibility visibility : effective) {
            managed.addVisibility(visibility);
        }
        menuRepo.flush();
    }

    public void resetMenusToSeed() {
        MenuSeed seed = menuSeed();
        String seedHash = currentSeedHash();
        // Phase 1: purge and rebuild menu tree in its own TX
        menuMutationTx.execute(status -> {
            performMenuReset(seed);
            return null;
        });
        // Phase 2: apply default role bindings in a separate TX so any
        // transient schema/data issues don't mark phase 1 TX rollback-only
        try {
            menuMutationTx.execute(status -> {
                applyDefaultRoleBindings();
                return null;
            });
        } catch (Exception ex) {
            log.warn("Failed applying default role bindings: {}", ex.getMessage());
            log.debug("Default role bindings error stack", ex);
        }
        persistSeedHash(seedHash);
    }

    public void clearSeedMenuRoleBindings() {
        Set<String> seedSectionKeys = seedSectionKeys(menuSeed());
        if (seedSectionKeys.isEmpty()) {
            return;
        }
        List<PortalMenu> allMenus = menuRepo.findAll();
        if (allMenus == null || allMenus.isEmpty()) {
            return;
        }

        boolean dirty = false;
        for (PortalMenu menu : allMenus) {
            if (!isSeedManagedMenu(menu, seedSectionKeys)) {
                continue;
            }
            List<PortalMenuVisibility> existing = menu.getVisibilities() == null
                ? List.of()
                : new ArrayList<>(menu.getVisibilities());
            for (PortalMenuVisibility visibility : existing) {
                removeVisibility(visibility);
                dirty = true;
            }
        }
        if (dirty) {
            menuRepo.flush();
        }
    }

    private Set<String> seedSectionKeys(MenuSeed seed) {
        if (seed == null || seed.portalNavSections() == null || seed.portalNavSections().isEmpty()) {
            return Set.of();
        }
        return seed
            .portalNavSections()
            .stream()
            .map(MenuNode::key)
            .filter(StringUtils::hasText)
            .map(key -> key.trim().toLowerCase(Locale.ROOT))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean isSeedManagedMenu(PortalMenu menu, Set<String> seedSectionKeys) {
        if (menu == null || seedSectionKeys == null || seedSectionKeys.isEmpty()) {
            return false;
        }
        String sectionKey = extractSectionKey(menu);
        if (StringUtils.hasText(sectionKey) && seedSectionKeys.contains(sectionKey.trim().toLowerCase(Locale.ROOT))) {
            return true;
        }
        String metadataKey = extractMetadataKey(menu);
        return (
            menu.getParent() == null &&
            StringUtils.hasText(metadataKey) &&
            seedSectionKeys.contains(metadataKey.trim().toLowerCase(Locale.ROOT))
        );
    }

    private void performMenuReset(MenuSeed seed) {
        visibilityRepo.deleteAllInBatch();
        menuRepo.deleteAllInBatch();

        if (seed.portalNavSections() == null || seed.portalNavSections().isEmpty()) {
            log.warn("Menu seed is empty; no menus created");
            return;
        }

        int sortOrder = 1;
        for (MenuNode section : seed.portalNavSections()) {
            String sectionComposite = StringUtils.hasText(section.key()) ? section.key() : "section-" + sortOrder;
            PortalMenu root = buildMenuTree(section, null, sortOrder++, sectionComposite, sectionComposite);
            menuRepo.save(root);
        }
        // Default bindings are applied outside this TX in resetMenusToSeed()
    }

    private PortalMenu buildMenuTree(MenuNode node, PortalMenu parent, int sortOrder, String compositeKey, String sectionKey) {
        PortalMenu menu = new PortalMenu();
        menu.setName(resolveName(node));
        menu.setPath(buildPath(parent, node.path()));
        menu.setIcon(node.icon());
        menu.setSortOrder(sortOrder);
        menu.setMetadata(writeMetadata(node, parent == null, sectionKey));
        menu.setSecurityLevel(SecurityLevelCatalog.DEFAULT_PERSONNEL_SECURITY_LEVEL.code());
        menu.setDeleted(false);
        if (parent != null) {
            menu.setParent(parent);
        }

        List<PortalMenu> children = new ArrayList<>();
        if (node.children() != null) {
            int childOrder = 1;
            for (MenuNode child : node.children()) {
                String childKey = child.key() == null ? compositeKey : compositeKey + "." + child.key();
                PortalMenu childMenu = buildMenuTree(child, menu, childOrder++, childKey, sectionKey);
                childMenu.setParent(menu);
                children.add(childMenu);
            }
        }
        menu.setChildren(children);
        if (children.isEmpty()) {
            menu.setComponent(resolveComponent(compositeKey));
        } else {
            menu.setComponent(null);
        }
        for (PortalMenuVisibility visibility : defaultVisibilities(menu)) {
            menu.addVisibility(visibility);
        }
        return menu;
    }

    private String writeMetadata(MenuNode node, boolean isRoot, String sectionKey) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (StringUtils.hasText(node.key())) {
            metadata.put("key", node.key());
        }
        if (isRoot) {
            if (StringUtils.hasText(node.key())) {
                metadata.put("sectionKey", node.key());
            }
        } else {
            if (StringUtils.hasText(sectionKey)) {
                metadata.put("sectionKey", sectionKey);
            }
            if (StringUtils.hasText(node.key())) {
                metadata.put("entryKey", node.key());
            }
        }
        if (StringUtils.hasText(node.titleKey())) {
            metadata.put("titleKey", node.titleKey());
        }
        if (StringUtils.hasText(node.title())) {
            metadata.put("title", node.title());
        }
        if (StringUtils.hasText(node.icon())) {
            metadata.put("icon", node.icon());
        }
        if (StringUtils.hasText(node.externalLink())) {
            metadata.put("externalLink", node.externalLink());
        }
        if (metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception ex) {
            log.warn("Failed to serialize menu metadata for key {}: {}", node.key(), ex.getMessage());
            return null;
        }
    }

    private String resolveName(MenuNode node) {
        if (StringUtils.hasText(node.titleKey())) {
            return node.titleKey();
        }
        if (StringUtils.hasText(node.title())) {
            return node.title();
        }
        return StringUtils.hasText(node.key()) ? node.key() : "菜单";
    }

    private String buildPath(PortalMenu parent, String segment) {
        String normalized = segment == null ? "" : segment.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        String combined = combineMenuPath(parent == null ? null : parent.getPath(), normalized);
        if (!StringUtils.hasText(combined)) {
            return normalized;
        }
        String trimmed = combined.startsWith("/") ? combined.substring(1) : combined;
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String resolveComponent(String compositeKey) {
        if (!StringUtils.hasText(compositeKey)) {
            return null;
        }
        return MENU_COMPONENTS.get(compositeKey);
    }

    private MenuSeed loadMenuSeed() {
        ClassPathResource resource = new ClassPathResource("config/data/portal-menu-seed.json");
        if (!resource.exists()) {
            throw new IllegalStateException("Portal menu seed resource not found");
        }
        try (InputStream is = resource.getInputStream()) {
            return objectMapper.readValue(is, MenuSeed.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read portal menu seed", ex);
        }
    }

    private MenuSeed menuSeed() {
        MenuSeed seed = cachedSeed;
        if (seed == null) {
            synchronized (this) {
                seed = cachedSeed;
                if (seed == null) {
                    seed = loadMenuSeed();
                    cachedSeed = seed;
                }
            }
        }
        return seed;
    }

    private String currentSeedHash() {
        String hash = cachedSeedHash;
        if (hash != null) {
            return hash;
        }
        synchronized (this) {
            hash = cachedSeedHash;
            if (hash != null) {
                return hash;
            }
            try {
                ClassPathResource resource = new ClassPathResource("config/data/portal-menu-seed.json");
                if (!resource.exists()) {
                    return null;
                }
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream is = resource.getInputStream()) {
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = is.read(buffer)) > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
                hash = toHex(digest.digest());
                cachedSeedHash = hash;
                return hash;
            } catch (Exception ex) {
                log.warn("Failed to compute portal menu seed hash: {}", ex.getMessage());
                return null;
            }
        }
    }

    private String storedSeedHash() {
        return systemConfigRepository
            .findByKey(MENU_SEED_HASH_KEY)
            .map(SystemConfig::getValue)
            .filter(StringUtils::hasText)
            .orElse(null);
    }

    private void persistSeedHash(String hash) {
        if (!StringUtils.hasText(hash)) {
            return;
        }
        SystemConfig config = systemConfigRepository.findByKey(MENU_SEED_HASH_KEY).orElseGet(SystemConfig::new);
        config.setKey(MENU_SEED_HASH_KEY);
        config.setValue(hash);
        if (!StringUtils.hasText(config.getDescription())) {
            config.setDescription("Portal menu seed hash");
        }
        systemConfigRepository.save(config);
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void ensureSeedMenus() {
        try {
            MenuSeed seed = menuSeed();
            String seedHash = currentSeedHash();
            String storedHash = storedSeedHash();
            List<PortalMenu> roots = menuRepo.findByDeletedFalseAndParentIsNullOrderBySortOrderAscIdAsc();

            if (seedHash != null && storedHash != null && !seedHash.equals(storedHash)) {
                log.info("Portal menu seed changed; applying non-destructive seed upsert");
                applySeedMaintenance(seed);
                persistSeedHash(seedHash);
                return;
            }
            if (storedHash == null && roots != null && !roots.isEmpty()) {
                log.info("Portal menu seed baseline missing; resetting menus to new seed");
                resetMenusToSeed();
                persistSeedHash(seedHash);
                return;
            }
            if (roots == null || roots.isEmpty()) {
                // Fresh install: create full seed (destructive reset is acceptable when no menus exist).
                resetMenusToSeed();
                persistSeedHash(seedHash);
                return;
            }

            applySeedMaintenance(seed);

            if (storedHash == null) {
                persistSeedHash(seedHash);
            }
        } catch (Exception ex) {
            log.warn("Skip portal menu seed verification due to: {}", ex.getMessage());
        }
    }

    private void applySeedMaintenance(MenuSeed seed) {
        // Non-destructive: ensure missing seed nodes exist; do not wipe customized menus on upgrades.
        try {
            menuMutationTx.execute(status -> {
                upsertMenusFromSeed(seed);
                return null;
            });
        } catch (Exception ex) {
            log.warn("Failed ensuring portal menus from seed: {}", ex.getMessage());
            log.debug("Portal menu seed upsert error stack", ex);
        }

        // Ensure default role bindings exist at least once.
        try {
            menuMutationTx.execute(status -> {
                applyDefaultRoleBindings();
                return null;
            });
        } catch (Exception ignored) {}

        // Soft-delete legacy root menus that are no longer part of the seed.
        try {
            menuMutationTx.execute(status -> {
                cleanupLegacyRootMenus(seed);
                return null;
            });
        } catch (Exception ex) {
            log.warn("Failed cleaning legacy portal menus: {}", ex.getMessage());
            log.debug("Legacy portal menu cleanup error stack", ex);
        }
    }

    private void cleanupLegacyRootMenus(MenuSeed seed) {
        if (seed == null || seed.portalNavSections() == null) {
            return;
        }
        Set<String> seedKeys = seed
            .portalNavSections()
            .stream()
            .map(MenuNode::key)
            .filter(StringUtils::hasText)
            .map(key -> key.trim().toLowerCase(Locale.ROOT))
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (seedKeys.isEmpty()) {
            return;
        }
        List<PortalMenu> roots = menuRepo.findByDeletedFalseAndParentIsNullOrderBySortOrderAscIdAsc();
        if (roots == null || roots.isEmpty()) {
            return;
        }
        for (PortalMenu root : roots) {
            String rootKey = resolveRootKey(root);
            if (!StringUtils.hasText(rootKey)) {
                continue;
            }
            String normalized = rootKey.trim().toLowerCase(Locale.ROOT);
            if (seedKeys.contains(normalized)) {
                continue;
            }
            if (LEGACY_SECTION_KEYS.contains(normalized)) {
                markDeletedSubtree(root);
            }
        }
    }

    private void markDeletedSubtree(PortalMenu menu) {
        if (menu == null) {
            return;
        }
        if (!menu.isDeleted()) {
            menu.setDeleted(true);
            menuRepo.save(menu);
        }
        if (menu.getChildren() == null || menu.getChildren().isEmpty()) {
            return;
        }
        for (PortalMenu child : menu.getChildren()) {
            markDeletedSubtree(child);
        }
    }

    private String resolveRootKey(PortalMenu menu) {
        String key = extractMetadataKey(menu);
        if (StringUtils.hasText(key)) {
            return key;
        }
        key = extractSectionKey(menu);
        if (StringUtils.hasText(key)) {
            return key;
        }
        String name = menu != null ? menu.getName() : null;
        if (StringUtils.hasText(name)) {
            String normalized = name.trim();
            if (normalized.startsWith("sys.nav.portal.")) {
                normalized = normalized.substring("sys.nav.portal.".length());
            }
            return normalized;
        }
        String path = menu != null ? menu.getPath() : null;
        if (StringUtils.hasText(path)) {
            return path.trim().replaceAll("^/+", "").replaceAll("/+$", "");
        }
        return null;
    }

    private void upsertMenusFromSeed(MenuSeed seed) {
        if (seed == null || seed.portalNavSections() == null || seed.portalNavSections().isEmpty()) {
            return;
        }
        List<PortalMenu> roots = menuRepo.findByDeletedFalseAndParentIsNullOrderBySortOrderAscIdAsc();
        int sortOrder = 1;
        for (MenuNode section : seed.portalNavSections()) {
            if (section == null) continue;
            String sectionKey = StringUtils.hasText(section.key()) ? section.key().trim() : "section-" + sortOrder;
            if (isDisabledSectionKey(sectionKey)) {
                sortOrder++;
                continue;
            }
            PortalMenu existingRoot = findChildByMetadataKey(null, roots, sectionKey);
            if (existingRoot == null) {
                String sectionComposite = StringUtils.hasText(section.key()) ? section.key() : sectionKey;
                PortalMenu root = buildMenuTree(section, null, sortOrder, sectionComposite, sectionComposite);
                menuRepo.save(root);
            } else {
                // Ensure seed subtree exists under this root
                ensureChildrenFromSeed(existingRoot, section.children(), 1, sectionKey, sectionKey);
            }
            sortOrder++;
        }
        menuRepo.flush();
    }

    private void ensureChildrenFromSeed(
        PortalMenu parent,
        List<MenuNode> seedChildren,
        int startOrder,
        String compositeKey,
        String sectionKey
    ) {
        if (parent == null || parent.getId() == null || seedChildren == null || seedChildren.isEmpty()) {
            return;
        }
        List<PortalMenu> existingChildren = menuRepo.findByParentIdOrderBySortOrderAscIdAsc(parent.getId());
        int childOrder = startOrder;
        for (MenuNode child : seedChildren) {
            if (child == null) continue;
            String childKey = StringUtils.hasText(child.key()) ? child.key().trim() : "entry-" + childOrder;
            String nextCompositeKey = compositeKey + "." + childKey;
            PortalMenu existing = findChildByMetadataKey(parent.getId(), existingChildren, childKey);
            if (existing == null) {
                PortalMenu created = buildMenuTree(child, parent, childOrder, nextCompositeKey, sectionKey);
                created.setParent(parent);
                inheritVisibilitiesFromParentSubtree(created, parent, existingChildren);
                menuRepo.save(created);
            } else {
                boolean dirty = false;
                // Sync metadata from seed so removed fields (e.g. externalLink) are cleaned up.
                String freshMetadata = writeMetadata(child, false, sectionKey);
                if (freshMetadata != null && !freshMetadata.equals(existing.getMetadata())) {
                    existing.setMetadata(freshMetadata);
                    dirty = true;
                }
                if (inheritVisibilityIfUnbound(existing, parent, existingChildren)) {
                    dirty = true;
                }
                // Recurse to ensure deeper nodes exist.
                ensureChildrenFromSeed(existing, child.children(), 1, nextCompositeKey, sectionKey);
                // Keep seed-managed leaf components aligned with the current route mapping.
                if (child.children() == null || child.children().isEmpty()) {
                    String component = resolveComponent(nextCompositeKey);
                    if (StringUtils.hasText(component) && !component.equals(existing.getComponent())) {
                        existing.setComponent(component);
                        dirty = true;
                    }
                }
                if (dirty) {
                    menuRepo.save(existing);
                }
            }
            childOrder++;
        }
    }

    private void inheritVisibilitiesFromParentSubtree(PortalMenu menu, PortalMenu parent, List<PortalMenu> siblingCandidates) {
        if (menu == null) {
            return;
        }
        inheritVisibilityIfUnbound(menu, parent, siblingCandidates);
        if (menu.getChildren() == null || menu.getChildren().isEmpty()) {
            return;
        }
        for (PortalMenu child : menu.getChildren()) {
            inheritVisibilitiesFromParentSubtree(child, menu, menu.getChildren());
        }
    }

    private boolean inheritVisibilityIfUnbound(PortalMenu menu, PortalMenu parent, List<PortalMenu> siblingCandidates) {
        if (menu == null || parent == null) {
            return false;
        }
        if (isExplicitlyUnboundAccessDefaults(menu)) {
            return false;
        }
        if (menu.getVisibilities() != null && !menu.getVisibilities().isEmpty()) {
            return false;
        }
        List<PortalMenuVisibility> inheritedSources = inheritedVisibilitySources(menu, parent, siblingCandidates);
        if (inheritedSources.isEmpty()) {
            return false;
        }
        boolean dirty = false;
        for (PortalMenuVisibility visibility : inheritedSources) {
            PortalMenuVisibility inherited = copyVisibility(menu, visibility);
            if (inherited != null) {
                menu.addVisibility(inherited);
                dirty = true;
            }
        }
        return dirty;
    }

    private boolean isExplicitlyUnboundAccessDefaults(PortalMenu menu) {
        if (menu == null) {
            return false;
        }
        String sectionKey = extractSectionKey(menu);
        String metadataKey = extractMetadataKey(menu);
        if ("resource".equalsIgnoreCase(sectionKey) && "accessDefaults".equalsIgnoreCase(metadataKey)) {
            return true;
        }
        return "sys.nav.portal.resourceAccessDefaults".equals(menu.getName());
    }

    private List<PortalMenuVisibility> inheritedVisibilitySources(
        PortalMenu target,
        PortalMenu parent,
        List<PortalMenu> siblingCandidates
    ) {
        LinkedHashMap<String, PortalMenuVisibility> sources = new LinkedHashMap<>();
        addVisibilitySources(sources, parent == null ? null : parent.getVisibilities());
        if (!sources.isEmpty()) {
            return new ArrayList<>(sources.values());
        }
        if (siblingCandidates == null || siblingCandidates.isEmpty()) {
            return List.of();
        }
        for (PortalMenu sibling : siblingCandidates) {
            if (!isSeedSiblingVisibilitySource(target, sibling)) {
                continue;
            }
            addVisibilitySources(sources, sibling.getVisibilities());
        }
        return new ArrayList<>(sources.values());
    }

    private void addVisibilitySources(LinkedHashMap<String, PortalMenuVisibility> sources, List<PortalMenuVisibility> visibilities) {
        if (visibilities == null || visibilities.isEmpty()) {
            return;
        }
        for (PortalMenuVisibility visibility : visibilities) {
            if (visibility == null) {
                continue;
            }
            sources.putIfAbsent(visibilityKey(visibility), visibility);
        }
    }

    private String visibilityKey(PortalMenuVisibility visibility) {
        return (
            Objects.toString(visibility.getRoleCode(), "") +
            "|" +
            Objects.toString(visibility.getPermissionCode(), "") +
            "|" +
            Objects.toString(visibility.getDataLevel(), "")
        );
    }

    private boolean sameMenu(PortalMenu left, PortalMenu right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left.getId() != null && right.getId() != null) {
            return Objects.equals(left.getId(), right.getId());
        }
        String leftKey = extractMetadataKey(left);
        String rightKey = extractMetadataKey(right);
        if (StringUtils.hasText(leftKey) && StringUtils.hasText(rightKey)) {
            return leftKey.trim().equalsIgnoreCase(rightKey.trim());
        }
        return StringUtils.hasText(left.getPath()) && left.getPath().equalsIgnoreCase(right.getPath());
    }

    private boolean isSeedSiblingVisibilitySource(PortalMenu target, PortalMenu sibling) {
        if (sibling == null || sameMenu(target, sibling) || sibling.isDeleted()) {
            return false;
        }
        String name = sibling.getName();
        if (StringUtils.hasText(name) && name.trim().toLowerCase(Locale.ROOT).startsWith("custom.")) {
            return false;
        }
        return StringUtils.hasText(extractEntryKey(sibling)) || StringUtils.hasText(extractMetadataKey(sibling));
    }

    private PortalMenu findChildByMetadataKey(Long parentId, List<PortalMenu> candidates, String expectedKey) {
        if (candidates == null || candidates.isEmpty() || !StringUtils.hasText(expectedKey)) {
            return null;
        }
        String normalizedExpected = expectedKey.trim().toLowerCase(Locale.ROOT);
        for (PortalMenu menu : candidates) {
            if (menu == null) continue;
            if (parentId != null && menu.getParent() != null && menu.getParent().getId() != null) {
                if (!parentId.equals(menu.getParent().getId())) {
                    continue;
                }
            }
            String actualKey = extractMetadataKey(menu);
            if (!StringUtils.hasText(actualKey)) {
                // Backward-compat: older rows might only contain sectionKey/entryKey in metadata.
                actualKey = parentId == null ? extractSectionKey(menu) : extractEntryKey(menu);
            }
            if (StringUtils.hasText(actualKey) && actualKey.trim().toLowerCase(Locale.ROOT).equals(normalizedExpected)) {
                return menu;
            }
        }
        return null;
    }

    /**
     * Apply default menu visibilities from config/data/role-menu-defaults.json.
     * Idempotent: only adds missing visibilities.
     */
    private void applyDefaultRoleBindings() {
        List<Map<String, Object>> rules = loadDefaultRoleBindings();
        if (rules == null || rules.isEmpty()) return;
        // Build indices for quick lookup
        List<PortalMenu> allMenus = menuRepo.findAll();
        Map<Long, PortalMenu> byId = allMenus.stream().filter(m -> m.getId() != null).collect(Collectors.toMap(PortalMenu::getId, m -> m));
        Map<String, List<PortalMenu>> byTitleKey = new LinkedHashMap<>();
        Map<String, List<PortalMenu>> byPath = new LinkedHashMap<>();
        for (PortalMenu m : allMenus) {
            // index by metadata.titleKey
            String titleKey = extractTitleKey(m);
            if (StringUtils.hasText(titleKey)) {
                byTitleKey.computeIfAbsent(titleKey, k -> new ArrayList<>()).add(m);
            }
            // index by path
            if (StringUtils.hasText(m.getPath())) {
                byPath.computeIfAbsent(m.getPath(), k -> new ArrayList<>()).add(m);
            }
        }

        for (Map<String, Object> rule : rules) {
            String code = objToString(rule.get("code"));
            String route = objToString(rule.get("route"));
            List<String> requiredRoles = listOfString(rule.get("requiredRoles"));
            if ((requiredRoles == null || requiredRoles.isEmpty())) continue;
            String normalizedCode = normalizeCodeSynonyms(code);
            // Find target menus
            List<PortalMenu> targets = new ArrayList<>();
            if (StringUtils.hasText(normalizedCode) && byTitleKey.containsKey(normalizedCode)) {
                targets.addAll(byTitleKey.get(normalizedCode));
            }
            if (targets.isEmpty() && StringUtils.hasText(route)) {
                // exact path match or startsWith for subtree
                for (Map.Entry<String, List<PortalMenu>> e : byPath.entrySet()) {
                    String p = e.getKey();
                    if (p.equalsIgnoreCase(route) || p.startsWith(route.endsWith("/") ? route : route + "/")) {
                        targets.addAll(e.getValue());
                    }
                }
            }
            if (targets.isEmpty()) {
                log.debug("No portal menu matched for code={} route={} when applying defaults", code, route);
                continue;
            }
            // Expand to subtree for section-level items (root of section: metadata has sectionKey==key and entryKey null)
            Set<Long> menuIds = new LinkedHashSet<>();
            for (PortalMenu m : targets) {
                collectSubtree(menuIds, m);
                collectAncestors(menuIds, m);
            }
            if (menuIds.isEmpty()) continue;
            // Apply requiredRoles as basic visibility (dataLevel INTERNAL)
            for (Long id : menuIds) {
                PortalMenu m = byId.get(id);
                if (m == null) continue;
                List<PortalMenuVisibility> existing = m.getVisibilities() == null ? new ArrayList<>() : new ArrayList<>(m.getVisibilities());
                Set<String> existingRoles = existing.stream().map(PortalMenuVisibility::getRoleCode).filter(Objects::nonNull).collect(Collectors.toSet());
                boolean dirty = false;
                for (String r : requiredRoles) {
                    String roleCode = normalizeRoleCode(r);
                    if (!existingRoles.contains(roleCode)) {
                        PortalMenuVisibility v = new PortalMenuVisibility();
                        v.setMenu(m);
                        v.setRoleCode(roleCode);
                        v.setDataLevel(SecurityLevelCatalog.DEFAULT_DATA_SECURITY_LEVEL.code());
                        existing.add(v);
                        dirty = true;
                    }
                }
                if (dirty) {
                    replaceVisibilities(m, existing);
                }
            }
        }
    }

    private void collectSubtree(Set<Long> ids, PortalMenu menu) {
        if (menu == null || menu.getId() == null) return;
        if (ids.add(menu.getId()) && menu.getChildren() != null) {
            for (PortalMenu c : menu.getChildren()) {
                collectSubtree(ids, c);
            }
        }
    }

    private void collectAncestors(Set<Long> ids, PortalMenu menu) {
        PortalMenu parent = menu == null ? null : menu.getParent();
        while (parent != null && parent.getId() != null) {
            ids.add(parent.getId());
            parent = parent.getParent();
        }
    }

    private String lookupTitleByKey(String titleKey) {
        if (!StringUtils.hasText(titleKey)) {
            return null;
        }
        MenuSeed seed = menuSeed();
        if (seed != null && seed.portalNavSections() != null) {
            String fromSeed = findTitleInNodes(seed.portalNavSections(), titleKey);
            if (StringUtils.hasText(fromSeed)) {
                return fromSeed;
            }
        }
        try {
            List<PortalMenu> menus = menuRepo.findAllByOrderBySortOrderAscIdAsc();
            for (PortalMenu menu : menus) {
                String metadata = menu.getMetadata();
                if (!StringUtils.hasText(metadata)) {
                    continue;
                }
                try {
                    JsonNode node = objectMapper.readTree(metadata);
                    if (node.hasNonNull("titleKey") && titleKey.equals(node.get("titleKey").asText())) {
                        if (node.hasNonNull("title") && StringUtils.hasText(node.get("title").asText())) {
                            return node.get("title").asText();
                        }
                        if (node.hasNonNull("label") && StringUtils.hasText(node.get("label").asText())) {
                            return node.get("label").asText();
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (DataAccessException ex) {
            log.debug("Skip title lookup in repository: {}", ex.getMessage());
        }
        return null;
    }

    private String findTitleInNodes(List<MenuNode> nodes, String titleKey) {
        if (nodes == null || nodes.isEmpty() || !StringUtils.hasText(titleKey)) {
            return null;
        }
        for (MenuNode node : nodes) {
            if (node == null) {
                continue;
            }
            if (titleKey.equals(node.titleKey()) && StringUtils.hasText(node.title())) {
                return node.title();
            }
            String nested = findTitleInNodes(node.children(), titleKey);
            if (StringUtils.hasText(nested)) {
                return nested;
            }
        }
        return null;
    }

    private Optional<String> resolveTitleByPath(String path) {
        String normalized = normalizeMenuPath(path);
        if (!StringUtils.hasText(normalized)) {
            return Optional.empty();
        }
        MenuSeed seed = menuSeed();
        if (seed == null || seed.portalNavSections() == null) {
            return Optional.empty();
        }
        String resolved = findTitleByPath(seed.portalNavSections(), normalized, "");
        if (StringUtils.hasText(resolved)) {
            return Optional.of(resolved);
        }
        return Optional.empty();
    }

    private String findTitleByPath(List<MenuNode> nodes, String targetPath, String parentPath) {
        if (nodes == null || nodes.isEmpty() || !StringUtils.hasText(targetPath)) {
            return null;
        }
        for (MenuNode node : nodes) {
            if (node == null) {
                continue;
            }
            String currentPath = combineMenuPath(parentPath, node.path());
            if (StringUtils.hasText(currentPath) && currentPath.equals(targetPath) && StringUtils.hasText(node.title())) {
                return node.title();
            }
            String nested = findTitleByPath(node.children(), targetPath, currentPath);
            if (StringUtils.hasText(nested)) {
                return nested;
            }
        }
        return null;
    }

    private Optional<String> resolveTitleFromMetadata(String metadataJson) {
        if (!StringUtils.hasText(metadataJson)) {
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(metadataJson);
            if (node.hasNonNull("title") && StringUtils.hasText(node.get("title").asText())) {
                return Optional.of(node.get("title").asText().trim());
            }
            if (node.hasNonNull("label") && StringUtils.hasText(node.get("label").asText())) {
                return Optional.of(node.get("label").asText().trim());
            }
            if (node.hasNonNull("titleKey") && StringUtils.hasText(node.get("titleKey").asText())) {
                String key = node.get("titleKey").asText().trim();
                Optional<String> resolved = resolveTitleByKey(key);
                if (resolved.isPresent()) {
                    return resolved;
                }
            }
        } catch (Exception ex) {
            log.debug("Failed to parse menu metadata for display name: {}", ex.getMessage());
        }
        return Optional.empty();
    }

    private String combineMenuPath(String parentPath, String segment) {
        String normalizedSegment = segment == null ? "" : segment.trim();
        if (normalizedSegment.startsWith("/")) {
            normalizedSegment = normalizedSegment.substring(1);
        }
        if (normalizedSegment.endsWith("/")) {
            normalizedSegment = normalizedSegment.substring(0, normalizedSegment.length() - 1);
        }
        String base = StringUtils.hasText(parentPath) ? parentPath : "";
        if (!StringUtils.hasText(base)) {
            return StringUtils.hasText(normalizedSegment) ? "/" + normalizedSegment : "/";
        }
        if (!base.startsWith("/")) {
            base = "/" + base;
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!StringUtils.hasText(normalizedSegment)) {
            return base;
        }
        return base + "/" + normalizedSegment;
    }

    private String normalizeMenuPath(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        String normalized = path.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized.replaceAll("^/+", "");
        }
        if (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String extractTitleKey(PortalMenu menu) {
        if (menu == null || !StringUtils.hasText(menu.getMetadata())) return null;
        try {
            JsonNode node = objectMapper.readTree(menu.getMetadata());
            if (node.hasNonNull("titleKey")) return node.get("titleKey").asText();
        } catch (Exception ignored) {}
        return null;
    }

    private String normalizeCodeSynonyms(String code) {
        if (!StringUtils.hasText(code)) return null;
        String c = code.trim();
        if ("sys.nav.portal.viz".equals(c)) return "sys.nav.portal.visualization";
        return c;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadDefaultRoleBindings() {
        try {
            ClassPathResource resource = new ClassPathResource("config/data/role-menu-defaults.json");
            if (!resource.exists()) return java.util.Collections.emptyList();
            try (InputStream is = resource.getInputStream()) {
                return objectMapper.readValue(is, List.class);
            }
        } catch (Exception ex) {
            log.warn("Failed to read role-menu-defaults.json: {}", ex.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    private String objToString(Object o) { return o == null ? null : Objects.toString(o, null); }
    @SuppressWarnings("unchecked")
    private List<String> listOfString(Object o) {
        if (o instanceof List<?> l) {
            List<String> r = new ArrayList<>();
            for (Object e : l) if (e != null && StringUtils.hasText(e.toString())) r.add(e.toString().trim());
            return r;
        }
        return java.util.Collections.emptyList();
    }

    private boolean isSeedAligned(List<PortalMenu> roots, MenuSeed seed) {
        List<MenuNode> sections = seed.portalNavSections();
        if (sections == null || sections.isEmpty()) {
            return true;
        }
        Set<String> expected = sections
            .stream()
            .map(MenuNode::key)
            .filter(StringUtils::hasText)
            .map(key -> key.trim().toLowerCase(Locale.ROOT))
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (expected.isEmpty()) {
            return true;
        }
        if (roots == null || roots.isEmpty()) {
            return false;
        }
        Set<String> actual = new LinkedHashSet<>();
        for (PortalMenu root : roots) {
            if (root == null) continue;
            String actualKey = extractMetadataKey(root);
            if (!StringUtils.hasText(actualKey)) {
                // Backward-compat: older rows only had sectionKey in metadata
                actualKey = extractSectionKey(root);
            }
            if (StringUtils.hasText(actualKey)) {
                actual.add(actualKey.trim().toLowerCase(Locale.ROOT));
            }
        }
        // Seed keys must be present; allow extra root menus (custom/extended) without forcing a destructive reset.
        for (String key : expected) {
            if (!actual.contains(key)) {
                return false;
            }
        }
        return true;
    }

    private String extractMetadataKey(PortalMenu menu) {
        if (menu == null || !StringUtils.hasText(menu.getMetadata())) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(menu.getMetadata());
            if (node.hasNonNull("key")) {
                return node.get("key").asText();
            }
        } catch (Exception ex) {
            log.debug("Failed to extract key from portal menu metadata: {}", ex.getMessage());
        }
        return null;
    }

    private List<PortalMenuVisibility> defaultVisibilities(PortalMenu menu) {
        return new ArrayList<>();
    }

    private PortalMenuVisibility copyVisibility(PortalMenu targetMenu, PortalMenuVisibility source) {
        if (source == null) {
            return null;
        }
        PortalMenuVisibility copy = new PortalMenuVisibility();
        copy.setId(null);
        copy.setMenu(targetMenu);
        copy.setRoleCode(source.getRoleCode());
        copy.setPermissionCode(source.getPermissionCode());
        copy.setDataLevel(source.getDataLevel());
        return copy;
    }

    public void synchronizeRoleMenuVisibility(String roleCode, String scope, Set<String> operations) {
        String normalizedRole = normalizeRoleCode(roleCode);
        if (!StringUtils.hasText(normalizedRole)) {
            return;
        }
        Set<String> sections = determineSectionsForRole(normalizedRole, scope, operations);
        if (sections.isEmpty()) {
            sections = new LinkedHashSet<>(BASE_READ_SECTIONS);
        }

        List<PortalMenu> allMenus = menuRepo.findAll();
        // Guard against malformed rows (shouldn't happen, but avoid hard failure)
        Map<Long, PortalMenu> menuIndex = allMenus
            .stream()
            .filter(m -> m != null && m.getId() != null)
            .collect(Collectors.toMap(PortalMenu::getId, m -> m));
        Set<Long> targetMenuIds = resolveMenuIdsForSections(allMenus, sections);
        for (Long menuId : new ArrayList<>(targetMenuIds)) {
            collectAncestors(targetMenuIds, menuIndex.get(menuId));
        }

        List<PortalMenuVisibility> existing = visibilityRepo.findByRoleCode(normalizedRole);
        Set<Long> existingIds = existing
            .stream()
            .map(v -> v.getMenu() != null ? v.getMenu().getId() : null)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        for (PortalMenuVisibility visibility : new ArrayList<>(existing)) {
            Long menuId = visibility.getMenu() != null ? visibility.getMenu().getId() : null;
            if (menuId == null || !targetMenuIds.contains(menuId)) {
                removeVisibility(visibility);
            }
        }

        for (Long menuId : targetMenuIds) {
            if (existingIds.contains(menuId)) {
                continue;
            }
            PortalMenu menu = menuIndex.get(menuId);
            if (menu == null) {
                continue;
            }
            PortalMenuVisibility visibility = new PortalMenuVisibility();
            visibility.setMenu(menu);
            visibility.setRoleCode(normalizedRole);
            visibility.setDataLevel(SecurityLevelCatalog.DEFAULT_DATA_SECURITY_LEVEL.code());
            menu.addVisibility(visibility);
            visibilityRepo.save(visibility);
        }
    }

    private void removeVisibility(PortalMenuVisibility visibility) {
        PortalMenu menu = visibility.getMenu();
        if (menu != null) {
            menu.getVisibilities().removeIf(v -> Objects.equals(v.getId(), visibility.getId()));
        }
        visibilityRepo.delete(visibility);
    }

    private Set<Long> resolveMenuIdsForSections(Collection<PortalMenu> menus, Set<String> sections) {
        if (CollectionUtils.isEmpty(menus) || CollectionUtils.isEmpty(sections)) {
            return Set.of();
        }
        Set<String> normalizedSections = sections
            .stream()
            .filter(StringUtils::hasText)
            .map(section -> section.trim().toLowerCase(Locale.ROOT))
            .filter(section -> !DISABLED_SECTIONS.contains(section))
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalizedSections.isEmpty()) {
            return Set.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (PortalMenu menu : menus) {
            String sectionKey = extractSectionKey(menu);
            if (sectionKey != null && normalizedSections.contains(sectionKey.trim().toLowerCase(Locale.ROOT))) {
                ids.add(menu.getId());
            }
        }
        return ids;
    }

    private String extractSectionKey(PortalMenu menu) {
        if (menu == null || !StringUtils.hasText(menu.getMetadata())) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(menu.getMetadata());
            if (node.hasNonNull("sectionKey")) {
                return node.get("sectionKey").asText();
            }
        } catch (Exception ex) {
            log.debug("Failed to parse portal menu metadata for id {}: {}", menu.getId(), ex.getMessage());
        }
        return null;
    }

    private String extractEntryKey(PortalMenu menu) {
        if (menu == null || !StringUtils.hasText(menu.getMetadata())) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(menu.getMetadata());
            if (node.hasNonNull("entryKey")) {
                return node.get("entryKey").asText();
            }
        } catch (Exception ex) {
            log.debug("Failed to parse portal menu metadata for id {}: {}", menu.getId(), ex.getMessage());
        }
        return null;
    }

    private boolean isDisabledMenu(PortalMenu menu) {
        String sectionKey = extractSectionKey(menu);
        if (isDisabledSectionKey(sectionKey)) {
            return true;
        }
        // Fallback for legacy rows with missing/invalid metadata: detect by name/path tokens.
        String name = menu != null ? menu.getName() : null;
        String path = menu != null ? menu.getPath() : null;
        String nameLower = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        String pathLower = path == null ? "" : path.trim().toLowerCase(Locale.ROOT);
        String entryKey = extractEntryKey(menu);
        String titleKey = extractTitleKey(menu);
        String normalizedPath = pathLower.replaceAll("^/+", "").replaceAll("/+$", "");
        if (
            ("services".equalsIgnoreCase(sectionKey) && "consumption".equalsIgnoreCase(entryKey)) ||
            "sys.nav.portal.servicesConsumption".equals(titleKey) ||
            "services/consumption".equals(normalizedPath)
        ) {
            return true;
        }
        if ("security".equalsIgnoreCase(sectionKey) && StringUtils.hasText(entryKey)) {
            String normalized = entryKey.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
            if ("threeadmins".equals(normalized)) {
                return true;
            }
        }
        if (
            nameLower.contains("threeadmins") ||
            nameLower.contains("three_admins") ||
            nameLower.contains("three-admins") ||
            pathLower.contains("threeadmins") ||
            pathLower.contains("three_admins") ||
            pathLower.contains("three-admins")
        ) {
            return true;
        }
        return false;
    }

    private boolean isDisabledSectionKey(String sectionKey) {
        if (!StringUtils.hasText(sectionKey)) {
            return false;
        }
        String normalized = sectionKey.trim().toLowerCase(Locale.ROOT);
        return DISABLED_SECTIONS.contains(normalized);
    }

    private Set<String> determineSectionsForRole(String normalizedRole, String scope, Set<String> operations) {
        LinkedHashSet<String> sections = new LinkedHashSet<>(BASE_READ_SECTIONS);
        Set<String> ops = operations == null
            ? Set.of()
            : operations.stream().filter(StringUtils::hasText).map(op -> op.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        boolean hasWrite = ops.contains("write");
        boolean hasExport = ops.contains("export");

        if (hasWrite || hasExport) {
            sections.addAll(WRITE_SECTIONS);
            if ("INSTITUTE".equalsIgnoreCase(scope) || "INST".equalsIgnoreCase(scope)) {
                sections.addAll(FOUNDATION_SECTIONS);
            } else if (isOwnerRole(normalizedRole)) {
                // Department maintainers can manage foundation resources within their department context (e.g. data sources)
                sections.addAll(FOUNDATION_SECTIONS);
            }
            if (isOwnerRole(normalizedRole)) {
                sections.addAll(IAM_SECTIONS);
            }
        } else if ("INSTITUTE".equalsIgnoreCase(scope) || "INST".equalsIgnoreCase(scope)) {
            sections.addAll(FOUNDATION_SECTIONS);
        } else if (isOwnerRole(normalizedRole)) {
            // Even without explicit WRITE operations, dept owners/leaders still need foundation access
            sections.addAll(FOUNDATION_SECTIONS);
        }
        sections.removeIf(this::isDisabledSectionKey);

        return sections;
    }

    private boolean isOwnerRole(String normalizedRole) {
        if (normalizedRole == null) {
            return false;
        }
        return normalizedRole.endsWith("_OWNER") || normalizedRole.endsWith("_LEADER");
    }

    private String normalizeRoleCode(String role) {
        if (!StringUtils.hasText(role)) {
            return null;
        }
        String trimmed = role.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        return upper.startsWith("ROLE_") ? upper : "ROLE_" + upper;
    }

    private String stripRolePrefix(String role) {
        if (!StringUtils.hasText(role)) {
            return null;
        }
        String upper = role.trim().toUpperCase(Locale.ROOT);
        if (upper.startsWith("ROLE_")) {
            return upper.substring(5);
        }
        return upper;
    }

    private record MenuSeed(List<MenuNode> portalNavSections) {}

    private record MenuNode(
        String key,
        String path,
        String icon,
        String titleKey,
        String title,
        String externalLink,
        List<MenuNode> children
    ) {}
}
