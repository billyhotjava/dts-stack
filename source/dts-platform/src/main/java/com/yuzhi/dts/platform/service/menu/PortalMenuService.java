package com.yuzhi.dts.platform.service.menu;

import com.yuzhi.dts.platform.service.menu.PortalMenuClient.RemoteMenuNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PortalMenuService {

    private static final Logger log = LoggerFactory.getLogger(PortalMenuService.class);

    private final PortalMenuClient client;

    public PortalMenuService(PortalMenuClient client) {
        this.client = client;
    }

    public List<PortalMenuTreeItem> getMenuTree() {
        // Forward the current user's roles (sanitized) so dts-admin can filter precisely.
        List<String> roles = sanitizeAudienceRoles(currentAuthorities());
        List<RemoteMenuNode> baseline = client.fetchActiveMenuTree();
        java.util.Set<String> activeIds = flattenIds(baseline);
        java.util.Set<String> visited = new java.util.LinkedHashSet<>();
        List<RemoteMenuNode> remote = (roles != null && !roles.isEmpty()) ? client.fetchMenuTreeForAudience(roles, List.of()) : baseline;
        return remote
            .stream()
            .map(node -> mapTree(node, null, null, activeIds, visited))
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    public List<PortalMenuFlatItem> getFlatMenuList() {
        List<PortalMenuTreeItem> tree = getMenuTree();
        if (tree != null) {
            tree = tree.stream().filter(node -> node != null && !node.deleted()).collect(Collectors.toCollection(ArrayList::new));
        }
        List<PortalMenuFlatItem> flat = new ArrayList<>();
        Deque<String> pathStack = new ArrayDeque<>();
        for (PortalMenuTreeItem root : tree) {
            flatten(root, pathStack, flat);
        }
        return flat;
    }

    private List<String> currentAuthorities() {
        try {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .getAuthentication();
            if (auth == null || auth.getAuthorities() == null) return List.of();
            return auth
                .getAuthorities()
                .stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        } catch (Exception ex) {
            log.debug("No authorities found in SecurityContext: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * Sanitize audience roles before forwarding to dts-admin:
     * - Normalize to upper-case with ROLE_ prefix
     * - If user has any specific roles in addition to ROLE_USER, drop ROLE_USER to avoid broad default menus
     */
    private List<String> sanitizeAudienceRoles(List<String> authorities) {
        if (authorities == null || authorities.isEmpty()) return List.of();
        List<String> normalized = authorities
            .stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(s -> s.toUpperCase(java.util.Locale.ROOT))
            .map(s -> s.startsWith("ROLE_") ? s : "ROLE_" + s)
            .distinct()
            .collect(Collectors.toCollection(ArrayList::new));
        boolean hasSpecific = normalized.stream().anyMatch(r -> !"ROLE_USER".equals(r));
        if (hasSpecific) {
            normalized.remove("ROLE_USER");
        }
        return normalized;
    }

    public List<PortalMenuTreeNode> getMenuTreeView() {
        return getMenuTree().stream().map(item -> toTreeNode(item, null)).collect(Collectors.toCollection(ArrayList::new));
    }

    public Map<String, Object> createMenu(Map<String, Object> payload) {
        return client.createPortalMenu(payload);
    }

    public Map<String, Object> updateMenu(Long id, Map<String, Object> payload) {
        return client.updatePortalMenu(id, payload);
    }

    public Map<String, Object> deleteMenu(Long id) {
        return client.deletePortalMenu(id);
    }

    private void flatten(PortalMenuTreeItem node, Deque<String> pathStack, List<PortalMenuFlatItem> out) {
        if (node.deleted()) {
            return;
        }
        String parentPath = pathStack.peekLast();
        String fullPath = buildFullPath(parentPath, node.pathSegment());

        out.add(
            new PortalMenuFlatItem(
                node.id() != null ? String.valueOf(node.id()) : node.generatedId(),
                node.parentId() != null ? String.valueOf(node.parentId()) : Optional.ofNullable(pathStack.peekLast()).orElse(""),
                node.name(),
                node.code(),
                node.sortOrder(),
                node.type(),
                fullPath,
                node.component(),
                node.icon(),
                node.metadata(),
                node.deleted()
            )
        );

        if (!node.children().isEmpty()) {
            pathStack.addLast(fullPath);
            for (PortalMenuTreeItem child : node.children()) {
                flatten(child, pathStack, out);
            }
            pathStack.removeLast();
        }
    }

    private PortalMenuTreeItem mapTree(
        RemoteMenuNode node,
        PortalMenuTreeItem parent,
        String inheritedPath,
        java.util.Set<String> activeIds,
        java.util.Set<String> visited
    ) {
        if (Boolean.TRUE.equals(node.getDeleted())) {
            return null;
        }
        Long id = parseLong(node.getId());
        if (activeIds != null && id != null && !activeIds.isEmpty()) {
            if (!activeIds.contains(String.valueOf(id))) {
                return null;
            }
        }
        Long parentId = parseLong(node.getParentId());
        String pathSegment = normalizePathSegment(node.getPath());
        String fullPath = buildFullPath(inheritedPath, pathSegment);
        String dedupeKey = dedupeKey(id, fullPath);
        if (visited != null && dedupeKey != null) {
            if (visited.contains(dedupeKey)) {
                return null;
            }
            visited.add(dedupeKey);
        }
        List<PortalMenuTreeItem> children = new ArrayList<>();
        PortalMenuTreeItem current = new PortalMenuTreeItem(
            id,
            parent != null ? parent.id() : parentId,
            node.getName(),
            node.getCode(),
            node.getOrder(),
            deriveType(node, children),
            pathSegment,
            fullPath,
            node.getComponent(),
            node.getIcon(),
            node.getMetadata(),
            Boolean.TRUE.equals(node.getDeleted()),
            children,
            generateFallbackId(node, parent)
        );

        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
            List<RemoteMenuNode> sortedChildren = node
                .getChildren()
                .stream()
                .filter(Objects::nonNull)
                .sorted(this::compareNodes)
                .collect(Collectors.toList());
            for (RemoteMenuNode child : sortedChildren) {
                PortalMenuTreeItem mappedChild = mapTree(child, current, fullPath, activeIds, visited);
                if (mappedChild != null) {
                    children.add(mappedChild);
                }
            }
        }

        return current;
    }

    private PortalMenuTreeNode toTreeNode(PortalMenuTreeItem item, String parentId) {
        String nodeId = item.id() != null ? String.valueOf(item.id()) : item.generatedId();
        String currentParentId = parentId != null ? parentId : (item.parentId() != null ? String.valueOf(item.parentId()) : "");
        List<PortalMenuTreeNode> children = item
            .children()
            .stream()
            .map(child -> toTreeNode(child, nodeId))
            .collect(Collectors.toCollection(ArrayList::new));
        return new PortalMenuTreeNode(
            nodeId,
            currentParentId,
            item.name(),
            item.code(),
            item.sortOrder(),
            item.type(),
            item.fullPath(),
            item.component(),
            item.icon(),
            item.metadata(),
            item.deleted(),
            children
        );
    }

    private Integer deriveType(RemoteMenuNode node, List<PortalMenuTreeItem> children) {
        if (node.getType() != null) {
            return node.getType();
        }
        return (node.getChildren() != null && !node.getChildren().isEmpty()) ? 1 : 2;
    }

    private String normalizePathSegment(String segment) {
        if (!StringUtils.hasText(segment)) {
            return "";
        }
        String trimmed = segment.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String buildFullPath(String parentPath, String segment) {
        if (!StringUtils.hasText(segment)) {
            return parentPath == null ? "" : parentPath;
        }
        if (!StringUtils.hasText(parentPath)) {
            return "/" + segment;
        }
        String normalizedParent = parentPath.endsWith("/") ? parentPath.substring(0, parentPath.length() - 1) : parentPath;
        if (segment.isEmpty()) {
            return normalizedParent;
        }
        return normalizedParent + "/" + segment;
    }

    private Long parseLong(String value) {
        try {
            if (!StringUtils.hasText(value)) {
                return null;
            }
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            log.debug("Unable to parse portal menu id '{}'", value, ex);
            return null;
        }
    }

    private String generateFallbackId(RemoteMenuNode node, PortalMenuTreeItem parent) {
        String base = Optional.ofNullable(parent).map(PortalMenuTreeItem::fullPath).orElse("");
        String segment = normalizePathSegment(node.getPath());
        String candidate = (StringUtils.hasText(base) ? base + "/" : "") + (StringUtils.hasText(segment) ? segment : slug(node.getName()));
        return candidate.replaceAll("[^a-zA-Z0-9:/_-]", "-");
    }

    private String dedupeKey(Long id, String fullPath) {
        if (id != null) {
            return "id:" + id;
        }
        if (StringUtils.hasText(fullPath)) {
            return "path:" + fullPath;
        }
        return null;
    }

    private int compareNodes(RemoteMenuNode left, RemoteMenuNode right) {
        int orderLeft = left.getOrder() != null ? left.getOrder() : Integer.MAX_VALUE;
        int orderRight = right.getOrder() != null ? right.getOrder() : Integer.MAX_VALUE;
        int cmp = Integer.compare(orderLeft, orderRight);
        if (cmp != 0) {
            return cmp;
        }
        String nameLeft = left.getName() != null ? left.getName() : "";
        String nameRight = right.getName() != null ? right.getName() : "";
        cmp = nameLeft.compareToIgnoreCase(nameRight);
        if (cmp != 0) {
            return cmp;
        }
        String idLeft = left.getId() != null ? left.getId() : "";
        String idRight = right.getId() != null ? right.getId() : "";
        return idLeft.compareTo(idRight);
    }

    private String slug(String input) {
        if (!StringUtils.hasText(input)) {
            return "menu";
        }
        return input
            .trim()
            .toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-|-$)", "");
    }

    public record PortalMenuTreeItem(
        Long id,
        Long parentId,
        String name,
        String code,
        Integer sortOrder,
        Integer type,
        String pathSegment,
        String fullPath,
        String component,
        String icon,
        String metadata,
        boolean deleted,
        List<PortalMenuTreeItem> children,
        String generatedId
    ) {
        public List<PortalMenuTreeItem> children() {
            return children != null ? children : List.of();
        }
    }

    public record PortalMenuFlatItem(
        String id,
        String parentId,
        String name,
        String code,
        Integer order,
        Integer type,
        String path,
        String component,
        String icon,
        String metadata,
        boolean deleted
    ) {}

    public record PortalMenuTreeNode(
        String id,
        String parentId,
        String name,
        String code,
        Integer order,
        Integer type,
        String path,
        String component,
        String icon,
        String metadata,
        boolean deleted,
        List<PortalMenuTreeNode> children
    ) {}

    private java.util.Set<String> flattenIds(List<RemoteMenuNode> nodes) {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        if (nodes == null || nodes.isEmpty()) {
            return ids;
        }
        java.util.ArrayDeque<RemoteMenuNode> stack = new java.util.ArrayDeque<>(nodes);
        while (!stack.isEmpty()) {
            RemoteMenuNode current = stack.pop();
            if (current == null) {
                continue;
            }
            if (StringUtils.hasText(current.getId())) {
                ids.add(current.getId());
            }
            if (current.getChildren() != null) {
                for (RemoteMenuNode child : current.getChildren()) {
                    if (child != null) {
                        stack.push(child);
                    }
                }
            }
        }
        return ids;
    }
}
