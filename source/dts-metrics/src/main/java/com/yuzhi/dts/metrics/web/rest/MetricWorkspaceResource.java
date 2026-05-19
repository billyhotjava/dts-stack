package com.yuzhi.dts.metrics.web.rest;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics/workspace")
public class MetricWorkspaceResource {

    @GetMapping("/snapshot")
    public Map<String, Object> snapshot() {
        return object(
            "source",
            "dts-metrics-workspace",
            "generatedAt",
            Instant.now().toString(),
            "platformContracts",
            platformContracts(),
            "metricAssets",
            metricAssets(),
            "subjectMappings",
            subjectMappings(),
            "objectJoins",
            objectJoins(),
            "formulaBlocks",
            formulaBlocks(),
            "modelCandidates",
            modelCandidates(),
            "publishGates",
            publishGates(),
            "runRecords",
            runRecords(),
            "actions",
            actions()
        );
    }

    private static List<List<String>> platformContracts() {
        return List.of(
            List.of("login session", "platform-forward-auth", "connected"),
            List.of("asset permission", "/api/internal/asset-permission/check", "connected"),
            List.of("subject domain", "/api/internal/domains/resolve", "connected"),
            List.of("data standard", "/api/internal/data-standards/resolve", "connected"),
            List.of("glossary", "/api/internal/glossary/terms/resolve", "connected"),
            List.of("release gate", "/api/etl/dbt/release/submit", "pending integration")
        );
    }

    private static List<Map<String, Object>> metricAssets() {
        return List.of(
            object(
                "code",
                "project_cnt",
                "name",
                "项目总数",
                "domain",
                "项目管理",
                "type",
                "count_distinct",
                "grain",
                "月份 / 科室",
                "status",
                "PUBLISHED",
                "version",
                "1.0.0",
                "owner",
                "项目管理部",
                "terms",
                List.of("glossary.project"),
                "consumer",
                "项目驾驶舱"
            ),
            object(
                "code",
                "direct_cost_execution_rate",
                "name",
                "直接成本执行率",
                "domain",
                "项目管理",
                "type",
                "ratio",
                "grain",
                "月份 / 科室 / 项目",
                "status",
                "REVIEW",
                "version",
                "0.2.0",
                "owner",
                "财务管理部",
                "terms",
                List.of("glossary.direct_cost_execution_rate"),
                "consumer",
                "经营分析看板"
            ),
            object(
                "code",
                "overdue_project_cnt",
                "name",
                "延期项目数",
                "domain",
                "项目管理",
                "type",
                "count_if",
                "grain",
                "月份 / 科室",
                "status",
                "DRAFT",
                "version",
                "0.1.0",
                "owner",
                "项目管理部",
                "terms",
                List.of("glossary.project_risk"),
                "consumer",
                "风险预警列表"
            )
        );
    }

    private static List<Map<String, Object>> subjectMappings() {
        return List.of(
            object(
                "domain",
                "项目管理",
                "code",
                "project",
                "platformState",
                "ACTIVE",
                "assets",
                8,
                "metrics",
                14,
                "standards",
                "stat_month, dept_name, project_type",
                "gap",
                "缺少项目风险术语 owner"
            ),
            object(
                "domain",
                "采购管理",
                "code",
                "procurement",
                "platformState",
                "ACTIVE",
                "assets",
                5,
                "metrics",
                9,
                "standards",
                "supplier_id, supplier_name, stat_month",
                "gap",
                "准时交付口径待审核"
            )
        );
    }

    private static List<Map<String, Object>> objectJoins() {
        return List.of(
            object(
                "object",
                "项目",
                "source",
                "dwd_project_detail",
                "key",
                "project_id",
                "grain",
                "one row per project per month",
                "joins",
                List.of(
                    List.of("dwd_project_budget", "project_id", "left", "1:1"),
                    List.of("dwd_project_risk", "project_id", "left", "1:N pre-aggregate"),
                    List.of("dim_department", "dept_id", "left", "SCD-1")
                ),
                "guardrails",
                List.of("join key not null", "risk table pre-aggregated", "dept dimension conforms to platform standard")
            ),
            object(
                "object",
                "供应商",
                "source",
                "dwd_purchase_order_detail",
                "key",
                "supplier_id",
                "grain",
                "one row per supplier per month",
                "joins",
                List.of(List.of("dim_supplier", "supplier_id", "left", "SCD-2"), List.of("dwd_receive_detail", "po_id", "left", "N:1 aggregate")),
                "guardrails",
                List.of("supplier_id mapped to data standard", "late arrival handled by incremental window")
            )
        );
    }

