package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.ClassificationUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Dashboards with visibility controlled by SecurityLevel.
 */
@RestController
@RequestMapping("/api")
@Transactional
public class DashboardResource {

    private final ClassificationUtils classificationUtils;
    private final AuditService audit;

    public DashboardResource(ClassificationUtils classificationUtils, AuditService audit) {
        this.classificationUtils = classificationUtils;
        this.audit = audit;
    }

    @GetMapping("/dashboards")
    public ApiResponse<List<Map<String, Object>>> list() {
        // Example dashboards
        List<Map<String, Object>> all = new ArrayList<>();
        all.add(dashboard("biz-overview", "业务总览", "PUBLIC", "/superset/dashboard/1"));
        all.add(dashboard("finance", "财务看板", "INTERNAL", "/superset/dashboard/2"));
        all.add(dashboard("risk", "风控看板", "SECRET", "/superset/dashboard/3"));
        all.add(dashboard("ceo", "CEO驾驶舱", "TOP_SECRET", "/superset/dashboard/4"));

        String max = classificationUtils.getCurrentUserMaxLevel();
        List<Map<String, Object>> visible = all
            .stream()
            .filter(d -> classificationUtils.canAccess((String) d.get("level")))
            .toList();
        audit.audit("READ", "dashboard.list", "visible=" + visible.size());
        return ApiResponses.ok(visible);
    }

    private Map<String, Object> dashboard(String code, String name, String level, String url) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("name", name);
        m.put("level", level);
        m.put("url", url);
        return m;
    }
}

