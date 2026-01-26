package com.yuzhi.dts.admin.web.rest;

import com.yuzhi.dts.admin.domain.ChangeRequest;
import com.yuzhi.dts.admin.security.AuthoritiesConstants;
import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.admin.service.ops.OpsConfigService;
import com.yuzhi.dts.admin.service.ops.OpsConfigView;
import com.yuzhi.dts.admin.web.rest.vm.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运维配置 REST API
 * 
 * 提供运维配置的查看和管理功能，包含：
 * - 配置分类展示（脱敏处理敏感信息）
 * - 功能开关管理
 * - 所有操作记录审计日志
 */
@RestController
@RequestMapping("/api/admin/ops")
@io.swagger.v3.oas.annotations.tags.Tag(name = "ops-config", description = "运维配置管理")
public class OpsResource {

    private final OpsConfigService opsConfigService;

    public OpsResource(OpsConfigService opsConfigService) {
        this.opsConfigService = opsConfigService;
    }

    /**
     * 获取所有配置（按分类分组，敏感值脱敏）
     */
    @GetMapping("/configs")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.SYS_ADMIN + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getConfigs(HttpServletRequest request) {
        Map<String, List<OpsConfigView>> configsByCategory = opsConfigService.getConfigsByCategory();
        
        // 计算总数用于审计
        int totalCount = configsByCategory.values().stream()
            .mapToInt(List::size)
            .sum();

        // 记录查看审计
        String actor = SecurityUtils.getCurrentAuditableLogin();
        opsConfigService.recordViewAudit(actor, "configs", totalCount, request);

        // 构建响应（包含分类名称）
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, String> categoryLabels = Map.of(
            "FEATURE_TOGGLE", "功能开关",
            "SECURITY", "安全配置",
            "DATABASE", "数据库配置",
            "INTEGRATION", "集成配置",
            "SYSTEM", "系统配置"
        );
        
        List<Map<String, Object>> categories = configsByCategory.entrySet().stream()
            .map(entry -> {
                Map<String, Object> category = new LinkedHashMap<>();
                category.put("key", entry.getKey());
                category.put("label", categoryLabels.getOrDefault(entry.getKey(), entry.getKey()));
                category.put("items", entry.getValue());
                return category;
            })
            .toList();

        result.put("categories", categories);
        result.put("total", totalCount);

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 获取单个配置项
     */
    @GetMapping("/configs/{key}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.SYS_ADMIN + "')")
    public ResponseEntity<ApiResponse<OpsConfigView>> getConfig(
        @PathVariable String key,
        HttpServletRequest request
    ) {
        return opsConfigService.getConfig(key)
            .map(config -> ResponseEntity.ok(ApiResponse.ok(config)))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 更新配置项（触发审批流程）
     */
    @PutMapping("/configs/{key}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.SYS_ADMIN + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateConfig(
        @PathVariable String key,
        @RequestBody Map<String, Object> payload,
        HttpServletRequest request
    ) {
        String value = payload.get("value") != null ? String.valueOf(payload.get("value")) : null;
        String reason = payload.get("reason") != null ? String.valueOf(payload.get("reason")) : null;

        if (value == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("配置值不能为空"));
        }

        try {
            ChangeRequest cr = opsConfigService.updateConfig(key, value, reason, request);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("changeRequestId", cr.getId());
            result.put("status", cr.getStatus());
            result.put("message", "配置变更已提交审批");
            return ResponseEntity.accepted().body(ApiResponse.ok(result));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(409).body(ApiResponse.error(ex.getMessage()));
        }
    }

    /**
     * 获取功能开关列表
     */
    @GetMapping("/feature-toggles")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.SYS_ADMIN + "')")
    public ResponseEntity<ApiResponse<List<OpsConfigView>>> getFeatureToggles(HttpServletRequest request) {
        List<OpsConfigView> toggles = opsConfigService.getFeatureToggles();

        // 记录查看审计
        String actor = SecurityUtils.getCurrentAuditableLogin();
        opsConfigService.recordViewAudit(actor, "feature-toggles", toggles.size(), request);

        return ResponseEntity.ok(ApiResponse.ok(toggles));
    }

    /**
     * 切换功能开关（触发审批流程）
     */
    @PutMapping("/feature-toggles/{key}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.SYS_ADMIN + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleFeature(
        @PathVariable String key,
        @RequestBody Map<String, Object> payload,
        HttpServletRequest request
    ) {
        Object valueObj = payload.get("value");
        if (valueObj == null) {
            valueObj = payload.get("enabled");
        }
        
        boolean enabled;
        if (valueObj instanceof Boolean b) {
            enabled = b;
        } else if (valueObj instanceof String s) {
            enabled = "true".equalsIgnoreCase(s) || "1".equals(s);
        } else if (valueObj instanceof Number n) {
            enabled = n.intValue() != 0;
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error("请提供 value 或 enabled 参数"));
        }

        try {
            ChangeRequest cr = opsConfigService.toggleFeature(key, enabled, request);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("changeRequestId", cr.getId());
            result.put("status", cr.getStatus());
            result.put("message", enabled ? "功能启用已提交审批" : "功能禁用已提交审批");
            return ResponseEntity.accepted().body(ApiResponse.ok(result));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(409).body(ApiResponse.error(ex.getMessage()));
        }
    }
}