    private static List<Map<String, Object>> formulaBlocks() {
        return List.of(
            object(
                "code",
                "project_cnt",
                "name",
                "项目总数",
                "display",
                "count_distinct(project_id)",
                "unit",
                "个",
                "format",
                "integer",
                "warning",
                "none",
                "dsl",
                "formula:\n  type: aggregation\n  aggregation: count_distinct\n  field: project_id"
            ),
            object(
                "code",
                "direct_cost_execution_rate",
                "name",
                "直接成本执行率",
                "display",
                "sum(direct_cost_amount) / sum(direct_cost_control_amount) * 100",
                "unit",
                "%",
                "format",
                "percent",
                "warning",
                ">= 90 标红",
                "dsl",
                "formula:\n  type: ratio\n  numerator:\n    type: aggregation\n    aggregation: sum\n    field: direct_cost_amount\n  denominator:\n    type: aggregation\n    aggregation: sum\n    field: direct_cost_control_amount\n  multiply: 100\n  zero_division: null"
            )
        );
    }

    private static List<Map<String, Object>> modelCandidates() {
        return List.of(
            object(
                "layer",
                "DWS",
                "name",
                "dws_project_month_summary",
                "purpose",
                "项目月度公共汇总模型，可复用于驾驶舱、科室看板和风险分析。",
                "grain",
                "stat_month + dept_name + project_type",
                "materialization",
                "incremental table",
                "refresh",
                "daily 02:30",
                "fields",
                List.of("stat_month", "dept_name", "project_type", "project_cnt", "overdue_project_cnt", "direct_cost_execution_rate"),
                "sql",
                "select\n  stat_month,\n  dept_name,\n  project_type,\n  count(distinct project_id) as project_cnt\nfrom {{ ref('dwd_project_detail') }}\ngroup by stat_month, dept_name, project_type"
            ),
            object(
                "layer",
                "ADS",
                "name",
                "ads_project_dashboard_overview",
                "purpose",
                "直接服务项目管理综合驾驶舱，减少 BI 工具二次 Join。",
                "grain",
                "stat_month + dashboard_scope",
                "materialization",
                "table",
                "refresh",
                "daily 03:00",
                "fields",
                List.of("project_cnt", "active_project_cnt", "overdue_project_cnt", "cost_warning_level", "top_dept_name"),
                "sql",
                "select\n  stat_month,\n  sum(project_cnt) as project_cnt,\n  sum(overdue_project_cnt) as overdue_project_cnt\nfrom {{ ref('dws_project_month_summary') }}\ngroup by stat_month"
            )
        );
    }

    private static List<List<String>> publishGates() {
        return List.of(
            List.of("结构校验", "PASS", "指标包 schema、依赖声明和文件引用通过"),
            List.of("平台权限", "PASS", "当前用户具备来源资产 READ 权限"),
            List.of("术语绑定", "PASS", "指标绑定的 glossary term 已在 platform 激活"),
            List.of("RLS 注入", "PASS", "生成 SQL 强制承接 platform 用户策略"),
            List.of("dbt 门禁", "PENDING", "等待提交 /api/etl/dbt/release/submit")
        );
    }

    private static List<List<String>> runRecords() {
        return List.of(
            List.of("dws_project_month_summary", "DWS", "SUCCESS", "2026-05-17 02:32", "48s", "fresh"),
            List.of("ads_project_dashboard_overview", "ADS", "SUCCESS", "2026-05-17 03:04", "23s", "fresh"),
            List.of("dws_supplier_month_summary", "DWS", "WARNING", "2026-05-17 02:41", "55s", "late source rows"),
            List.of("ads_inventory_risk_board", "ADS", "PENDING", "-", "-", "waiting for governance")
        );
    }

    private static Map<String, Object> actions() {
        return object(
            "capabilities",
            "/api/metrics/capabilities",
            "previewArtifacts",
            "/api/metrics/packs/preview-artifacts",
            "importDryRun",
            "/api/metrics/packs/import",
            "publishDryRun",
            "/api/metrics/packs/publish-dry-run",
            "migrationDryRun",
            "/api/metrics/migration/semantic-dry-run"
        );
    }

    private static Map<String, Object> object(Object... entries) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            result.put(String.valueOf(entries[i]), entries[i + 1]);
        }
        return result;
    }
}
