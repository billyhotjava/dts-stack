package com.yuzhi.dts.copilot.analytics.service.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsReportTemplate;
import org.junit.jupiter.api.Test;

class ReportExecutionPlanServiceTest {

    private final AuthorityQueryService authorityQueryService = new AuthorityQueryService();
    private final ReportExecutionPlanService service = new ReportExecutionPlanService(authorityQueryService);

    @Test
    void financeStateAndDetailTemplatesShouldRouteToAuthorityViewOrSql() {
        AnalyticsReportTemplate authorityViewTemplate = template(
                "FIN-AR-OVERVIEW",
                "财务",
                "KPI",
                "VIEW",
                "v_monthly_settlement",
                "REALTIME");
        AnalyticsReportTemplate authoritySqlTemplate = template(
                "FIN-PENDING-RECEIPTS-DETAIL",
                "财务",
                "DETAIL",
                "VIEW",
                "authority.finance.pending_receipts_detail",
                "REALTIME");

        ReportExecutionPlanService.ReportExecutionPlan viewPlan = service.planFor(authorityViewTemplate);
        ReportExecutionPlanService.ReportExecutionPlan sqlPlan = service.planFor(authoritySqlTemplate);

        assertThat(viewPlan.route()).isEqualTo(ReportExecutionPlanService.Route.AUTHORITY_VIEW);
        assertThat(viewPlan.targetObject()).isEqualTo("v_monthly_settlement");
        assertThat(sqlPlan.route()).isEqualTo(ReportExecutionPlanService.Route.AUTHORITY_SQL);
        assertThat(sqlPlan.targetObject()).isEqualTo("authority.finance.pending_receipts_detail");
    }

    @Test
    void financeSettlementSummaryTemplateShouldUseAuthoritySqlEvenWhenRealtime() {
        AnalyticsReportTemplate financeSettlementSummaryTemplate = template(
                "FIN-AR-OVERVIEW",
                "财务",
                "DETAIL",
                "SQL",
                "authority.finance.settlement_summary",
                "REALTIME");

        ReportExecutionPlanService.ReportExecutionPlan plan = service.planFor(financeSettlementSummaryTemplate);

        assertThat(plan.route()).isEqualTo(ReportExecutionPlanService.Route.AUTHORITY_SQL);
        assertThat(plan.adapterKey()).isEqualTo("authority.finance");
        assertThat(plan.targetObject()).isEqualTo("authority.finance.settlement_summary");
    }

    @Test
    void financeReimbursementStatusTemplateShouldUseAuthoritySqlEvenWhenRealtime() {
        AnalyticsReportTemplate financeReimbursementTemplate = template(
                "FIN-REIMBURSEMENT-STATUS",
                "财务",
                "状态",
                "SQL",
                "authority.finance.reimbursement_status",
                "REALTIME");

        ReportExecutionPlanService.ReportExecutionPlan plan = service.planFor(financeReimbursementTemplate);

        assertThat(plan.route()).isEqualTo(ReportExecutionPlanService.Route.AUTHORITY_SQL);
        assertThat(plan.adapterKey()).isEqualTo("authority.finance");
        assertThat(plan.targetObject()).isEqualTo("authority.finance.reimbursement_status");
    }

    @Test
    void financeInvoiceReconciliationTemplateShouldUseAuthoritySqlEvenWhenRealtime() {
        AnalyticsReportTemplate financeInvoiceTemplate = template(
                "FIN-INVOICE-RECONCILIATION",
                "财务",
                "对账",
                "SQL",
                "authority.finance.invoice_reconciliation",
                "REALTIME");

        ReportExecutionPlanService.ReportExecutionPlan plan = service.planFor(financeInvoiceTemplate);

        assertThat(plan.route()).isEqualTo(ReportExecutionPlanService.Route.AUTHORITY_SQL);
        assertThat(plan.adapterKey()).isEqualTo("authority.finance");
        assertThat(plan.targetObject()).isEqualTo("authority.finance.invoice_reconciliation");
    }

