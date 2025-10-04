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
        // Try to forward current user's roles/permissions to dts-admin so it can filter menus.
        List<String> roles = currentAuthorities();
        List<RemoteMenuNode> remote;
        if (roles != null && !roles.isEmpty()) {
            remote = client.fetchMenuTreeForAudience(roles, List.of(), null);
        } else {
            remote = client.fetchMenuTree();
        }
        return remote.stream().map(node -> mapTree(node, null, null)).collect(Collectors.toCollection(ArrayList::new));
    }

    public List<PortalMenuFlatItem> getFlatMenuList() {
        List<PortalMenuTreeItem> tree = getMenuTree();
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
            return auth.getAuthorities().stream().map(org.springframework.security.core.GrantedAuthority::getAuthority).filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        } catch (Exception ex) {
            log.debug("No authorities found in SecurityContext: {}", ex.getMessage());
            return List.of();
        }
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
                node.metadata()
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

    private PortalMenuTreeItem mapTree(RemoteMenuNode node, PortalMenuTreeItem parent, String inheritedPath) {
        Long id = parseLong(node.getId());
        Long parentId = parseLong(node.getParentId());
        String pathSegment = normalizePathSegment(node.getPath());
        String fullPath = buildFullPath(inheritedPath, pathSegment);
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
            children,
            generateFallbackId(node, parent)
        );

        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
            for (RemoteMenuNode child : node.getChildren()) {
                children.add(mapTree(child, current, fullPath));
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
        String metadata
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
        List<PortalMenuTreeNode> children
    ) {}
}
