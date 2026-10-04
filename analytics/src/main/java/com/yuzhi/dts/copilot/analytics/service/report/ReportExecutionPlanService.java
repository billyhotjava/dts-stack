package com.yuzhi.dts.copilot.analytics.service.report;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsReportTemplate;
import java.util.Set;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class ReportExecutionPlanService {

    private static final Set<String> STATE_DETAIL_CATEGORIES =
            Set.of("看板", "明细", "待办", "状态", "进度", "对账", "kpi", "detail", "todo", "status", "progress", "reconciliation");
    private static final Set<String> TREND_RANK_CATEGORIES =
            Set.of("趋势", "排行", "trend", "rank");

    private final AuthorityQueryService authorityQueryService;

    public ReportExecutionPlanService(AuthorityQueryService authorityQueryService) {
        this.authorityQueryService = authorityQueryService;
    }

    public ReportExecutionPlan planFor(AnalyticsReportTemplate template) {
        if (template == null) {
            return ReportExecutionPlan.exploration(null, null, null, null);
        }

        String domain = normalize(template.getDomain());
        String category = normalize(template.getCategory());
        String dataSourceType = normalize(template.getDataSourceType());
        String refreshPolicy = normalize(template.getRefreshPolicy());
        String targetObject = normalize(template.getTargetObject());
        String templateCode = trimToNull(template.getTemplateCode());
        String displayTargetObject = trimToNull(template.getTargetObject());
        String displayDataSourceType = trimToNull(template.getDataSourceType());
        String displayRefreshPolicy = trimToNull(template.getRefreshPolicy());

        if (isDbtScreenTarget(dataSourceType, targetObject)) {
            return new ReportExecutionPlan(
                    Route.MART_FACT,
                    templateCode,
                    "dbt.screen",
                    displayTargetObject,
                    "template uses dbt screen table route",
                    displayDataSourceType,
                    displayRefreshPolicy);
        }

        if (isTrendOrRank(category) || isMartOrFactTarget(dataSourceType, targetObject)) {
            if (isCacheRoute(refreshPolicy, dataSourceType, targetObject)) {
                return new ReportExecutionPlan(
                        Route.CACHE,
                        templateCode,
                        "cache",
                        displayTargetObject,
                        "template uses cache or cache-like refresh policy",
                        displayDataSourceType,
                        displayRefreshPolicy);
            }
            return new ReportExecutionPlan(
                    Route.MART_FACT,
                    templateCode,
                    "mart.fact",
                    displayTargetObject,
                    "template uses mart/fact route",
                    displayDataSourceType,
                    displayRefreshPolicy);
        }

        if (isAuthorityDomain(domain) && isStateOrDetail(category, targetObject)) {
            AuthorityQueryService.AuthorityAdapter adapter = authorityQueryService.resolve(template);
            if (adapter.route() != Route.EXPLORATION) {
                return new ReportExecutionPlan(
                        adapter.route(),
                        templateCode,
                        adapter.adapterKey(),
                        adapter.targetObject(),
                        adapter.route() == Route.AUTHORITY_VIEW
                                ? "template uses realtime authority view"
                                : "template uses authority sql fallback",
                        displayDataSourceType,
                        displayRefreshPolicy);
            }
        }

        return ReportExecutionPlan.exploration(
                templateCode,
                displayTargetObject,
                displayDataSourceType,
                "template not matched to fixed report route");
    }

    private static boolean isAuthorityDomain(String domain) {
        return "财务".equals(domain)
                || "采购".equals(domain)
                || "仓库".equals(domain)
                || domain.contains("finance")
                || domain.contains("procurement")
                || domain.contains("warehouse")
                || domain.contains("inventory");
    }

    private static boolean isStateOrDetail(String category, String targetObject) {
        return STATE_DETAIL_CATEGORIES.contains(category)
                || targetObject.startsWith("authority.")
                || targetObject.startsWith("v_");
    }

    private static boolean isTrendOrRank(String category) {
        return TREND_RANK_CATEGORIES.contains(category);
    }

    private static boolean isMartOrFactTarget(String dataSourceType, String targetObject) {
        return "mart".equals(dataSourceType)
                || "fact".equals(dataSourceType)
                || targetObject.startsWith("mart.")
                || targetObject.startsWith("fact.");
    }

    private static boolean isDbtScreenTarget(String dataSourceType, String targetObject) {
        return "dbt_screen".equals(dataSourceType)
                || "dbt".equals(dataSourceType)
                || dataSourceType.startsWith("dbt_")
                || targetObject.startsWith("screen.");
    }

    private static boolean isCacheRoute(String refreshPolicy, String dataSourceType, String targetObject) {
        return dataSourceType.startsWith("cache")
                || refreshPolicy.contains("cache")
                || targetObject.startsWith("cache.");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public enum Route {
        AUTHORITY_VIEW,
        AUTHORITY_SQL,
        MART_FACT,
        CACHE,
        EXPLORATION
    }

    public record ReportExecutionPlan(
            Route route,
            String templateCode,
            String adapterKey,
            String targetObject,
            String rationale,
            String dataSourceType,
            String refreshPolicy) {
        private static ReportExecutionPlan exploration(
                String templateCode, String targetObject, String dataSourceType, String rationale) {
            return new ReportExecutionPlan(
                    Route.EXPLORATION, templateCode, null, targetObject, rationale, dataSourceType, null);
        }
    }
}