    @Test
    void procurementAndWarehouseRealtimeStateTemplatesShouldPreferAuthorityViews() {
        AnalyticsReportTemplate procurementRealtimeTemplate = template(
                "PROC-PURCHASE-REQUEST-TODO",
                "采购",
                "DETAIL",
                "VIEW",
                "v_procurement_todo",
                "REALTIME");
        AnalyticsReportTemplate inventoryRealtimeTemplate = template(
                "WH-STOCK-OVERVIEW",
                "仓库",
                "KPI",
                "VIEW",
                "v_inventory_current",
                "REALTIME");
        AnalyticsReportTemplate procurementFallbackTemplate = template(
                "PROC-PENDING-INBOUND-LIST",
                "采购",
                "DETAIL",
                "VIEW",
                "authority.procurement.pending_inbound_list",
                "NEAR_REALTIME_5M");

        ReportExecutionPlanService.ReportExecutionPlan procurementRealtimePlan =
                service.planFor(procurementRealtimeTemplate);
        ReportExecutionPlanService.ReportExecutionPlan inventoryRealtimePlan =
                service.planFor(inventoryRealtimeTemplate);
        ReportExecutionPlanService.ReportExecutionPlan procurementFallbackPlan =
                service.planFor(procurementFallbackTemplate);

        assertThat(procurementRealtimePlan.route()).isEqualTo(ReportExecutionPlanService.Route.AUTHORITY_VIEW);
        assertThat(procurementRealtimePlan.adapterKey()).isEqualTo("authority.procurement");
        assertThat(inventoryRealtimePlan.route()).isEqualTo(ReportExecutionPlanService.Route.AUTHORITY_VIEW);
        assertThat(inventoryRealtimePlan.adapterKey()).isEqualTo("authority.inventory");
        assertThat(procurementFallbackPlan.route()).isEqualTo(ReportExecutionPlanService.Route.AUTHORITY_SQL);
    }

    @Test
    void procurementSummaryTemplateShouldUseAuthoritySqlEvenWhenRealtime() {
        AnalyticsReportTemplate procurementSummaryTemplate = template(
                "PROC-SUPPLIER-AMOUNT-RANK",
                "采购",
                "DETAIL",
                "SQL",
                "authority.procurement.purchase_summary",
                "REALTIME");

        ReportExecutionPlanService.ReportExecutionPlan plan = service.planFor(procurementSummaryTemplate);

        assertThat(plan.route()).isEqualTo(ReportExecutionPlanService.Route.AUTHORITY_SQL);
        assertThat(plan.adapterKey()).isEqualTo("authority.procurement");
        assertThat(plan.targetObject()).isEqualTo("authority.procurement.purchase_summary");
    }

    @Test
    void procurementOrderExecutionProgressTemplateShouldUseAuthoritySqlEvenWhenRealtime() {
        AnalyticsReportTemplate procurementDetailTemplate = template(
                "PROC-ORDER-EXECUTION-PROGRESS",
                "采购",
                "明细",
                "SQL",
                "authority.procurement.order_execution_progress",
                "REALTIME");

        ReportExecutionPlanService.ReportExecutionPlan plan = service.planFor(procurementDetailTemplate);

        assertThat(plan.route()).isEqualTo(ReportExecutionPlanService.Route.AUTHORITY_SQL);
        assertThat(plan.adapterKey()).isEqualTo("authority.procurement");
        assertThat(plan.targetObject()).isEqualTo("authority.procurement.order_execution_progress");
    }

    @Test
    void warehouseStockOverviewTemplateShouldUseAuthoritySqlEvenWhenRealtime() {
        AnalyticsReportTemplate warehouseStockOverviewTemplate = template(
                "WH-STOCK-OVERVIEW",
                "仓库",
                "DETAIL",
                "SQL",
                "authority.inventory.stock_overview",
                "REALTIME");

        ReportExecutionPlanService.ReportExecutionPlan plan = service.planFor(warehouseStockOverviewTemplate);

        assertThat(plan.route()).isEqualTo(ReportExecutionPlanService.Route.AUTHORITY_SQL);
        assertThat(plan.adapterKey()).isEqualTo("authority.inventory");
        assertThat(plan.targetObject()).isEqualTo("authority.inventory.stock_overview");
    }

