package com.yuzhi.dts.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.domain.PortalMenu;
import com.yuzhi.dts.admin.domain.PortalMenuVisibility;
import com.yuzhi.dts.admin.repository.PortalMenuRepository;
import com.yuzhi.dts.admin.repository.PortalMenuVisibilityRepository;
import com.yuzhi.dts.admin.security.AuthoritiesConstants;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.core.io.ClassPathResource;

@Service
@Transactional
public class PortalMenuService {

    private static final Logger log = LoggerFactory.getLogger(PortalMenuService.class);

    // Default roles that can see menus when no explicit visibility is defined.
    // Note: Do NOT include ROLE_USER here; otherwise all authenticated users would see all menus.
    private static final List<String> DEFAULT_MENU_ROLES = List.of(
        "ROLE_OP_ADMIN"
    );
    private static final Set<String> BASE_READ_SECTIONS = Set.of("catalog", "explore", "visualization");
    private static final Set<String> WRITE_SECTIONS = Set.of("modeling", "governance", "services");
    private static final Set<String> FOUNDATION_SECTIONS = Set.of("foundation");
    private static final Set<String> IAM_SECTIONS = Set.of("iam");
    private static final Map<String, String> MENU_COMPONENTS = Map.ofEntries(
        Map.entry("catalog.assets", "/pages/catalog/DatasetsPage"),
        Map.entry("catalog.accessPolicy", "/pages/catalog/AccessPolicyPage"),
        Map.entry("catalog.secureViews", "/pages/catalog/SecureViewsPage"),
        Map.entry("modeling.standards", "/pages/modeling/DataStandardsPage"),
        Map.entry("governance.rules", "/pages/governance/QualityRulesPage"),
        Map.entry("governance.compliance", "/pages/governance/CompliancePage"),
        Map.entry("explore.workbench", "/pages/explore/QueryWorkbenchPage"),
        Map.entry("explore.savedQueries", "/pages/explore/SavedQueriesPage"),
        Map.entry("services.api", "/pages/services/ApiServicesPage"),
        Map.entry("services.products", "/pages/services/DataProductsPage"),
        Map.entry("services.tokens", "/pages/services/TokensPage"),
        Map.entry("visualization.dashboards", "/pages/visualization/DashboardsPage"),
        Map.entry("visualization.cockpit", "/pages/visualization/CockpitPage"),
        Map.entry("visualization.projects", "/pages/visualization/ProjectsSummaryPage"),
        Map.entry("visualization.finance", "/pages/visualization/FinanceSummaryPage"),
        Map.entry("visualization.supplyChain", "/pages/visualization/SupplyChainSummaryPage"),
        Map.entry("visualization.hr", "/pages/visualization/HRSummaryPage"),
        Map.entry("iam.classification", "/pages/iam/ClassificationMappingPage"),
        Map.entry("iam.authorization", "/pages/iam/AuthorizationPage"),
        Map.entry("iam.simulation", "/pages/iam/SimulationPage"),
        Map.entry("iam.requests", "/pages/iam/RequestsPage"),
        Map.entry("foundation.dataSources", "/pages/foundation/DataSourcesPage"),
        Map.entry("foundation.dataStorage", "/pages/foundation/DataStoragePage"),
        Map.entry("foundation.taskScheduling", "/pages/foundation/TaskSchedulingPage")
    );

    private final PortalMenuRepository menuRepo;
    private final PortalMenuVisibilityRepository visibilityRepo;
    private final ObjectMapper objectMapper;

    private volatile MenuSeed cachedSeed;

