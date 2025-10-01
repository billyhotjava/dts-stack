package com.yuzhi.dts.admin.web.rest;

import com.yuzhi.dts.admin.domain.PortalMenu;
import com.yuzhi.dts.admin.repository.PortalMenuRepository;
import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.admin.service.audit.AdminAuditService;
import com.yuzhi.dts.admin.service.dto.menu.MenuTreeDTO;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@io.swagger.v3.oas.annotations.tags.Tag(name = "basic")
public class BasicApiResource {

    private final PortalMenuRepository menuRepo;
    private final AdminAuditService auditService;

    public BasicApiResource(PortalMenuRepository menuRepo, AdminAuditService auditService) {
        this.menuRepo = menuRepo;
        this.auditService = auditService;
    }

    @GetMapping("/menu")
    @Operation(summary = "List portal menu tree")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successful operation")
    public ResponseEntity<ApiResponse<List<MenuTreeDTO>>> getMenuTree() {
        List<PortalMenu> roots = menuRepo.findByDeletedFalseAndParentIsNullOrderBySortOrderAscIdAsc();
        List<MenuTreeDTO> out = new ArrayList<>();
        for (PortalMenu r : roots) out.add(toDto(r));
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "MENU_LIST", "MENU", "portal", "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @GetMapping("/user")
    @Operation(summary = "Demo user list (mock-compatible)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successful operation")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getDemoUsers() {
        List<Map<String, Object>> demo = List.of();
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "USER_DEMO_LIST", "USER", "demo", "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(demo));
    }

    @PostMapping("/user/tokenExpired")
    @Operation(summary = "Demo token expired endpoint")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Token expired")
    public ResponseEntity<ApiResponse<Void>> tokenExpired() {
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "TOKEN_EXPIRED_DEMO", "AUTH", "demo", "ERROR", null);
        return ResponseEntity.status(401).body(ApiResponse.error("token expired"));
    }

    private static String slug(String s) {
        if (s == null) return "menu";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private MenuTreeDTO toDto(PortalMenu p) {
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
        if (p.getChildren() != null) {
            List<MenuTreeDTO> cs = new ArrayList<>();
            for (PortalMenu c : p.getChildren()) cs.add(toDto(c));
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
}