    @Test
    void warehouseLowStockAlertTemplateShouldUseAuthoritySqlEvenWhenRealtime() {
        AnalyticsReportTemplate warehouseLowStockAlertTemplate = template(
                "WH-LOW-STOCK-ALERT",
                "仓库",
                "预警",
                "SQL",
                "authority.inventory.low_stock_alert",
                "REALTIME");

        ReportExecutionPlanService.ReportExecutionPlan plan = service.planFor(warehouseLowStockAlertTemplate);

        assertThat(plan.route()).isEqualTo(ReportExecutionPlanService.Route.AUTHORITY_SQL);
        assertThat(plan.adapterKey()).isEqualTo("authority.inventory");
        assertThat(plan.targetObject()).isEqualTo("authority.inventory.low_stock_alert");
    }

    @Test
    void trendAndRankingTemplatesShouldRouteToMartOrCachePlans() {
        AnalyticsReportTemplate martTemplate = template(
                "FIN-CUSTOMER-AR-RANK",
                "财务",
                "RANK",
                "MART",
                "mart.finance.customer_ar_rank_daily",
                "NEAR_REALTIME_5M");
        AnalyticsReportTemplate cacheTemplate = template(
                "PROC-ARRIVAL-ONTIME-RATE",
                "采购",
                "TREND",
                "CACHE",
                "cache.procurement.arrival_ontime_rate",
                "CACHE_1M");

        ReportExecutionPlanService.ReportExecutionPlan martPlan = service.planFor(martTemplate);
        ReportExecutionPlanService.ReportExecutionPlan cachedPlan = service.planFor(cacheTemplate);

        assertThat(martPlan.route()).isEqualTo(ReportExecutionPlanService.Route.MART_FACT);
        assertThat(martPlan.targetObject()).isEqualTo("mart.finance.customer_ar_rank_daily");
        assertThat(cachedPlan.route()).isEqualTo(ReportExecutionPlanService.Route.CACHE);
        assertThat(cachedPlan.targetObject()).isEqualTo("cache.procurement.arrival_ontime_rate");
    }

    @Test
    void dbtScreenTemplatesShouldRouteToTableBackedReportExecution() {
        AnalyticsReportTemplate template = template(
                "PRS-FLOWERBIZ-OVERVIEW",
                "PRS租赁",
                "总览",
                "DBT_SCREEN",
                "screen.prs-flowerbiz-overview-v1",
                "DBT_BUILD");

        ReportExecutionPlanService.ReportExecutionPlan plan = service.planFor(template);

        assertThat(plan.route()).isEqualTo(ReportExecutionPlanService.Route.MART_FACT);
        assertThat(plan.adapterKey()).isEqualTo("dbt.screen");
        assertThat(plan.targetObject()).isEqualTo("screen.prs-flowerbiz-overview-v1");
    }

    @Test
    void unknownTemplatesShouldFallBackToExploration() {
        AnalyticsReportTemplate template = template(
                "OPS-UNKNOWN",
                "运营",
                "其他",
                "VIEW",
                "authority.ops.unknown",
                "REALTIME");

        ReportExecutionPlanService.ReportExecutionPlan plan = service.planFor(template);

        assertThat(plan.route()).isEqualTo(ReportExecutionPlanService.Route.EXPLORATION);
    }

    private static AnalyticsReportTemplate template(
            String templateCode,
            String domain,
            String category,
            String dataSourceType,
            String targetObject,
            String refreshPolicy) {
        AnalyticsReportTemplate template = new AnalyticsReportTemplate();
        template.setTemplateCode(templateCode);
        template.setName(templateCode);
        template.setDomain(domain);
        template.setCategory(category);
        template.setDataSourceType(dataSourceType);
        template.setTargetObject(targetObject);
        template.setRefreshPolicy(refreshPolicy);
        template.setCertificationStatus("CERTIFIED");
        template.setPublished(true);
        template.setArchived(false);
        template.setCreatorId(1L);
        template.setSpecJson("{}");
        return template;
    }
}