    public PortalMenuService(PortalMenuRepository menuRepo, PortalMenuVisibilityRepository visibilityRepo, ObjectMapper objectMapper) {
        this.menuRepo = menuRepo;
        this.visibilityRepo = visibilityRepo;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<PortalMenu> findTree() {
        ensureSeedMenus();
        return runSafely(
            () -> {
                List<PortalMenu> roots = menuRepo.findByDeletedFalseAndParentIsNullOrderBySortOrderAscIdAsc();
                roots.forEach(this::touch);
                return roots;
            },
            List.of(),
            "menu tree"
        );
    }

    @Transactional(readOnly = true)
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
                return deleted;
            },
            List.of(),
            "deleted menu list"
        );
    }

    @Transactional(readOnly = true)
    public List<PortalMenu> findAllMenusOrdered() {
        ensureSeedMenus();
        return runSafely(
            () -> {
                List<PortalMenu> all = menuRepo.findAllByOrderBySortOrderAscIdAsc();
                all.forEach(this::touch);
                return all;
            },
            List.of(),
            "full menu list"
        );
    }

    private <T> T runSafely(java.util.concurrent.Callable<T> action, T fallback, String label) {
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
            if (CollectionUtils.isEmpty(roleCodes)) {
                return false;
            }
            return roleCodes.stream().anyMatch(DEFAULT_MENU_ROLES::contains);
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
        // Unconditional bypass for operator admin: OP_ADMIN must see all menus permanently
        if (!CollectionUtils.isEmpty(roleCodes) && roleCodes.contains(AuthoritiesConstants.OP_ADMIN)) {
            return true;
        }
        if (!StringUtils.hasText(visibility.getRoleCode())) {
            return true;
        }
        if (CollectionUtils.isEmpty(roleCodes)) {
            return false;
        }
        if (roleCodes.contains(visibility.getRoleCode())) {
            // New rule: ROLE_USER is non-binding (does not grant any menu)
            if (AuthoritiesConstants.USER.equals(visibility.getRoleCode())) {
                return false;
            }
            return true;
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
        if (!StringUtils.hasText(visibility.getDataLevel()) || visibility.getDataLevel().equalsIgnoreCase("INTERNAL")) {
            return true;
        }
        if (!StringUtils.hasText(maxDataLevel)) {
            return false;
        }
        return dataLevelPriority(maxDataLevel) >= dataLevelPriority(visibility.getDataLevel());
    }

    private int dataLevelPriority(String level) {
        return switch (level == null ? "" : level.toUpperCase(Locale.ROOT)) {
            case "PUBLIC", "NON_SECRET" -> 1;
            case "INTERNAL", "GENERAL" -> 2;
            case "SECRET", "IMPORTANT" -> 3;
            case "TOP_SECRET", "CORE", "CORE_SECRET" -> 4;
            default -> 0;
        };
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
        visibilityRepo.deleteByMenuId(menu.getId());
        menu.clearVisibilities();
        List<PortalMenuVisibility> effective = (visibilities == null || visibilities.isEmpty()) ? defaultVisibilities(menu) : visibilities;
        for (PortalMenuVisibility visibility : effective) {
            menu.addVisibility(visibility);
        }
        menuRepo.save(menu);
    }

    public void resetMenusToSeed() {
        MenuSeed seed = menuSeed();
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
    }

    private PortalMenu buildMenuTree(MenuNode node, PortalMenu parent, int sortOrder, String compositeKey, String sectionKey) {
        PortalMenu menu = new PortalMenu();
        menu.setName(resolveName(node));
        menu.setPath(buildPath(parent, node.path()));
        menu.setIcon(node.icon());
        menu.setSortOrder(sortOrder);
        menu.setMetadata(writeMetadata(node, parent == null, sectionKey));
        menu.setSecurityLevel("GENERAL");
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
        String parentPath = parent == null ? "" : parent.getPath();
        if (!StringUtils.hasText(parentPath)) {
            return "/" + normalized;
        }
        String base = parentPath.endsWith("/") ? parentPath.substring(0, parentPath.length() - 1) : parentPath;
        return normalized.isEmpty() ? base : base + "/" + normalized;
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

    private void ensureSeedMenus() {
        try {
            MenuSeed seed = menuSeed();
            List<PortalMenu> roots = menuRepo.findByDeletedFalseAndParentIsNullOrderBySortOrderAscIdAsc();
            if (!isSeedAligned(roots, seed)) {
                resetMenusToSeed();
            }
        } catch (Exception ex) {
            log.warn("Skip portal menu seed verification due to: {}", ex.getMessage());
        }
    }

    private boolean isSeedAligned(List<PortalMenu> roots, MenuSeed seed) {
        List<MenuNode> sections = seed.portalNavSections();
        if (sections == null || sections.isEmpty()) {
            return true;
        }
        if (roots == null || roots.size() != sections.size()) {
            return false;
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
        for (PortalMenu root : roots) {
            String actualKey = extractMetadataKey(root);
            if (actualKey == null || !expected.contains(actualKey.toLowerCase(Locale.ROOT))) {
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
        List<PortalMenuVisibility> defaults = new ArrayList<>();
        String section = null;
        try {
            section = extractSectionKey(menu);
        } catch (Exception ignore) {}
        // By default, only grant OP_ADMIN; end-users see menus only when their roles are explicitly bound.
        // Foundation section remains OP_ADMIN-only by policy.
        PortalMenuVisibility op = new PortalMenuVisibility();
        op.setMenu(menu);
        op.setRoleCode(AuthoritiesConstants.OP_ADMIN);
        op.setDataLevel("INTERNAL");
        defaults.add(op);
        return defaults;
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
            visibility.setDataLevel("INTERNAL");
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
        Set<Long> ids = new LinkedHashSet<>();
        for (PortalMenu menu : menus) {
            String sectionKey = extractSectionKey(menu);
            if (sectionKey != null && sections.contains(sectionKey)) {
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

    private Set<String> determineSectionsForRole(String normalizedRole, String scope, Set<String> operations) {
        LinkedHashSet<String> sections = new LinkedHashSet<>(BASE_READ_SECTIONS);
        Set<String> ops = operations == null
            ? Set.of()
            : operations.stream().filter(StringUtils::hasText).map(op -> op.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        boolean hasWrite = ops.contains("write");
        boolean hasExport = ops.contains("export");

        if (hasWrite || hasExport) {
            sections.addAll(WRITE_SECTIONS);
            if ("INSTITUTE".equalsIgnoreCase(scope)) {
                sections.addAll(FOUNDATION_SECTIONS);
            }
            if (isOwnerRole(normalizedRole)) {
                sections.addAll(IAM_SECTIONS);
            }
        } else if ("INSTITUTE".equalsIgnoreCase(scope)) {
            sections.addAll(FOUNDATION_SECTIONS);
        }

        return sections;
    }

    private boolean isOwnerRole(String normalizedRole) {
        return normalizedRole != null && normalizedRole.endsWith("_OWNER");
    }

    private String normalizeRoleCode(String role) {
        if (!StringUtils.hasText(role)) {
            return null;
        }
        String trimmed = role.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        return upper.startsWith("ROLE_") ? upper : "ROLE_" + upper;
    }

    private record MenuSeed(List<MenuNode> portalNavSections) {}

    private record MenuNode(String key, String path, String icon, String titleKey, String title, List<MenuNode> children) {}
}
