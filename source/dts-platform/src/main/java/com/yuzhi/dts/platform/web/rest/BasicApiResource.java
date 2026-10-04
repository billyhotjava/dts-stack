package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.menu.PortalMenuService;
import com.yuzhi.dts.platform.service.menu.PortalMenuService.PortalMenuFlatItem;
import com.yuzhi.dts.platform.service.menu.PortalMenuService.PortalMenuTreeNode;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Basic APIs used by the business portal frontend.
 * Provides a menu endpoint compatible with the platform webapp (flat list).
 */
@RestController
@RequestMapping("/api")
public class BasicApiResource {

    private final PortalMenuService portalMenuService;

    public BasicApiResource(PortalMenuService portalMenuService) {
        this.portalMenuService = portalMenuService;
    }

    /**
     * Return portal menus as a flat list compatible with Menu[] in the webapp.
     * Fields: id, parentId, name, code, order, type, path, component, icon, caption, info, disabled, auth, hidden
     */
    @GetMapping("/menu")
    public ApiResponse<List<PortalMenuFlatItem>> menu() {
        return ApiResponses.ok(portalMenuService.getFlatMenuList());
    }

    /**
     * Return portal menus as a hierarchical tree for clients that prefer nested data.
     */
    @GetMapping("/menu/tree")
    public ApiResponse<List<PortalMenuTreeNode>> menuTree() {
        return ApiResponses.ok(portalMenuService.getMenuTreeView());
    }
}
