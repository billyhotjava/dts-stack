package com.yuzhi.dts.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.domain.PortalMenu;
import com.yuzhi.dts.admin.domain.PortalMenuVisibility;
import com.yuzhi.dts.admin.repository.PortalMenuRepository;
import com.yuzhi.dts.admin.repository.PortalMenuVisibilityRepository;
import java.util.ArrayList;
import java.util.Collection;
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

@Service
@Transactional
public class PortalMenuService {

    private static final Logger log = LoggerFactory.getLogger(PortalMenuService.class);

    private static final List<String> DEFAULT_MENU_ROLES = List.of("ROLE_OP_ADMIN", "ROLE_USER");
    private static final Set<String> BASE_READ_SECTIONS = Set.of("catalog", "explore", "visualization");
    private static final Set<String> WRITE_SECTIONS = Set.of("modeling", "governance", "services");
    private static final Set<String> FOUNDATION_SECTIONS = Set.of("foundation");
    private static final Set<String> IAM_SECTIONS = Set.of("iam");

    private final PortalMenuRepository menuRepo;
    private final PortalMenuVisibilityRepository visibilityRepo;
    private final ObjectMapper objectMapper;

    public PortalMenuService(PortalMenuRepository menuRepo, PortalMenuVisibilityRepository visibilityRepo, ObjectMapper objectMapper) {
        this.menuRepo = menuRepo;
        this.visibilityRepo = visibilityRepo;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<PortalMenu> findTree() {
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
        if (!StringUtils.hasText(visibility.getRoleCode())) {
            return true;
        }
        if (CollectionUtils.isEmpty(roleCodes)) {
            return false;
        }
        return roleCodes.contains(visibility.getRoleCode());
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

    private List<PortalMenuVisibility> defaultVisibilities(PortalMenu menu) {
        List<PortalMenuVisibility> defaults = new ArrayList<>();
        for (String role : DEFAULT_MENU_ROLES) {
            PortalMenuVisibility visibility = new PortalMenuVisibility();
            visibility.setMenu(menu);
            visibility.setRoleCode(role);
            visibility.setDataLevel("INTERNAL");
            defaults.add(visibility);
        }
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
        Map<Long, PortalMenu> menuIndex = allMenus.stream().collect(Collectors.toMap(PortalMenu::getId, m -> m));
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
}
