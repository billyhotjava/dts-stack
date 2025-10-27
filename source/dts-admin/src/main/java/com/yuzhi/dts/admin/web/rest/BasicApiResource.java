package com.yuzhi.dts.admin.web.rest;

import com.yuzhi.dts.admin.domain.PortalMenu;
import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.admin.service.PortalMenuService;
import com.yuzhi.dts.admin.service.audit.AdminAuditOperation;
import com.yuzhi.dts.admin.service.audit.AdminAuditService;
import com.yuzhi.dts.admin.service.dto.menu.MenuTreeDTO;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@io.swagger.v3.oas.annotations.tags.Tag(name = "basic")
public class BasicApiResource {

    private final PortalMenuService portalMenuService;
    private final AdminAuditService auditService;

    public BasicApiResource(PortalMenuService portalMenuService, AdminAuditService auditService) {
        this.portalMenuService = portalMenuService;
        this.auditService = auditService;
    }

    @GetMapping("/menu")
    @Operation(summary = "List portal menu tree")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successful operation")
    public ResponseEntity<ApiResponse<List<MenuTreeDTO>>> getMenuTree(
        @RequestParam(name = "roles", required = false) List<String> roleParams,
        @RequestParam(name = "permissions", required = false) List<String> permissionParams,
        @RequestParam(name = "dataLevel", required = false) String dataLevel
    ) {
        Set<String> roleCodes = normalizeRoles(roleParams);
        Set<String> permissionCodes = normalizePermissions(permissionParams);
        String normalizedLevel = normalizeDataLevel(dataLevel);

        if (CollectionUtils.isEmpty(roleCodes)) {
            roleCodes = rolesFromSecurityContext();
        }

        List<PortalMenu> roots;
        if (!CollectionUtils.isEmpty(roleCodes) || !CollectionUtils.isEmpty(permissionCodes) || StringUtils.hasText(normalizedLevel)) {
            roots = portalMenuService.findTreeForAudience(roleCodes, permissionCodes, normalizedLevel);
        } else {
            roots = portalMenuService.findTree();
        }

        List<MenuTreeDTO> out = new ArrayList<>();
        for (PortalMenu root : roots) {
            MenuTreeDTO dto = toDto(root);
            if (dto != null) {
                out.add(dto);
            }
        }
        auditService.record(
            auditService
                .builder()
                .actor(SecurityUtils.getCurrentUserLogin().orElse(null))
                .fromOperation(AdminAuditOperation.PORTAL_MENU_FETCH)
                .summary("查询门户菜单")
                .details(Map.of("roleCount", roleCodes.size(), "permissionCount", permissionCodes.size()))
                .result(AdminAuditService.AuditResult.SUCCESS)
                .build()
        );
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @GetMapping("/user")
    @Operation(summary = "Demo user list (mock-compatible)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successful operation")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getDemoUsers() {
        List<Map<String, Object>> demo = List.of();
        auditService.record(
            auditService
                .builder()
                .actor(SecurityUtils.getCurrentUserLogin().orElse(null))
                .fromOperation(AdminAuditOperation.ADMIN_USER_VIEW)
                .summary("查看示例用户列表")
                .details(Map.of("source", "demo"))
                .result(AdminAuditService.AuditResult.SUCCESS)
                .build()
        );
        return ResponseEntity.ok(ApiResponse.ok(demo));
    }

    private static String slug(String s) {
        if (s == null) return "menu";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private MenuTreeDTO toDto(PortalMenu p) {
        if (p == null) {
            return null;
        }
        MenuTreeDTO d = new MenuTreeDTO();
        d.setId(String.valueOf(p.getId()));
        d.setParentId(p.getParent() != null ? String.valueOf(p.getParent().getId()) : "");
        d.setName(p.getName());
        d.setDisplayName(resolveMenuDisplayName(p));
        d.setCode(slug(Optional.ofNullable(p.getPath()).orElse(p.getName())));
        d.setOrder(p.getSortOrder());
        d.setPath(p.getPath());
        d.setComponent(p.getComponent());
        d.setIcon(p.getIcon());
        d.setMetadata(p.getMetadata());
        d.setSecurityLevel(p.getSecurityLevel());
        d.setType((p.getChildren() != null && !p.getChildren().isEmpty()) ? 1 : 2);
        d.setDeleted(p.isDeleted());
        if (p.getChildren() != null) {
            List<MenuTreeDTO> cs = new ArrayList<>();
            for (PortalMenu c : p.getChildren()) {
                MenuTreeDTO childDto = toDto(c);
                if (childDto != null) {
                    cs.add(childDto);
                }
            }
            d.setChildren(cs);
        }
        return d;
    }

    private String resolveMenuDisplayName(PortalMenu menu) {
        String metadata = menu.getMetadata();
        if (metadata != null && !metadata.isBlank()) {
            try {
                Map<String, Object> meta = new com.fasterxml.jackson.databind.ObjectMapper().readValue(metadata, Map.class);
                Object title = meta.get("title");
                if (title instanceof String s && !s.isBlank()) {
                    return s;
                }
                Object label = meta.get("label");
                if (label instanceof String s && !s.isBlank()) {
                    return s;
                }
            } catch (Exception ignored) {
                // ignore parsing errors and fall back to raw name
            }
        }
        return menu.getName();
    }

    private Set<String> normalizeRoles(List<String> roles) {
        if (roles == null) {
            return new LinkedHashSet<>();
        }
        return roles
            .stream()
            .filter(StringUtils::hasText)
            .map(role -> role.trim().toUpperCase(Locale.ROOT))
            .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> normalizePermissions(List<String> permissions) {
        if (permissions == null) {
            return new LinkedHashSet<>();
        }
        return permissions
            .stream()
            .filter(StringUtils::hasText)
            .map(permission -> permission.trim().toLowerCase(Locale.ROOT))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeDataLevel(String dataLevel) {
        if (!StringUtils.hasText(dataLevel)) {
            return null;
        }
        return dataLevel.trim().toUpperCase(Locale.ROOT);
    }

    private Set<String> rolesFromSecurityContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return new LinkedHashSet<>();
        }
        Collection<? extends GrantedAuthority> authorities;
        if (authentication instanceof JwtAuthenticationToken jwt) {
            authorities = SecurityUtils.extractAuthorityFromClaims(jwt.getToken().getClaims());
        } else {
            authorities = authentication.getAuthorities();
        }
        return authorities
            .stream()
            .map(GrantedAuthority::getAuthority)
            .filter(StringUtils::hasText)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
