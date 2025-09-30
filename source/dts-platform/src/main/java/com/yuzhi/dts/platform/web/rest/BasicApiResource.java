package com.yuzhi.dts.platform.web.rest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Return portal menus as a flat list compatible with Menu[] in the webapp.
     * Fields: id, parentId, name, code, order, type, path, component, icon, caption, info, disabled, auth, hidden
     */
    @GetMapping("/menu")
    public ApiResponse<List<Map<String, Object>>> menu() {
        return ApiResponses.ok(MenuData.flat());
    }

    /**
     * In-memory demo menu data provider.
     * Keep structure minimal but aligned to frontend expectations.
     */
    static final class MenuData {
        private static List<Map<String, Object>> FLAT;

        static synchronized List<Map<String, Object>> flat() {
            if (FLAT != null) return FLAT;
            List<Map<String, Object>> out = new ArrayList<>();

            // Group: dashboard
            out.add(menu(
                "group_dashboard",
                "",
                "sys.nav.dashboard",
                "dashboard",
                0,
                0,
                null,
                null,
                null
            ));

            // Workbench under dashboard
            out.add(menu(
                "workbench",
                "group_dashboard",
                "sys.nav.workbench",
                "workbench",
                10,
                2,
                "/workbench",
                "/pages/dashboard/workbench",
                "local:ic-workbench"
            ));

            // Data security
            out.add(menu(
                "data_security",
                "group_dashboard",
                "sys.nav.dataSecurity",
                "security",
                20,
                2,
                "/security/assets",
                "/pages/security/data-security",
                "solar:shield-keyhole-bold"
            ));

            // Catalogue: management
            out.add(menu(
                "management",
                "group_pages",
                "sys.nav.management",
                "management",
                0,
                1,
                "/management",
                null,
                "local:ic-management"
            ));

            // Management -> system
            out.add(menu(
                "management_system",
                "management",
                "sys.nav.system.index",
                "management:system",
                0,
                1,
                "management/system",
                null,
                null
            ));

            // Management -> system -> audit-log
            out.add(menu(
                "management_system_audit_log",
                "management_system",
                "sys.nav.system.audit_log",
                "management:system:audit_log",
                0,
                2,
                "/management/system/audit-log",
                "/pages/management/system/audit-log/index",
                null
            ));

            FLAT = out;
            return FLAT;
        }

        private static Map<String, Object> menu(
            String id,
            String parentId,
            String name,
            String code,
            int order,
            int type,
            String path,
            String component,
            String icon
        ) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("parentId", parentId);
            m.put("name", name);
            m.put("code", code);
            m.put("order", order);
            m.put("type", type);
            if (path != null) m.put("path", path);
            if (component != null) m.put("component", component);
            if (icon != null) m.put("icon", icon);
            return m;
        }
    }
}

