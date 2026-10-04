package com.yuzhi.dts.copilot.analytics.service.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDatabaseRole;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsReportTemplate;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsDatabaseRepository;
import com.yuzhi.dts.copilot.analytics.service.DatasetQueryService;
import com.yuzhi.dts.copilot.analytics.service.DatasetQueryService.DatasetConstraints;
import com.yuzhi.dts.copilot.analytics.service.DatasetQueryService.DatasetResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultFixedReportExecutionServiceTest {

    @Mock
    private AnalyticsDatabaseRepository databaseRepository;

    @Mock
    private DatasetQueryService datasetQueryService;

    @Captor
    private ArgumentCaptor<String> sqlCaptor;

    @Captor
    private ArgumentCaptor<List<Object>> bindingsCaptor;

    @Test
    void shouldExecuteDbtScreenTemplateAsTableBackedFixedReport() throws Exception {
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setId(8L);
        database.setName("DTS dbt模型库");
        database.setDatabaseRole(AnalyticsDatabaseRole.BUSINESS_SECONDARY);
        when(databaseRepository.findFirstByNameIgnoreCase("DTS dbt模型库")).thenReturn(Optional.of(database));
        when(datasetQueryService.runNative(eq(8L), any(String.class), any(DatasetConstraints.class), any()))
                .thenReturn(new DatasetResult(
                        List.of(List.of(2025, 5, "国贸项目", "客户A", new BigDecimal("12000.00"))),
                        List.of(
                                Map.of("name", "年份", "display_name", "年份", "base_type", "type/Integer"),
                                Map.of("name", "业务月份", "display_name", "业务月份", "base_type", "type/Integer"),
                                Map.of("name", "项目", "display_name", "项目", "base_type", "type/Text"),
                                Map.of("name", "客户", "display_name", "客户", "base_type", "type/Text"),
                                Map.of("name", "租金净额全口径", "display_name", "租金净额全口径", "base_type", "type/Decimal")),
                        List.of(),
                        "Asia/Shanghai"));

        DefaultFixedReportExecutionService service =
                new DefaultFixedReportExecutionService(databaseRepository, datasetQueryService);

        Optional<FixedReportExecutionService.ExecutionResult> result = service.execute(
                prsDbtScreenTemplate(),
                Map.of("dateFrom", "2025-05-01", "projectName", "国贸"),
                new ReportExecutionPlanService.ReportExecutionPlan(
                        ReportExecutionPlanService.Route.MART_FACT,
                        "PRS-FLOWERBIZ-OVERVIEW",
                        "dbt.screen",
                        "screen.prs-flowerbiz-overview-v1",
                        "template uses dbt screen table route",
                        "DBT_SCREEN",
                        "DBT_BUILD"));

        assertThat(result).isPresent();
        assertThat(result.get().databaseId()).isEqualTo(8L);
        assertThat(result.get().columns()).extracting(FixedReportExecutionService.PreviewColumn::key)
                .containsExactly("年份", "业务月份", "项目", "客户", "租金净额全口径");
        assertThat(result.get().rows()).containsExactly(Map.of(
                "年份", 2025,
                "业务月份", 5,
                "项目", "国贸项目",
                "客户", "客户A",
                "租金净额全口径", new BigDecimal("12000.00")));

        verify(datasetQueryService).runNative(eq(8L), sqlCaptor.capture(), any(DatasetConstraints.class), bindingsCaptor.capture());
	        assertThat(sqlCaptor.getValue()).contains("FROM public.xycyl_ads_flowerbiz_overview");
	        assertThat(sqlCaptor.getValue()).contains("SELECT \"年份\", \"业务月份\", \"项目\", \"客户\", \"租金净额全口径\"");
	        assertThat(sqlCaptor.getValue()).contains("ORDER BY \"租金净额全口径\" DESC NULLS LAST");
	        assertThat(sqlCaptor.getValue()).contains("LIMIT 50");
	        assertThat(sqlCaptor.getValue()).doesNotContain("screen.prs-flowerbiz-overview-v1");
	        assertThat(bindingsCaptor.getValue()).isEmpty();
    }

    @Test
    void shouldExecuteProcurementSummaryAgainstConfiguredBusinessDatabase() throws Exception {
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setId(7L);
        database.setName("园林业务库");
        when(databaseRepository.findFirstByNameIgnoreCase("园林业务库")).thenReturn(Optional.of(database));
        when(datasetQueryService.runNative(eq(7L), any(String.class), any(DatasetConstraints.class), any()))
                .thenReturn(new DatasetResult(
                        List.of(List.of("2025-02-28", "邹顿顿", new BigDecimal("11979.50"))),
                        List.of(
                                Map.of("name", "purchaseDate", "display_name", "采购时间", "base_type", "type/Date"),
                                Map.of("name", "purchaseUserName", "display_name", "采购人", "base_type", "type/Text"),
                                Map.of("name", "totalAmount", "display_name", "总金额", "base_type", "type/Number")),
                        List.of(),
                        "Asia/Shanghai"));

        DefaultFixedReportExecutionService service =
                new DefaultFixedReportExecutionService(databaseRepository, datasetQueryService);

        Optional<FixedReportExecutionService.ExecutionResult> result = service.execute(
                procurementSummaryTemplate(),
                Map.of(
                        "purchaseUserId", "42",
                        "startDate", "2025-02-01",
                        "endDate", "2025-02-28"),
                new ReportExecutionPlanService.ReportExecutionPlan(
                        ReportExecutionPlanService.Route.AUTHORITY_SQL,
                        "PROC-SUPPLIER-AMOUNT-RANK",
                        "authority.procurement",
                        "authority.procurement.purchase_summary",
                        "template uses realtime procurement summary sql",
                        "SQL",
                        "REALTIME"));

        assertThat(result).isPresent();
        assertThat(result.get().databaseId()).isEqualTo(7L);
        assertThat(result.get().databaseName()).isEqualTo("园林业务库");
        assertThat(result.get().rowCount()).isEqualTo(1);
        assertThat(result.get().columns()).extracting(FixedReportExecutionService.PreviewColumn::key)
                .containsExactly("purchaseDate", "purchaseUserName", "totalAmount");
        assertThat(result.get().rows()).containsExactly(Map.of(
                "purchaseDate", "2025-02-28",
                "purchaseUserName", "邹顿顿",
                "totalAmount", new BigDecimal("11979.50")));

        verify(datasetQueryService).runNative(eq(7L), sqlCaptor.capture(), any(DatasetConstraints.class), bindingsCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("FROM t_purchase_price_item a");
        assertThat(sqlCaptor.getValue()).contains("GROUP BY DATE_FORMAT( a.purchase_time, '%Y-%m-%d' ), b.purchase_user_id, b.purchase_user_name");
        assertThat(bindingsCaptor.getValue()).containsExactly("42", "2025-02-01", "2025-02-28");
    }

    @Test
    void shouldExecuteProcurementOrderExecutionProgressAgainstConfiguredBusinessDatabase() throws Exception {
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setId(7L);
        database.setName("园林业务库");
        when(databaseRepository.findFirstByNameIgnoreCase("园林业务库")).thenReturn(Optional.of(database));
        when(datasetQueryService.runNative(eq(7L), any(String.class), any(DatasetConstraints.class), any()))
                .thenReturn(new DatasetResult(
                        List.of(List.of(
                                "王果",
                                "2025-05-12 00:00:00",
                                "蝴蝶兰",
                                "描述:双剑#规格:0.5m",
                                1,
                                "盆",
                                new BigDecimal("28.0000"),
                                new BigDecimal("28.0000"),
                                "挂账",
                                "帅启高",
                                "换2025050083",
                                "特种公司DB21")),
                        List.of(
                                Map.of("name", "purchaseUserName", "display_name", "采购人", "base_type", "type/Text"),
                                Map.of("name", "purchaseTime", "display_name", "采购时间", "base_type", "type/Text"),
                                Map.of("name", "goodName", "display_name", "物品名称", "base_type", "type/Text"),
                                Map.of("name", "goodSpecs", "display_name", "物品属性", "base_type", "type/Text"),
                                Map.of("name", "parchaseNumber", "display_name", "采购数量", "base_type", "type/Number"),
                                Map.of("name", "goodUnit", "display_name", "单位", "base_type", "type/Text"),
                                Map.of("name", "parchasePrice", "display_name", "采购单价", "base_type", "type/Number"),
                                Map.of("name", "totalPrice", "display_name", "采购总价", "base_type", "type/Number"),
                                Map.of("name", "payTypeName", "display_name", "结算方式", "base_type", "type/Text"),
                                Map.of("name", "supplyName", "display_name", "供应商", "base_type", "type/Text"),
                                Map.of("name", "bizCode", "display_name", "关联单据", "base_type", "type/Text"),
                                Map.of("name", "projectName", "display_name", "所属项目点", "base_type", "type/Text")),
                        List.of(),
                        "Asia/Shanghai"));

        DefaultFixedReportExecutionService service =
                new DefaultFixedReportExecutionService(databaseRepository, datasetQueryService);

        Optional<FixedReportExecutionService.ExecutionResult> result = service.execute(
                procurementOrderExecutionProgressTemplate(),
                Map.of(
                        "projectId", "3001",
                        "purchaseUserId", "42",
                        "payType", "2",
                        "startTime", "2025-05-01",
                        "endTime", "2025-05-31",
                        "goodName", "蝴蝶兰",
                        "goodSpecs", "0.5m",
                        "supplyName", "帅启高",
                        "bizCode", "换2025050083"),
                new ReportExecutionPlanService.ReportExecutionPlan(
                        ReportExecutionPlanService.Route.AUTHORITY_SQL,
                        "PROC-ORDER-EXECUTION-PROGRESS",
                        "authority.procurement",
                        "authority.procurement.order_execution_progress",
                        "template uses realtime procurement detail sql",
                        "SQL",
                        "REALTIME"));

        assertThat(result).isPresent();
        assertThat(result.get().databaseId()).isEqualTo(7L);
        assertThat(result.get().databaseName()).isEqualTo("园林业务库");
        assertThat(result.get().rowCount()).isEqualTo(1);
        assertThat(result.get().columns()).extracting(FixedReportExecutionService.PreviewColumn::key)
                .containsExactly(
                        "purchaseUserName",
                        "purchaseTime",
                        "goodName",
                        "goodSpecs",
                        "parchaseNumber",
                        "goodUnit",
                        "parchasePrice",
                        "totalPrice",
                        "payTypeName",
                        "supplyName",
                        "bizCode",
                        "projectName");
        assertThat(result.get().rows()).containsExactly(Map.ofEntries(
                Map.entry("purchaseUserName", "王果"),
                Map.entry("purchaseTime", "2025-05-12 00:00:00"),
                Map.entry("goodName", "蝴蝶兰"),
                Map.entry("goodSpecs", "描述:双剑#规格:0.5m"),
                Map.entry("parchaseNumber", 1),
                Map.entry("goodUnit", "盆"),
                Map.entry("parchasePrice", new BigDecimal("28.0000")),
                Map.entry("totalPrice", new BigDecimal("28.0000")),
                Map.entry("payTypeName", "挂账"),
                Map.entry("supplyName", "帅启高"),
                Map.entry("bizCode", "换2025050083"),
                Map.entry("projectName", "特种公司DB21")));

        verify(datasetQueryService).runNative(eq(7L), sqlCaptor.capture(), any(DatasetConstraints.class), bindingsCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("FROM t_purchase_price_item a");
        assertThat(sqlCaptor.getValue()).contains("LEFT JOIN t_plan_purchase_item c ON c.purchase_price_id = a.id");
        assertThat(sqlCaptor.getValue()).contains("LEFT JOIN t_flower_biz_item d ON d.id = c.flower_item_id");
        assertThat(sqlCaptor.getValue()).contains("a.supply_name LIKE ?");
        assertThat(sqlCaptor.getValue()).contains("a.pay_type = ?");
        assertThat(bindingsCaptor.getValue()).containsExactly(
                "42",
                "3001",
                "%换2025050083%",
                "%蝴蝶兰%",
                "%0.5m%",
                "%帅启高%",
                "2025-05-01",
                "2025-05-31",
                "2");
    }

    @Test
    void shouldExecuteWarehouseStockOverviewAgainstConfiguredBusinessDatabase() throws Exception {
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setId(7L);
        database.setName("园林业务库");
        when(databaseRepository.findFirstByNameIgnoreCase("园林业务库")).thenReturn(Optional.of(database));
        when(datasetQueryService.runNative(eq(7L), any(String.class), any(DatasetConstraints.class), any()))
                .thenReturn(new DatasetResult(
                        List.of(List.of("大兴基地", "垃圾袋", "描述:厚（50个/包）#规格:120X140cm", 9906, new BigDecimal("0.4500"), new BigDecimal("13.00"))),
                        List.of(
                                Map.of("name", "storehouseName", "display_name", "所属库房", "base_type", "type/Text"),
                                Map.of("name", "goodName", "display_name", "物品名称", "base_type", "type/Text"),
                                Map.of("name", "goodSpecs", "display_name", "物品属性", "base_type", "type/Text"),
                                Map.of("name", "goodNumber", "display_name", "可用数量", "base_type", "type/Number"),
                                Map.of("name", "outCost", "display_name", "出库单价", "base_type", "type/Number"),
                                Map.of("name", "salePrice", "display_name", "销售单价", "base_type", "type/Number")),
                        List.of(),
                        "Asia/Shanghai"));

        DefaultFixedReportExecutionService service =
                new DefaultFixedReportExecutionService(databaseRepository, datasetQueryService);

        Optional<FixedReportExecutionService.ExecutionResult> result = service.execute(
                warehouseStockOverviewTemplate(),
                Map.of(
                        "storehouseInfoId", "1001",
                        "goodType", "4",
                        "goodName", "垃圾袋",
                        "goodSpecs", "120X140cm",
                        "passNumber", "10",
                        "underNumber", "10000"),
                new ReportExecutionPlanService.ReportExecutionPlan(
                        ReportExecutionPlanService.Route.AUTHORITY_SQL,
                        "WH-STOCK-OVERVIEW",
                        "authority.inventory",
                        "authority.inventory.stock_overview",
                        "template uses realtime stock overview sql",
                        "SQL",
                        "REALTIME"));

        assertThat(result).isPresent();
        assertThat(result.get().databaseId()).isEqualTo(7L);
        assertThat(result.get().databaseName()).isEqualTo("园林业务库");
        assertThat(result.get().rowCount()).isEqualTo(1);
        assertThat(result.get().columns()).extracting(FixedReportExecutionService.PreviewColumn::key)
                .containsExactly("storehouseName", "goodName", "goodSpecs", "goodNumber", "outCost", "salePrice");
        assertThat(result.get().rows()).containsExactly(Map.of(
                "storehouseName", "大兴基地",
                "goodName", "垃圾袋",
                "goodSpecs", "描述:厚（50个/包）#规格:120X140cm",
                "goodNumber", 9906,
                "outCost", new BigDecimal("0.4500"),
                "salePrice", new BigDecimal("13.00")));

        verify(datasetQueryService).runNative(eq(7L), sqlCaptor.capture(), any(DatasetConstraints.class), bindingsCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("FROM s_stock_info a");
        assertThat(sqlCaptor.getValue()).contains("LEFT JOIN b_goods_price gp ON gp.id = a.good_price_id");
        assertThat(sqlCaptor.getValue()).contains("a.del_flag = '0'");
        assertThat(bindingsCaptor.getValue()).containsExactly("1001", "4", "%垃圾袋%", "%120X140cm%", "10", "10000");
    }

    @Test
    void shouldExecuteWarehouseLowStockAlertAgainstConfiguredBusinessDatabase() throws Exception {
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setId(7L);
        database.setName("园林业务库");
        when(databaseRepository.findFirstByNameIgnoreCase("园林业务库")).thenReturn(Optional.of(database));
        when(datasetQueryService.runNative(eq(7L), any(String.class), any(DatasetConstraints.class), any()))
                .thenReturn(new DatasetResult(
                        List.of(List.of("中石油缓养库", "白塑料盆", "小", "10X12", 0, new BigDecimal("2.3000"), new BigDecimal("4.50"), "李四")),
                        List.of(
                                Map.of("name", "storehouseName", "display_name", "所属库房", "base_type", "type/Text"),
                                Map.of("name", "goodName", "display_name", "物品名称", "base_type", "type/Text"),
                                Map.of("name", "goodNorms", "display_name", "物品规格", "base_type", "type/Text"),
                                Map.of("name", "goodSpecs", "display_name", "物品属性", "base_type", "type/Text"),
                                Map.of("name", "goodNumber", "display_name", "可用数量", "base_type", "type/Number"),
                                Map.of("name", "outCost", "display_name", "出库单价", "base_type", "type/Number"),
                                Map.of("name", "salePrice", "display_name", "销售单价", "base_type", "type/Number"),
                                Map.of("name", "leaderUserName", "display_name", "负责人", "base_type", "type/Text")),
                        List.of(),
                        "Asia/Shanghai"));

        DefaultFixedReportExecutionService service =
                new DefaultFixedReportExecutionService(databaseRepository, datasetQueryService);

        Optional<FixedReportExecutionService.ExecutionResult> result = service.execute(
                warehouseLowStockAlertTemplate(),
                Map.of(
                        "storehouseInfoId", "1001",
                        "goodType", "2",
                        "goodName", "白塑料盆",
                        "goodSpecs", "10X12",
                        "underNumber", "10",
                        "status", "0"),
                new ReportExecutionPlanService.ReportExecutionPlan(
                        ReportExecutionPlanService.Route.AUTHORITY_SQL,
                        "WH-LOW-STOCK-ALERT",
                        "authority.inventory",
                        "authority.inventory.low_stock_alert",
                        "template uses realtime low stock alert sql",
                        "SQL",
                        "REALTIME"));

        assertThat(result).isPresent();
        assertThat(result.get().databaseId()).isEqualTo(7L);
        assertThat(result.get().databaseName()).isEqualTo("园林业务库");
        assertThat(result.get().rowCount()).isEqualTo(1);
        assertThat(result.get().columns()).extracting(FixedReportExecutionService.PreviewColumn::key)
                .containsExactly("storehouseName", "goodName", "goodNorms", "goodSpecs", "goodNumber", "outCost", "salePrice", "leaderUserName");
        assertThat(result.get().rows()).containsExactly(Map.of(
                "storehouseName", "中石油缓养库",
                "goodName", "白塑料盆",
                "goodNorms", "小",
                "goodSpecs", "10X12",
                "goodNumber", 0,
                "outCost", new BigDecimal("2.3000"),
                "salePrice", new BigDecimal("4.50"),
                "leaderUserName", "李四"));

        verify(datasetQueryService).runNative(eq(7L), sqlCaptor.capture(), any(DatasetConstraints.class), bindingsCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("FROM s_stock_info a");
        assertThat(sqlCaptor.getValue()).contains("a.good_number <= ?");
        assertThat(sqlCaptor.getValue()).contains("a.status = ?");
        assertThat(bindingsCaptor.getValue()).containsExactly("1001", "2", "%白塑料盆%", "%10X12%", "10", "0");
    }

    @Test
    void shouldFallbackToMysqlBusinessDatabaseWhenTemplatePointsToSystemRuntimeDatabase() throws Exception {
        AnalyticsDatabase runtimeDatabase = new AnalyticsDatabase();
        runtimeDatabase.setId(9L);
        runtimeDatabase.setName("园林业务库");
        runtimeDatabase.setEngine("postgres");
        runtimeDatabase.setDetailsJson("{\"dataSourceId\":10}");
        runtimeDatabase.setDatabaseRole(AnalyticsDatabaseRole.SYSTEM_RUNTIME);

        AnalyticsDatabase businessDatabase = new AnalyticsDatabase();
        businessDatabase.setId(8L);
        businessDatabase.setName("新业务测试库1");
        businessDatabase.setEngine("mysql");
        businessDatabase.setDetailsJson("{\"dataSourceId\":9}");
        businessDatabase.setDatabaseRole(AnalyticsDatabaseRole.BUSINESS_PRIMARY);

        when(databaseRepository.findFirstByNameIgnoreCase("园林业务库")).thenReturn(Optional.of(runtimeDatabase));
        when(databaseRepository.findAll()).thenReturn(List.of(runtimeDatabase, businessDatabase));
        when(datasetQueryService.runNative(eq(8L), any(String.class), any(DatasetConstraints.class), any()))
                .thenReturn(new DatasetResult(
                        List.of(List.of("中石油缓养库", "白塑料盆", "小", "10X12", 0, new BigDecimal("2.3000"), new BigDecimal("4.50"), "李四")),
                        List.of(
                                Map.of("name", "storehouseName", "display_name", "所属库房", "base_type", "type/Text"),
                                Map.of("name", "goodName", "display_name", "物品名称", "base_type", "type/Text"),
                                Map.of("name", "goodNorms", "display_name", "物品规格", "base_type", "type/Text"),
                                Map.of("name", "goodSpecs", "display_name", "物品属性", "base_type", "type/Text"),
                                Map.of("name", "goodNumber", "display_name", "可用数量", "base_type", "type/Number"),
                                Map.of("name", "outCost", "display_name", "出库单价", "base_type", "type/Number"),
                                Map.of("name", "salePrice", "display_name", "销售单价", "base_type", "type/Number"),
                                Map.of("name", "leaderUserName", "display_name", "负责人", "base_type", "type/Text")),
                        List.of(),
                        "Asia/Shanghai"));

        DefaultFixedReportExecutionService service =
                new DefaultFixedReportExecutionService(databaseRepository, datasetQueryService);

        Optional<FixedReportExecutionService.ExecutionResult> result = service.execute(
                warehouseLowStockAlertTemplate(),
                Map.of("underNumber", "10"),
                new ReportExecutionPlanService.ReportExecutionPlan(
                        ReportExecutionPlanService.Route.AUTHORITY_SQL,
                        "WH-LOW-STOCK-ALERT",
                        "authority.inventory",
                        "authority.inventory.low_stock_alert",
                        "template uses realtime low stock alert sql",
                        "SQL",
                        "REALTIME"));

        assertThat(result).isPresent();
        assertThat(result.get().databaseId()).isEqualTo(8L);
        assertThat(result.get().databaseName()).isEqualTo("新业务测试库1");

        verify(datasetQueryService).runNative(eq(8L), sqlCaptor.capture(), any(DatasetConstraints.class), bindingsCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("FROM s_stock_info a");
    }

    @Test
    void shouldPreferBusinessPrimaryDatabaseRoleOverLegacyNameHeuristics() throws Exception {
        AnalyticsDatabase secondaryDatabase = new AnalyticsDatabase();
        secondaryDatabase.setId(7L);
        secondaryDatabase.setName("归档业务库");
        secondaryDatabase.setEngine("mysql");
        secondaryDatabase.setDetailsJson("{\"dataSourceId\":11}");
        secondaryDatabase.setDatabaseRole(AnalyticsDatabaseRole.BUSINESS_SECONDARY);

        AnalyticsDatabase primaryDatabase = new AnalyticsDatabase();
        primaryDatabase.setId(8L);
        primaryDatabase.setName("新业务测试库1");
        primaryDatabase.setEngine("mysql");
        primaryDatabase.setDetailsJson("{\"dataSourceId\":9}");
        primaryDatabase.setDatabaseRole(AnalyticsDatabaseRole.BUSINESS_PRIMARY);

        when(databaseRepository.findFirstByNameIgnoreCase("园林业务库")).thenReturn(Optional.empty());
        when(databaseRepository.findAll()).thenReturn(List.of(secondaryDatabase, primaryDatabase));
        when(datasetQueryService.runNative(eq(8L), any(String.class), any(DatasetConstraints.class), any()))
                .thenReturn(new DatasetResult(
                        List.of(List.of("中石油缓养库", "白塑料盆", "小", "10X12", 0, new BigDecimal("2.3000"), new BigDecimal("4.50"), "李四")),
                        List.of(
                                Map.of("name", "storehouseName", "display_name", "所属库房", "base_type", "type/Text"),
                                Map.of("name", "goodName", "display_name", "物品名称", "base_type", "type/Text"),
                                Map.of("name", "goodNorms", "display_name", "物品规格", "base_type", "type/Text"),
                                Map.of("name", "goodSpecs", "display_name", "物品属性", "base_type", "type/Text"),
                                Map.of("name", "goodNumber", "display_name", "可用数量", "base_type", "type/Number"),
                                Map.of("name", "outCost", "display_name", "出库单价", "base_type", "type/Number"),
                                Map.of("name", "salePrice", "display_name", "销售单价", "base_type", "type/Number"),
                                Map.of("name", "leaderUserName", "display_name", "负责人", "base_type", "type/Text")),
                        List.of(),
                        "Asia/Shanghai"));

        DefaultFixedReportExecutionService service =
                new DefaultFixedReportExecutionService(databaseRepository, datasetQueryService);

        Optional<FixedReportExecutionService.ExecutionResult> result = service.execute(
                warehouseLowStockAlertTemplate(),
                Map.of("underNumber", "10"),
                new ReportExecutionPlanService.ReportExecutionPlan(
                        ReportExecutionPlanService.Route.AUTHORITY_SQL,
                        "WH-LOW-STOCK-ALERT",
                        "authority.inventory",
                        "authority.inventory.low_stock_alert",
                        "template uses realtime low stock alert sql",
                        "SQL",
                        "REALTIME"));

        assertThat(result).isPresent();
        assertThat(result.get().databaseId()).isEqualTo(8L);
        assertThat(result.get().databaseName()).isEqualTo("新业务测试库1");

        verify(datasetQueryService).runNative(eq(8L), sqlCaptor.capture(), any(DatasetConstraints.class), bindingsCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("FROM s_stock_info a");
    }

    @Test
    void shouldResolveBusinessPrimaryFromQueryContractDatabaseRole() throws Exception {
        AnalyticsDatabase runtimeDatabase = new AnalyticsDatabase();
        runtimeDatabase.setId(9L);
        runtimeDatabase.setName("园林业务库");
        runtimeDatabase.setEngine("postgres");
        runtimeDatabase.setDetailsJson("{\"dataSourceId\":10}");
        runtimeDatabase.setDatabaseRole(AnalyticsDatabaseRole.SYSTEM_RUNTIME);

        AnalyticsDatabase businessDatabase = new AnalyticsDatabase();
        businessDatabase.setId(8L);
        businessDatabase.setName("新业务测试库1");
        businessDatabase.setEngine("mysql");
        businessDatabase.setDetailsJson("{\"dataSourceId\":9}");
        businessDatabase.setDatabaseRole(AnalyticsDatabaseRole.BUSINESS_PRIMARY);

        when(databaseRepository.findAll()).thenReturn(List.of(runtimeDatabase, businessDatabase));
        when(datasetQueryService.runNative(eq(8L), any(String.class), any(DatasetConstraints.class), any()))
                .thenReturn(new DatasetResult(
                        List.of(List.of("中石油缓养库", "白塑料盆", "小", "10X12", 0, new BigDecimal("2.3000"), new BigDecimal("4.50"), "李四")),
                        List.of(
                                Map.of("name", "storehouseName", "display_name", "所属库房", "base_type", "type/Text"),
                                Map.of("name", "goodName", "display_name", "物品名称", "base_type", "type/Text"),
                                Map.of("name", "goodNorms", "display_name", "物品规格", "base_type", "type/Text"),
                                Map.of("name", "goodSpecs", "display_name", "物品属性", "base_type", "type/Text"),
                                Map.of("name", "goodNumber", "display_name", "可用数量", "base_type", "type/Number"),
                                Map.of("name", "outCost", "display_name", "出库单价", "base_type", "type/Number"),
                                Map.of("name", "salePrice", "display_name", "销售单价", "base_type", "type/Number"),
                                Map.of("name", "leaderUserName", "display_name", "负责人", "base_type", "type/Text")),
                        List.of(),
                        "Asia/Shanghai"));

        DefaultFixedReportExecutionService service =
                new DefaultFixedReportExecutionService(databaseRepository, datasetQueryService);

        Optional<FixedReportExecutionService.ExecutionResult> result = service.execute(
                warehouseLowStockAlertRoleBasedTemplate(),
                Map.of("underNumber", "10"),
                new ReportExecutionPlanService.ReportExecutionPlan(
                        ReportExecutionPlanService.Route.AUTHORITY_SQL,
                        "WH-LOW-STOCK-ALERT",
                        "authority.inventory",
                        "authority.inventory.low_stock_alert",
                        "template uses realtime low stock alert sql",
                        "SQL",
                        "REALTIME"));

        assertThat(result).isPresent();
        assertThat(result.get().databaseId()).isEqualTo(8L);
        assertThat(result.get().databaseName()).isEqualTo("新业务测试库1");
    }

    @Test
    void shouldExecuteFinanceSettlementSummaryAgainstConfiguredBusinessDatabase() throws Exception {
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setId(7L);
        database.setName("园林业务库");
        when(databaseRepository.findFirstByNameIgnoreCase("园林业务库")).thenReturn(Optional.of(database));
        when(datasetQueryService.runNative(eq(7L), any(String.class), any(DatasetConstraints.class), any()))
                .thenReturn(new DatasetResult(
                        List.of(List.of(
                                "202504",
                                "国企大厦",
                                "申请售花",
                                "主营业务收入/销售收入",
                                "月季",
                                new BigDecimal("8800.0000"),
                                "公司",
                                "收入/项目销售额挂帐",
                                "张三",
                                "演示备注")),
                        List.of(
                                Map.of("name", "accountPeriod", "display_name", "财务账期", "base_type", "type/Text"),
                                Map.of("name", "projectName", "display_name", "项目", "base_type", "type/Text"),
                                Map.of("name", "bizTypeName", "display_name", "流程类型", "base_type", "type/Text"),
                                Map.of("name", "subjectFullName", "display_name", "财务科目", "base_type", "type/Text"),
                                Map.of("name", "feeName", "display_name", "收支名称", "base_type", "type/Text"),
                                Map.of("name", "feeNumber", "display_name", "金额", "base_type", "type/Number"),
                                Map.of("name", "feeBelongName", "display_name", "成本归属", "base_type", "type/Text"),
                                Map.of("name", "settleHandleName", "display_name", "结算处理", "base_type", "type/Text"),
                                Map.of("name", "feeUserName", "display_name", "关联人员", "base_type", "type/Text"),
                                Map.of("name", "remark", "display_name", "备注", "base_type", "type/Text")),
                        List.of(),
                        "Asia/Shanghai"));

        DefaultFixedReportExecutionService service =
                new DefaultFixedReportExecutionService(databaseRepository, datasetQueryService);

        Optional<FixedReportExecutionService.ExecutionResult> result = service.execute(
                financeSettlementSummaryTemplate(),
                Map.of(
                        "accountPeriod", "202504",
                        "projectId", "2001",
                        "feeUserId", "99",
                        "status", "1"),
                new ReportExecutionPlanService.ReportExecutionPlan(
                        ReportExecutionPlanService.Route.AUTHORITY_SQL,
                        "FIN-AR-OVERVIEW",
                        "authority.finance",
                        "authority.finance.settlement_summary",
                        "template uses realtime settlement summary sql",
                        "SQL",
                        "REALTIME"));

        assertThat(result).isPresent();
        assertThat(result.get().databaseId()).isEqualTo(7L);
        assertThat(result.get().databaseName()).isEqualTo("园林业务库");
        assertThat(result.get().rowCount()).isEqualTo(1);
        assertThat(result.get().columns()).extracting(FixedReportExecutionService.PreviewColumn::key)
                .containsExactly(
                        "accountPeriod",
                        "projectName",
                        "bizTypeName",
                        "subjectFullName",
                        "feeName",
                        "feeNumber",
                        "feeBelongName",
                        "settleHandleName",
                        "feeUserName",
                        "remark");
        assertThat(result.get().rows()).containsExactly(Map.of(
                "accountPeriod", "202504",
                "projectName", "国企大厦",
                "bizTypeName", "申请售花",
                "subjectFullName", "主营业务收入/销售收入",
                "feeName", "月季",
                "feeNumber", new BigDecimal("8800.0000"),
                "feeBelongName", "公司",
                "settleHandleName", "收入/项目销售额挂帐",
                "feeUserName", "张三",
                "remark", "演示备注"));

        verify(datasetQueryService).runNative(eq(7L), sqlCaptor.capture(), any(DatasetConstraints.class), bindingsCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("FROM f_settled_items");
        assertThat(sqlCaptor.getValue()).contains("status > 0");
        assertThat(sqlCaptor.getValue()).contains("ORDER BY a.account_period ASC");
        assertThat(bindingsCaptor.getValue()).containsExactly("202504", "2001", "99", "1");
    }

    @Test
    void shouldExecuteFinanceAdvanceRequestStatusAgainstConfiguredBusinessDatabase() throws Exception {
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setId(7L);
        database.setName("园林业务库");
        when(databaseRepository.findFirstByNameIgnoreCase("园林业务库")).thenReturn(Optional.of(database));
        when(datasetQueryService.runNative(eq(7L), any(String.class), any(DatasetConstraints.class), any()))
                .thenReturn(new DatasetResult(
                        List.of(List.of(
                                "武延娟",
                                "YZ202602100273",
                                "备用金",
                                new BigDecimal("1000.0000"),
                                new BigDecimal("1000.0000"),
                                new BigDecimal("800.0000"),
                                "已付款",
                                "有",
                                "未提供",
                                "武延娟于20260210152437发起预支",
                                "2026-02-10 15:24:38",
                                "2026-02-10 18:00:00")),
                        List.of(
                                Map.of("name", "applyUserName", "display_name", "申请人", "base_type", "type/Text"),
                                Map.of("name", "code", "display_name", "单号", "base_type", "type/Text"),
                                Map.of("name", "typeName", "display_name", "申请类型", "base_type", "type/Text"),
                                Map.of("name", "totalAmount", "display_name", "申请金额", "base_type", "type/Number"),
                                Map.of("name", "payAmount", "display_name", "付款金额", "base_type", "type/Number"),
                                Map.of("name", "offsetTotalAmount", "display_name", "抵消金额", "base_type", "type/Number"),
                                Map.of("name", "statusName", "display_name", "状态", "base_type", "type/Text"),
                                Map.of("name", "invoiceStatusName", "display_name", "发票", "base_type", "type/Text"),
                                Map.of("name", "invoiceOfferName", "display_name", "发票提供", "base_type", "type/Text"),
                                Map.of("name", "title", "display_name", "标题", "base_type", "type/Text"),
                                Map.of("name", "applyTime", "display_name", "申请时间", "base_type", "type/Text"),
                                Map.of("name", "payTime", "display_name", "付款日期", "base_type", "type/Text")),
                        List.of(),
                        "Asia/Shanghai"));

        DefaultFixedReportExecutionService service =
                new DefaultFixedReportExecutionService(databaseRepository, datasetQueryService);

        Optional<FixedReportExecutionService.ExecutionResult> result = service.execute(
                financeAdvanceRequestStatusTemplate(),
                Map.of(
                        "code", "YZ202602100273",
                        "status", "7",
                        "applyUserId", "1001"),
                new ReportExecutionPlanService.ReportExecutionPlan(
                        ReportExecutionPlanService.Route.AUTHORITY_SQL,
                        "FIN-ADVANCE-REQUEST-STATUS",
                        "authority.finance",
                        "authority.finance.advance_request_status",
                        "template uses realtime advance request status sql",
                        "SQL",
                        "REALTIME"));

        assertThat(result).isPresent();
        assertThat(result.get().databaseId()).isEqualTo(7L);
        assertThat(result.get().databaseName()).isEqualTo("园林业务库");
        assertThat(result.get().rowCount()).isEqualTo(1);
        assertThat(result.get().columns()).extracting(FixedReportExecutionService.PreviewColumn::key)
                .containsExactly(
                        "applyUserName",
                        "code",
                        "typeName",
                        "totalAmount",
                        "payAmount",
                        "offsetTotalAmount",
                        "statusName",
                        "invoiceStatusName",
                        "invoiceOfferName",
                        "title",
                        "applyTime",
                        "payTime");
        assertThat(result.get().rows()).containsExactly(Map.ofEntries(
                Map.entry("applyUserName", "武延娟"),
                Map.entry("code", "YZ202602100273"),
                Map.entry("typeName", "备用金"),
                Map.entry("totalAmount", new BigDecimal("1000.0000")),
                Map.entry("payAmount", new BigDecimal("1000.0000")),
                Map.entry("offsetTotalAmount", new BigDecimal("800.0000")),
                Map.entry("statusName", "已付款"),
                Map.entry("invoiceStatusName", "有"),
                Map.entry("invoiceOfferName", "未提供"),
                Map.entry("title", "武延娟于20260210152437发起预支"),
                Map.entry("applyTime", "2026-02-10 15:24:38"),
                Map.entry("payTime", "2026-02-10 18:00:00")));

        verify(datasetQueryService).runNative(eq(7L), sqlCaptor.capture(), any(DatasetConstraints.class), bindingsCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("FROM f_advance_info a");
        assertThat(sqlCaptor.getValue()).contains("a.code LIKE ?");
        assertThat(sqlCaptor.getValue()).contains("a.status = ?");
        assertThat(sqlCaptor.getValue()).contains("a.apply_user_id = ?");
        assertThat(bindingsCaptor.getValue()).containsExactly("%YZ202602100273%", "7", "1001");
    }

    @Test
    void shouldExecuteFinancePendingReceiptsDetailAgainstConfiguredBusinessDatabase() throws Exception {
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setId(7L);
        database.setName("园林业务库");
        when(databaseRepository.findFirstByNameIgnoreCase("园林业务库")).thenReturn(Optional.of(database));
        when(datasetQueryService.runNative(eq(7L), any(String.class), any(DatasetConstraints.class), any()))
                .thenReturn(new DatasetResult(
                        List.of(List.of(
                                "202602",
                                "某大型企业",
                                "测试项目A",
                                new BigDecimal("12000.0000"),
                                new BigDecimal("8000.0000"),
                                new BigDecimal("4000.0000"),
                                new BigDecimal("0.9500"),
                                "赵经理",
                                "钱商务")),
                        List.of(
                                Map.of("name", "accountPeriod", "display_name", "账期", "base_type", "type/Text"),
                                Map.of("name", "customerName", "display_name", "客户", "base_type", "type/Text"),
                                Map.of("name", "projectName", "display_name", "项目点", "base_type", "type/Text"),
                                Map.of("name", "receivableAmount", "display_name", "应收", "base_type", "type/Number"),
                                Map.of("name", "receiptAmount", "display_name", "已收", "base_type", "type/Number"),
                                Map.of("name", "arrearsAmount", "display_name", "待收", "base_type", "type/Number"),
                                Map.of("name", "discountRate", "display_name", "折扣系数", "base_type", "type/Number"),
                                Map.of("name", "managerName", "display_name", "项目经理", "base_type", "type/Text"),
                                Map.of("name", "bizUserName", "display_name", "商务", "base_type", "type/Text")),
                        List.of(),
                        "Asia/Shanghai"));

        DefaultFixedReportExecutionService service =
                new DefaultFixedReportExecutionService(databaseRepository, datasetQueryService);

        Optional<FixedReportExecutionService.ExecutionResult> result = service.execute(
                financePendingReceiptsDetailTemplate(),
                Map.of(
                        "yearAndMonth", "202602",
                        "projectName", "测试项目A",
                        "companyName", "某大型企业"),
                new ReportExecutionPlanService.ReportExecutionPlan(
                        ReportExecutionPlanService.Route.AUTHORITY_SQL,
                        "FIN-PENDING-RECEIPTS-DETAIL",
                        "authority.finance",
                        "authority.finance.pending_receipts_detail",
                        "template uses pending receipts sql",
                        "SQL",
                        "REALTIME"));

        assertThat(result).isPresent();
        assertThat(result.get().databaseId()).isEqualTo(7L);
        assertThat(result.get().rowCount()).isEqualTo(1);

        verify(datasetQueryService).runNative(eq(7L), sqlCaptor.capture(), any(DatasetConstraints.class), bindingsCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("FROM a_month_accounting ma");
        assertThat(sqlCaptor.getValue()).contains("ma.company_name AS customerName");
        assertThat(sqlCaptor.getValue()).contains("(COALESCE(ma.receivable_total_amount, 0) - COALESCE(ma.net_receipt_total_amount, 0)) > 0");
        assertThat(bindingsCaptor.getValue()).containsExactly("%测试项目A%", "202602", "%某大型企业%");
    }

    @Test
    void shouldExecuteFinanceProjectCollectionProgressAgainstConfiguredBusinessDatabase() throws Exception {
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setId(7L);
        database.setName("园林业务库");
        when(databaseRepository.findFirstByNameIgnoreCase("园林业务库")).thenReturn(Optional.of(database));
        when(datasetQueryService.runNative(eq(7L), any(String.class), any(DatasetConstraints.class), any()))
                .thenReturn(new DatasetResult(
                        List.of(List.of(
                                "测试项目A",
                                "202602",
                                new BigDecimal("12000.0000"),
                                new BigDecimal("8000.0000"),
                                new BigDecimal("4000.0000"),
                                new BigDecimal("66.7"),
                                2,
                                "2026-02-21 10:20:00",
                                "赵经理")),
                        List.of(
                                Map.of("name", "projectName", "display_name", "项目点", "base_type", "type/Text"),
                                Map.of("name", "accountPeriod", "display_name", "账期", "base_type", "type/Text"),
                                Map.of("name", "receivableAmount", "display_name", "应收", "base_type", "type/Number"),
                                Map.of("name", "receiptAmount", "display_name", "已收", "base_type", "type/Number"),
                                Map.of("name", "arrearsAmount", "display_name", "待收", "base_type", "type/Number"),
                                Map.of("name", "collectionRate", "display_name", "回款进度", "base_type", "type/Number"),
                                Map.of("name", "collectionRecordCount", "display_name", "收款次数", "base_type", "type/Number"),
                                Map.of("name", "lastCollectionTime", "display_name", "最近收款时间", "base_type", "type/Text"),
                                Map.of("name", "managerName", "display_name", "项目经理", "base_type", "type/Text")),
                        List.of(),
                        "Asia/Shanghai"));

        DefaultFixedReportExecutionService service =
                new DefaultFixedReportExecutionService(databaseRepository, datasetQueryService);

        Optional<FixedReportExecutionService.ExecutionResult> result = service.execute(
                financeProjectCollectionProgressTemplate(),
                Map.of(
                        "yearAndMonth", "202602",
                        "projectName", "测试项目A"),
                new ReportExecutionPlanService.ReportExecutionPlan(
                        ReportExecutionPlanService.Route.AUTHORITY_SQL,
                        "FIN-PROJECT-COLLECTION-PROGRESS",
                        "authority.finance",
                        "authority.finance.project_collection_progress",
                        "template uses collection progress sql",
                        "SQL",
                        "REALTIME"));

        assertThat(result).isPresent();
        assertThat(result.get().databaseId()).isEqualTo(7L);
        assertThat(result.get().rowCount()).isEqualTo(1);

        verify(datasetQueryService).runNative(eq(7L), sqlCaptor.capture(), any(DatasetConstraints.class), bindingsCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("FROM a_month_accounting ma");
        assertThat(sqlCaptor.getValue()).contains("FROM a_collection_record cr");
        assertThat(sqlCaptor.getValue()).contains("collectionRecordCount");
        assertThat(sqlCaptor.getValue()).contains("lastCollectionTime");
        assertThat(bindingsCaptor.getValue()).containsExactly("%测试项目A%", "202602");
    }

    @Test
    void shouldExecuteFinancePendingPaymentApprovalAgainstConfiguredBusinessDatabase() throws Exception {
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setId(7L);
        database.setName("园林业务库");
        when(databaseRepository.findFirstByNameIgnoreCase("园林业务库")).thenReturn(Optional.of(database));
        when(datasetQueryService.runNative(eq(7L), any(String.class), any(DatasetConstraints.class), any()))
                .thenReturn(new DatasetResult(
                        List.of(List.of(
                                "FK202602001",
                                "采购报销付款",
                                "采购报销",
                                "未确认",
                                "王财务",
                                "2026-02-22 16:00:00",
                                new BigDecimal("3200.0000"),
                                "张供应商",
                                "中国银行",
                                "有",
                                "未提供")),
                        List.of(
                                Map.of("name", "code", "display_name", "付款单号", "base_type", "type/Text"),
                                Map.of("name", "title", "display_name", "标题", "base_type", "type/Text"),
                                Map.of("name", "businessTypeName", "display_name", "业务类型", "base_type", "type/Text"),
                                Map.of("name", "statusName", "display_name", "状态", "base_type", "type/Text"),
                                Map.of("name", "draweeUserName", "display_name", "付款人", "base_type", "type/Text"),
                                Map.of("name", "draweeDate", "display_name", "付款时间", "base_type", "type/Text"),
                                Map.of("name", "draweePrice", "display_name", "付款金额", "base_type", "type/Number"),
                                Map.of("name", "collectionName", "display_name", "收款人", "base_type", "type/Text"),
                                Map.of("name", "payBankName", "display_name", "付款银行", "base_type", "type/Text"),
                                Map.of("name", "invoiceStatusName", "display_name", "发票", "base_type", "type/Text"),
                                Map.of("name", "invoiceOfferName", "display_name", "发票提供", "base_type", "type/Text")),
                        List.of(),
                        "Asia/Shanghai"));

        DefaultFixedReportExecutionService service =
                new DefaultFixedReportExecutionService(databaseRepository, datasetQueryService);

        Optional<FixedReportExecutionService.ExecutionResult> result = service.execute(
                financePendingPaymentApprovalTemplate(),
                Map.of(
                        "code", "FK202602001",
                        "businessType", "7",
                        "draweeId", "1001",
                        "payBankName", "中国银行",
                        "invoiceStatus", "2",
                        "invoiceOffer", "1"),
                new ReportExecutionPlanService.ReportExecutionPlan(
                        ReportExecutionPlanService.Route.AUTHORITY_SQL,
                        "FIN-PENDING-PAYMENT-APPROVAL",
                        "authority.finance",
                        "authority.finance.pending_payment_approval",
                        "template uses pending payment approval sql",
                        "SQL",
                        "REALTIME"));

        assertThat(result).isPresent();
        assertThat(result.get().databaseId()).isEqualTo(7L);
        assertThat(result.get().rowCount()).isEqualTo(1);

        verify(datasetQueryService).runNative(eq(7L), sqlCaptor.capture(), any(DatasetConstraints.class), bindingsCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("FROM f_pay_record a");
        assertThat(sqlCaptor.getValue()).contains("LEFT JOIN f_expense_account_info c on c.id=a.business_id");
        assertThat(sqlCaptor.getValue()).contains("a.status = 1");
        assertThat(sqlCaptor.getValue()).contains("c.invoice_status");
        assertThat(bindingsCaptor.getValue()).containsExactly("%FK202602001%", "7", "1001", "%中国银行%", "2", "1");
    }

    @Test
    void shouldExecuteProcurementPendingInboundListAgainstConfiguredBusinessDatabase() throws Exception {
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setId(7L);
        database.setName("园林业务库");
        when(databaseRepository.findFirstByNameIgnoreCase("园林业务库")).thenReturn(Optional.of(database));
        when(datasetQueryService.runNative(eq(7L), any(String.class), any(DatasetConstraints.class), any()))
                .thenReturn(new DatasetResult(
                        List.of(List.of(
                                "测试库房",
                                "RK202602001",
                                "绿萝回收入库",
                                18,
                                "报花入库",
                                "换202602001",
                                "待入库",
                                "王五",
                                "2026-02-18 15:00:00",
                                "2026-02-18 11:00:00",
                                "未打印",
                                "待回收转入库")),
                        List.of(
                                Map.of("name", "storehouseName", "display_name", "所属库房", "base_type", "type/Text"),
                                Map.of("name", "code", "display_name", "单号", "base_type", "type/Text"),
                                Map.of("name", "title", "display_name", "标题", "base_type", "type/Text"),
                                Map.of("name", "totalNumber", "display_name", "总数量", "base_type", "type/Number"),
                                Map.of("name", "warehousingTypeName", "display_name", "入库类型", "base_type", "type/Text"),
                                Map.of("name", "realBizCode", "display_name", "业务单据", "base_type", "type/Text"),
                                Map.of("name", "statusName", "display_name", "状态", "base_type", "type/Text"),
                                Map.of("name", "warehousingUserName", "display_name", "入库人", "base_type", "type/Text"),
                                Map.of("name", "warehousingTime", "display_name", "入库时间", "base_type", "type/Text"),
                                Map.of("name", "createTime", "display_name", "创建时间", "base_type", "type/Text"),
                                Map.of("name", "printStatusName", "display_name", "打印状态", "base_type", "type/Text"),
                                Map.of("name", "remark", "display_name", "备注", "base_type", "type/Text")),
                        List.of(),
                        "Asia/Shanghai"));

        DefaultFixedReportExecutionService service =
                new DefaultFixedReportExecutionService(databaseRepository, datasetQueryService);

        Optional<FixedReportExecutionService.ExecutionResult> result = service.execute(
                procurementPendingInboundTemplate(),
                Map.of(
                        "status", "1",
                        "storehouseInfoId", "5001",
                        "code", "RK202602001",
                        "goodName", "绿萝",
                        "goodSpecs", "大盆",
                        "bizCode", "换202602001"),
                new ReportExecutionPlanService.ReportExecutionPlan(
                        ReportExecutionPlanService.Route.AUTHORITY_SQL,
                        "PROC-PENDING-INBOUND-LIST",
                        "authority.procurement",
                        "authority.procurement.pending_inbound_list",
                        "template uses pending inbound sql",
                        "SQL",
                        "REALTIME"));

        assertThat(result).isPresent();
        assertThat(result.get().databaseId()).isEqualTo(7L);
        assertThat(result.get().rowCount()).isEqualTo(1);

        verify(datasetQueryService).runNative(eq(7L), sqlCaptor.capture(), any(DatasetConstraints.class), bindingsCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("FROM t_warehousing_info a");
        assertThat(sqlCaptor.getValue()).contains("LEFT JOIN s_storehouse_info sh ON sh.id = a.storehouse_info_id");
        assertThat(sqlCaptor.getValue()).contains("SELECT SUM(b.good_number) FROM t_warehousing_item b WHERE a.id = b.warehousing_info_id");
        assertThat(sqlCaptor.getValue()).contains("EXISTS(select * from t_warehousing_item b where a.id=b.warehousing_info_id and b.good_name like ?");
        assertThat(sqlCaptor.getValue()).contains("realBizCode");
        assertThat(bindingsCaptor.getValue()).containsExactly("1", "5001", "%RK202602001%", "%绿萝%", "%大盆%", "%换202602001%");
    }

    @Test
    void shouldExecuteProcurementRequestTodoAgainstConfiguredBusinessDatabase() throws Exception {
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setId(7L);
        database.setName("园林业务库");
        when(databaseRepository.findFirstByNameIgnoreCase("园林业务库")).thenReturn(Optional.of(database));
        when(datasetQueryService.runNative(eq(7L), any(String.class), any(DatasetConstraints.class), any()))
                .thenReturn(new DatasetResult(
                        List.of(List.of(
                                "测试项目A",
                                "A栋/大堂",
                                "换202602001",
                                "大堂绿萝更换",
                                "绿萝",
                                "大盆",
                                12,
                                8,
                                new BigDecimal("25.5000"),
                                "2026-02-12 09:30:00",
                                "待采购",
                                "张三",
                                "2026-02-11 00:00:00",
                                "李四",
                                "2026-02-10 12:00:00")),
                        List.of(
                                Map.of("name", "projectName", "display_name", "所属项目点", "base_type", "type/Text"),
                                Map.of("name", "positionFullName", "display_name", "所属摆位", "base_type", "type/Text"),
                                Map.of("name", "flowerCode", "display_name", "所属单号", "base_type", "type/Text"),
                                Map.of("name", "flowerTitle", "display_name", "报花标题", "base_type", "type/Text"),
                                Map.of("name", "goodName", "display_name", "采购物品", "base_type", "type/Text"),
                                Map.of("name", "goodSpecs", "display_name", "物品属性", "base_type", "type/Text"),
                                Map.of("name", "planPurchaseNumber", "display_name", "采购数量", "base_type", "type/Number"),
                                Map.of("name", "realPurchaseNumber", "display_name", "已采购数量", "base_type", "type/Number"),
                                Map.of("name", "purchasePrice", "display_name", "采购单价", "base_type", "type/Number"),
                                Map.of("name", "purchaseTime", "display_name", "采购时间", "base_type", "type/Text"),
                                Map.of("name", "statusName", "display_name", "状态", "base_type", "type/Text"),
                                Map.of("name", "purchaseUserName", "display_name", "采购人", "base_type", "type/Text"),
                                Map.of("name", "planPurchaseTime", "display_name", "计划采购时间", "base_type", "type/Text"),
                                Map.of("name", "distributionUserName", "display_name", "分配人", "base_type", "type/Text"),
                                Map.of("name", "distributionTime", "display_name", "分配时间", "base_type", "type/Text")),
                        List.of(),
                        "Asia/Shanghai"));

        DefaultFixedReportExecutionService service =
                new DefaultFixedReportExecutionService(databaseRepository, datasetQueryService);

        Optional<FixedReportExecutionService.ExecutionResult> result = service.execute(
                procurementRequestTodoTemplate(),
                Map.of(
                        "status", "1",
                        "projectId", "2001",
                        "purchaseUserId", "3001",
                        "flowerCode", "换202602001",
                        "goodName", "绿萝"),
                new ReportExecutionPlanService.ReportExecutionPlan(
                        ReportExecutionPlanService.Route.AUTHORITY_SQL,
                        "PROC-PURCHASE-REQUEST-TODO",
                        "authority.procurement",
                        "authority.procurement.request_todo",
                        "template uses procurement todo sql",
                        "SQL",
                        "REALTIME"));

        assertThat(result).isPresent();
        assertThat(result.get().databaseId()).isEqualTo(7L);
        assertThat(result.get().rowCount()).isEqualTo(1);

        verify(datasetQueryService).runNative(eq(7L), sqlCaptor.capture(), any(DatasetConstraints.class), bindingsCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("FROM t_plan_purchase_item a");
        assertThat(sqlCaptor.getValue()).contains("LEFT JOIN t_plan_purchase_info b ON b.id = a.plan_purchase_info_id");
        assertThat(sqlCaptor.getValue()).contains("LEFT JOIN t_flower_biz_info c ON c.id = b.flower_biz_info_id");
        assertThat(sqlCaptor.getValue()).contains("LEFT JOIN t_flower_biz_item fi ON fi.id = a.flower_item_id");
        assertThat(sqlCaptor.getValue()).contains("LEFT JOIN p_position p ON p.id = fi.position_id");
        assertThat(sqlCaptor.getValue()).contains("LEFT JOIN t_purchase_price_item pp ON pp.id = a.purchase_price_id");
        assertThat(sqlCaptor.getValue()).contains("b.del_flag = '0'");
        assertThat(bindingsCaptor.getValue()).containsExactly("1", "2001", "3001", "%换202602001%", "%绿萝%");
    }

    @Test
    void shouldExecuteProcurementIntransitBoardAgainstConfiguredBusinessDatabase() throws Exception {
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setId(7L);
        database.setName("园林业务库");
        when(databaseRepository.findFirstByNameIgnoreCase("园林业务库")).thenReturn(Optional.of(database));
        when(datasetQueryService.runNative(eq(7L), any(String.class), any(DatasetConstraints.class), any()))
                .thenReturn(new DatasetResult(
                        List.of(List.of(
                                "测试项目A",
                                "PS202602001",
                                "配送202602001",
                                18,
                                "配送中",
                                "市场->项目点",
                                "张三",
                                "李四",
                                "2026-02-18 09:30:00",
                                "2026-02-18 18:00:00")),
                        List.of(
                                Map.of("name", "projectName", "display_name", "项目点", "base_type", "type/Text"),
                                Map.of("name", "bizCode", "display_name", "关联单号", "base_type", "type/Text"),
                                Map.of("name", "deliveryCode", "display_name", "配送单号", "base_type", "type/Text"),
                                Map.of("name", "totalNumber", "display_name", "数量", "base_type", "type/Number"),
                                Map.of("name", "statusName", "display_name", "状态", "base_type", "type/Text"),
                                Map.of("name", "deliveryModeName", "display_name", "类型", "base_type", "type/Text"),
                                Map.of("name", "deliveryUserName", "display_name", "配送人", "base_type", "type/Text"),
                                Map.of("name", "receiveUserName", "display_name", "接收人", "base_type", "type/Text"),
                                Map.of("name", "startDeliveryTime", "display_name", "配送时间", "base_type", "type/Text"),
                                Map.of("name", "receiveTime", "display_name", "接收时间", "base_type", "type/Text")),
                        List.of(),
                        "Asia/Shanghai"));

        DefaultFixedReportExecutionService service =
                new DefaultFixedReportExecutionService(databaseRepository, datasetQueryService);

        Optional<FixedReportExecutionService.ExecutionResult> result = service.execute(
                procurementIntransitBoardTemplate(),
                Map.of(
                        "projectId", "2001",
                        "status", "1",
                        "code", "配送202602001",
                        "bizCode", "PS202602001",
                        "receiveUserId", "3001"),
                new ReportExecutionPlanService.ReportExecutionPlan(
                        ReportExecutionPlanService.Route.AUTHORITY_SQL,
                        "PROC-INTRANSIT-BOARD",
                        "authority.procurement",
                        "authority.procurement.intransit_board",
                        "template uses procurement intransit sql",
                        "SQL",
                        "REALTIME"));

        assertThat(result).isPresent();
        assertThat(result.get().databaseId()).isEqualTo(7L);
        assertThat(result.get().rowCount()).isEqualTo(1);

        verify(datasetQueryService).runNative(eq(7L), sqlCaptor.capture(), any(DatasetConstraints.class), bindingsCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("FROM t_delivery_info a");
        assertThat(sqlCaptor.getValue()).contains("LEFT JOIN t_flower_biz_info b ON a.biz_id = b.id");
        assertThat(sqlCaptor.getValue()).contains("( SELECT sum( c.good_number ) FROM t_delivery_item c WHERE c.delivery_info_id = a.id ) AS totalNumber");
        assertThat(sqlCaptor.getValue()).contains("t.project_id = ?");
        assertThat(sqlCaptor.getValue()).contains("t.biz_code like ?");
        assertThat(bindingsCaptor.getValue()).containsExactly("2001", "1", "%配送202602001%", "%PS202602001%", "3001");
    }

    @Test
    void shouldExecuteProcurementArrivalOntimeRateAgainstConfiguredBusinessDatabase() throws Exception {
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setId(7L);
        database.setName("园林业务库");
        when(databaseRepository.findFirstByNameIgnoreCase("园林业务库")).thenReturn(Optional.of(database));
        when(datasetQueryService.runNative(eq(7L), any(String.class), any(DatasetConstraints.class), any()))
                .thenReturn(new DatasetResult(
                        List.of(List.of(
                                "2026-02-18",
                                8,
                                6,
                                75.0)),
                        List.of(
                                Map.of("name", "statDate", "display_name", "日期", "base_type", "type/Text"),
                                Map.of("name", "totalDeliveries", "display_name", "配送单数", "base_type", "type/Number"),
                                Map.of("name", "ontimeDeliveries", "display_name", "准时到货数", "base_type", "type/Number"),
                                Map.of("name", "ontimeRate", "display_name", "到货及时率", "base_type", "type/Number")),
                        List.of(),
                        "Asia/Shanghai"));

        DefaultFixedReportExecutionService service =
                new DefaultFixedReportExecutionService(databaseRepository, datasetQueryService);

        Optional<FixedReportExecutionService.ExecutionResult> result = service.execute(
                procurementArrivalOntimeRateTemplate(),
                Map.of(
                        "startDate", "2026-02-01",
                        "endDate", "2026-02-29"),
                new ReportExecutionPlanService.ReportExecutionPlan(
                        ReportExecutionPlanService.Route.AUTHORITY_SQL,
                        "PROC-ARRIVAL-ONTIME-RATE",
                        "authority.procurement",
                        "authority.procurement.arrival_ontime_rate",
                        "template uses procurement arrival ontime sql",
                        "SQL",
                        "REALTIME"));

        assertThat(result).isPresent();
        assertThat(result.get().databaseId()).isEqualTo(7L);
        assertThat(result.get().rowCount()).isEqualTo(1);

        verify(datasetQueryService).runNative(eq(7L), sqlCaptor.capture(), any(DatasetConstraints.class), bindingsCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("FROM t_delivery_info a");
        assertThat(sqlCaptor.getValue()).contains("TIMESTAMPDIFF(HOUR, a.start_delivery_time, a.receive_time) <= 24");
        assertThat(sqlCaptor.getValue()).contains("GROUP BY DATE_FORMAT(a.start_delivery_time, '%Y-%m-%d')");
        assertThat(bindingsCaptor.getValue()).containsExactly("2026-02-01", "2026-02-29");
    }

    @Test
    void shouldExecuteFinanceCustomerArRankAgainstConfiguredBusinessDatabase() throws Exception {
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setId(7L);
        database.setName("园林业务库");
        when(databaseRepository.findFirstByNameIgnoreCase("园林业务库")).thenReturn(Optional.of(database));
        when(datasetQueryService.runNative(eq(7L), any(String.class), any(DatasetConstraints.class), any()))
                .thenReturn(new DatasetResult(
                        List.of(List.of(
                                "某大型企业",
                                new BigDecimal("36000.0000"),
                                3,
                                new BigDecimal("82000.0000"),
                                new BigDecimal("46000.0000"))),
                        List.of(
                                Map.of("name", "customerName", "display_name", "客户", "base_type", "type/Text"),
                                Map.of("name", "totalArrears", "display_name", "欠款金额", "base_type", "type/Number"),
                                Map.of("name", "projectCount", "display_name", "项目数", "base_type", "type/Number"),
                                Map.of("name", "totalReceivable", "display_name", "应收", "base_type", "type/Number"),
                                Map.of("name", "totalReceipt", "display_name", "已收", "base_type", "type/Number")),
                        List.of(),
                        "Asia/Shanghai"));

        DefaultFixedReportExecutionService service =
                new DefaultFixedReportExecutionService(databaseRepository, datasetQueryService);

        Optional<FixedReportExecutionService.ExecutionResult> result = service.execute(
                financeCustomerArRankTemplate(),
                Map.of(
                        "yearAndMonth", "202602",
                        "companyName", "某大型企业"),
                new ReportExecutionPlanService.ReportExecutionPlan(
                        ReportExecutionPlanService.Route.AUTHORITY_SQL,
                        "FIN-CUSTOMER-AR-RANK",
                        "authority.finance",
                        "authority.finance.customer_ar_rank",
                        "template uses customer arrears rank sql",
                        "SQL",
                        "REALTIME"));

        assertThat(result).isPresent();
        assertThat(result.get().databaseId()).isEqualTo(7L);
        assertThat(result.get().rowCount()).isEqualTo(1);

        verify(datasetQueryService).runNative(eq(7L), sqlCaptor.capture(), any(DatasetConstraints.class), bindingsCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("FROM a_month_accounting ma");
        assertThat(sqlCaptor.getValue()).contains("GROUP BY ma.company_name");
        assertThat(sqlCaptor.getValue()).contains("ma.year_and_month = ?");
        assertThat(bindingsCaptor.getValue()).containsExactly("202602", "%某大型企业%");
    }

    @Test
    void shouldExecuteFinanceReimbursementStatusAgainstConfiguredBusinessDatabase() throws Exception {
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setId(7L);
        database.setName("园林业务库");
        when(databaseRepository.findFirstByNameIgnoreCase("园林业务库")).thenReturn(Optional.of(database));
        when(datasetQueryService.runNative(eq(7L), any(String.class), any(DatasetConstraints.class), any()))
                .thenReturn(new DatasetResult(
                        List.of(List.of(
                                "BX202602100916",
                                "超级管理员于20260210150648发起报销",
                                "超级管理员",
                                "2026-02-10 15:06:49",
                                "已结束",
                                new BigDecimal("1000.00"),
                                "无",
                                "",
                                "2026-02-11 00:00:00",
                                "汇款",
                                "王国信",
                                "sssss")),
                        List.of(
                                Map.of("name", "code", "display_name", "单号", "base_type", "type/Text"),
                                Map.of("name", "title", "display_name", "标题", "base_type", "type/Text"),
                                Map.of("name", "applyUserName", "display_name", "申请人", "base_type", "type/Text"),
                                Map.of("name", "applyTime", "display_name", "申请时间", "base_type", "type/Text"),
                                Map.of("name", "statusName", "display_name", "状态", "base_type", "type/Text"),
                                Map.of("name", "totalAmount", "display_name", "报销总金额", "base_type", "type/Number"),
                                Map.of("name", "invoiceStatusName", "display_name", "发票", "base_type", "type/Text"),
                                Map.of("name", "invoiceOfferName", "display_name", "发票提供", "base_type", "type/Text"),
                                Map.of("name", "payTime", "display_name", "付款日期", "base_type", "type/Text"),
                                Map.of("name", "payTypeName", "display_name", "付款方式", "base_type", "type/Text"),
                                Map.of("name", "collectName", "display_name", "收款人", "base_type", "type/Text"),
                                Map.of("name", "remark", "display_name", "关键字", "base_type", "type/Text")),
                        List.of(),
                        "Asia/Shanghai"));

        DefaultFixedReportExecutionService service =
                new DefaultFixedReportExecutionService(databaseRepository, datasetQueryService);

        Optional<FixedReportExecutionService.ExecutionResult> result = service.execute(
                financeReimbursementStatusTemplate(),
                Map.of(
                        "code", "BX202602100916",
                        "status", "3",
                        "applyUserId", "1",
                        "collectName", "王国信",
                        "payType", "3",
                        "remark", "ss"),
                new ReportExecutionPlanService.ReportExecutionPlan(
                        ReportExecutionPlanService.Route.AUTHORITY_SQL,
                        "FIN-REIMBURSEMENT-STATUS",
                        "authority.finance",
                        "authority.finance.reimbursement_status",
                        "template uses realtime reimbursement status sql",
                        "SQL",
                        "REALTIME"));

        assertThat(result).isPresent();
        assertThat(result.get().databaseId()).isEqualTo(7L);
        assertThat(result.get().databaseName()).isEqualTo("园林业务库");
        assertThat(result.get().rowCount()).isEqualTo(1);
        assertThat(result.get().columns()).extracting(FixedReportExecutionService.PreviewColumn::key)
                .containsExactly(
                        "code",
                        "title",
                        "applyUserName",
                        "applyTime",
                        "statusName",
                        "totalAmount",
                        "invoiceStatusName",
                        "invoiceOfferName",
                        "payTime",
                        "payTypeName",
                        "collectName",
                        "remark");
        assertThat(result.get().rows()).containsExactly(Map.ofEntries(
                Map.entry("code", "BX202602100916"),
                Map.entry("title", "超级管理员于20260210150648发起报销"),
                Map.entry("applyUserName", "超级管理员"),
                Map.entry("applyTime", "2026-02-10 15:06:49"),
                Map.entry("statusName", "已结束"),
                Map.entry("totalAmount", new BigDecimal("1000.00")),
                Map.entry("invoiceStatusName", "无"),
                Map.entry("invoiceOfferName", ""),
                Map.entry("payTime", "2026-02-11 00:00:00"),
                Map.entry("payTypeName", "汇款"),
                Map.entry("collectName", "王国信"),
                Map.entry("remark", "sssss")));

        verify(datasetQueryService).runNative(eq(7L), sqlCaptor.capture(), any(DatasetConstraints.class), bindingsCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("FROM f_expense_account_info a");
        assertThat(sqlCaptor.getValue()).contains("CASE a.status");
        assertThat(sqlCaptor.getValue()).contains("CASE a.pay_type");
        assertThat(sqlCaptor.getValue()).contains("a.collect_name LIKE ?");
        assertThat(bindingsCaptor.getValue()).containsExactly(
                "%BX202602100916%",
                "3",
                "1",
                "%王国信%",
                "3",
                "%ss%");
    }

    @Test
    void shouldExecuteFinanceInvoiceReconciliationAgainstConfiguredBusinessDatabase() throws Exception {
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setId(7L);
        database.setName("园林业务库");
        when(databaseRepository.findFirstByNameIgnoreCase("园林业务库")).thenReturn(Optional.of(database));
        when(datasetQueryService.runNative(eq(7L), any(String.class), any(DatasetConstraints.class), any()))
                .thenReturn(new DatasetResult(
                        List.of(List.of(
                                "中石油",
                                "个人销售账单",
                                "待回款",
                                new BigDecimal("300.0000"),
                                new BigDecimal("300.0000"),
                                new BigDecimal("0.0000"),
                                "武延娟",
                                "2025-03-24 14:26:37",
                                "武延娟",
                                "2025-03-24 16:08:08",
                                "KP202503240001",
                                "武延娟于20250324142637发起开票申请")),
                        List.of(
                                Map.of("name", "projectName", "display_name", "项目点", "base_type", "type/Text"),
                                Map.of("name", "billTypeName", "display_name", "类型", "base_type", "type/Text"),
                                Map.of("name", "statusName", "display_name", "状态", "base_type", "type/Text"),
                                Map.of("name", "applyInvoiceAmoney", "display_name", "申请开票金额", "base_type", "type/Number"),
                                Map.of("name", "invoiceAmoney", "display_name", "开票金额", "base_type", "type/Number"),
                                Map.of("name", "payTotalAmount", "display_name", "已回款金额", "base_type", "type/Number"),
                                Map.of("name", "applyUserName", "display_name", "申请人", "base_type", "type/Text"),
                                Map.of("name", "applyTime", "display_name", "申请时间", "base_type", "type/Text"),
                                Map.of("name", "invoiceUserName", "display_name", "开票人", "base_type", "type/Text"),
                                Map.of("name", "invoiceTime", "display_name", "开票时间", "base_type", "type/Text"),
                                Map.of("name", "code", "display_name", "单号", "base_type", "type/Text"),
                                Map.of("name", "title", "display_name", "标题", "base_type", "type/Text")),
                        List.of(),
                        "Asia/Shanghai"));

        DefaultFixedReportExecutionService service =
                new DefaultFixedReportExecutionService(databaseRepository, datasetQueryService);

        Optional<FixedReportExecutionService.ExecutionResult> result = service.execute(
                financeInvoiceReconciliationTemplate(),
                Map.of(
                        "projectId", "1508637941377024000",
                        "status", "4",
                        "billType", "3",
                        "code", "KP202503240001",
                        "itemTitle", "售2025030035",
                        "applyUserId", "1469666776017309696",
                        "applyStartTime", "2025-03-24",
                        "applyEndTime", "2025-03-24",
                        "invoiceStartTime", "2025-03-24",
                        "invoiceEndTime", "2025-03-24"),
                new ReportExecutionPlanService.ReportExecutionPlan(
                        ReportExecutionPlanService.Route.AUTHORITY_SQL,
                        "FIN-INVOICE-RECONCILIATION",
                        "authority.finance",
                        "authority.finance.invoice_reconciliation",
                        "template uses realtime invoice reconciliation sql",
                        "SQL",
                        "REALTIME"));

        assertThat(result).isPresent();
        assertThat(result.get().databaseId()).isEqualTo(7L);
        assertThat(result.get().databaseName()).isEqualTo("园林业务库");
        assertThat(result.get().rowCount()).isEqualTo(1);
        assertThat(result.get().columns()).extracting(FixedReportExecutionService.PreviewColumn::key)
                .containsExactly(
                        "projectName",
                        "billTypeName",
                        "statusName",
                        "applyInvoiceAmoney",
                        "invoiceAmoney",
                        "payTotalAmount",
                        "applyUserName",
                        "applyTime",
                        "invoiceUserName",
                        "invoiceTime",
                        "code",
                        "title");
        assertThat(result.get().rows()).containsExactly(Map.ofEntries(
                Map.entry("projectName", "中石油"),
                Map.entry("billTypeName", "个人销售账单"),
                Map.entry("statusName", "待回款"),
                Map.entry("applyInvoiceAmoney", new BigDecimal("300.0000")),
                Map.entry("invoiceAmoney", new BigDecimal("300.0000")),
                Map.entry("payTotalAmount", new BigDecimal("0.0000")),
                Map.entry("applyUserName", "武延娟"),
                Map.entry("applyTime", "2025-03-24 14:26:37"),
                Map.entry("invoiceUserName", "武延娟"),
                Map.entry("invoiceTime", "2025-03-24 16:08:08"),
                Map.entry("code", "KP202503240001"),
                Map.entry("title", "武延娟于20250324142637发起开票申请")));

        verify(datasetQueryService).runNative(eq(7L), sqlCaptor.capture(), any(DatasetConstraints.class), bindingsCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("FROM a_invoice_info a");
        assertThat(sqlCaptor.getValue()).contains("EXISTS (");
        assertThat(sqlCaptor.getValue()).contains("FROM a_invoice_item ai");
        assertThat(sqlCaptor.getValue()).contains("a.apply_time >= CONCAT(?, ' 00:00:00')");
        assertThat(sqlCaptor.getValue()).contains("a.invoice_time <= CONCAT(?, ' 23:59:59')");
        assertThat(bindingsCaptor.getValue()).containsExactly(
                "1508637941377024000",
                "4",
                "3",
                "%KP202503240001%",
                "%售2025030035%",
                "1469666776017309696",
                "2025-03-24",
                "2025-03-24",
                "2025-03-24",
                "2025-03-24");
    }

    private static AnalyticsReportTemplate procurementSummaryTemplate() {
        AnalyticsReportTemplate template = new AnalyticsReportTemplate();
        template.setTemplateCode("PROC-SUPPLIER-AMOUNT-RANK");
        template.setName("采购汇总");
        template.setDomain("采购");
        template.setCategory("明细");
        template.setDataSourceType("SQL");
        template.setTargetObject("authority.procurement.purchase_summary");
        template.setRefreshPolicy("REALTIME");
        template.setSpecJson("""
                {
                  "templateCode":"PROC-SUPPLIER-AMOUNT-RANK",
                  "reportType":"fixed",
                  "displayType":"table",
                  "queryContract":{
                    "sourceType":"AUTHORITY_SQL",
                    "targetObject":"authority.procurement.purchase_summary",
                    "databaseName":"园林业务库"
                  },
                  "placeholderReviewRequired":false
                }
                """);
        return template;
    }

    private static AnalyticsReportTemplate warehouseStockOverviewTemplate() {
        AnalyticsReportTemplate template = new AnalyticsReportTemplate();
        template.setTemplateCode("WH-STOCK-OVERVIEW");
        template.setName("库存现量");
        template.setDomain("仓库");
        template.setCategory("明细");
        template.setDataSourceType("SQL");
        template.setTargetObject("authority.inventory.stock_overview");
        template.setRefreshPolicy("REALTIME");
        template.setSpecJson("""
                {
                  "templateCode":"WH-STOCK-OVERVIEW",
                  "reportType":"fixed",
                  "displayType":"table",
                  "queryContract":{
                    "sourceType":"AUTHORITY_SQL",
                    "targetObject":"authority.inventory.stock_overview",
                    "databaseName":"园林业务库"
                  },
                  "placeholderReviewRequired":false
                }
                """);
        return template;
    }

    private static AnalyticsReportTemplate procurementOrderExecutionProgressTemplate() {
        AnalyticsReportTemplate template = new AnalyticsReportTemplate();
        template.setTemplateCode("PROC-ORDER-EXECUTION-PROGRESS");
        template.setName("采购明细-执行进度");
        template.setDomain("采购");
        template.setCategory("明细");
        template.setDataSourceType("SQL");
        template.setTargetObject("authority.procurement.order_execution_progress");
        template.setRefreshPolicy("REALTIME");
        template.setSpecJson("""
                {
                  "templateCode":"PROC-ORDER-EXECUTION-PROGRESS",
                  "reportType":"fixed",
                  "displayType":"progress-table",
                  "queryContract":{
                    "sourceType":"AUTHORITY_SQL",
                    "targetObject":"authority.procurement.order_execution_progress",
                    "databaseName":"园林业务库"
                  },
                  "placeholderReviewRequired":false
                }
                """);
        return template;
    }

    private static AnalyticsReportTemplate financeSettlementSummaryTemplate() {
        AnalyticsReportTemplate template = new AnalyticsReportTemplate();
        template.setTemplateCode("FIN-AR-OVERVIEW");
        template.setName("财务结算汇总");
        template.setDomain("财务");
        template.setCategory("明细");
        template.setDataSourceType("SQL");
        template.setTargetObject("authority.finance.settlement_summary");
        template.setRefreshPolicy("REALTIME");
        template.setSpecJson("""
                {
                  "templateCode":"FIN-AR-OVERVIEW",
                  "reportType":"fixed",
                  "displayType":"table",
                  "queryContract":{
                    "sourceType":"AUTHORITY_SQL",
                    "targetObject":"authority.finance.settlement_summary",
                    "databaseName":"园林业务库"
                  },
                  "placeholderReviewRequired":false
                }
                """);
        return template;
    }

    private static AnalyticsReportTemplate warehouseLowStockAlertTemplate() {
        AnalyticsReportTemplate template = new AnalyticsReportTemplate();
        template.setTemplateCode("WH-LOW-STOCK-ALERT");
        template.setName("库存现量-低库存预警");
        template.setDomain("仓库");
        template.setCategory("预警");
        template.setDataSourceType("SQL");
        template.setTargetObject("authority.inventory.low_stock_alert");
        template.setRefreshPolicy("REALTIME");
        template.setSpecJson("""
                {
                  "templateCode":"WH-LOW-STOCK-ALERT",
                  "reportType":"fixed",
                  "displayType":"alert-list",
                  "queryContract":{
                    "sourceType":"AUTHORITY_SQL",
                    "targetObject":"authority.inventory.low_stock_alert",
                    "databaseName":"园林业务库"
                  },
                  "placeholderReviewRequired":false
                }
                """);
        return template;
    }

    private static AnalyticsReportTemplate warehouseLowStockAlertRoleBasedTemplate() {
        AnalyticsReportTemplate template = warehouseLowStockAlertTemplate();
        template.setSpecJson("""
                {
                  "templateCode":"WH-LOW-STOCK-ALERT",
                  "reportType":"fixed",
                  "displayType":"alert-list",
                  "queryContract":{
                    "sourceType":"AUTHORITY_SQL",
                    "targetObject":"authority.inventory.low_stock_alert",
                    "databaseRole":"BUSINESS_PRIMARY"
                  },
                  "placeholderReviewRequired":false
                }
                """);
        return template;
    }

    private static AnalyticsReportTemplate financeAdvanceRequestStatusTemplate() {
        AnalyticsReportTemplate template = new AnalyticsReportTemplate();
        template.setTemplateCode("FIN-ADVANCE-REQUEST-STATUS");
        template.setName("预支申请");
        template.setDomain("财务");
        template.setCategory("状态");
        template.setDataSourceType("SQL");
        template.setTargetObject("authority.finance.advance_request_status");
        template.setRefreshPolicy("REALTIME");
        template.setSpecJson("""
                {
                  "templateCode":"FIN-ADVANCE-REQUEST-STATUS",
                  "reportType":"fixed",
                  "displayType":"status-board",
                  "queryContract":{
                    "sourceType":"AUTHORITY_SQL",
                    "targetObject":"authority.finance.advance_request_status",
                    "databaseName":"园林业务库"
                  },
                  "placeholderReviewRequired":false
                }
                """);
        return template;
    }

    private static AnalyticsReportTemplate financeReimbursementStatusTemplate() {
        AnalyticsReportTemplate template = new AnalyticsReportTemplate();
        template.setTemplateCode("FIN-REIMBURSEMENT-STATUS");
        template.setName("日常报销");
        template.setDomain("财务");
        template.setCategory("状态");
        template.setDataSourceType("SQL");
        template.setTargetObject("authority.finance.reimbursement_status");
        template.setRefreshPolicy("REALTIME");
        template.setSpecJson("""
                {
                  "templateCode":"FIN-REIMBURSEMENT-STATUS",
                  "reportType":"fixed",
                  "displayType":"status-board",
                  "queryContract":{
                    "sourceType":"AUTHORITY_SQL",
                    "targetObject":"authority.finance.reimbursement_status",
                    "databaseName":"园林业务库"
                  },
                  "placeholderReviewRequired":false
                }
                """);
        return template;
    }

    private static AnalyticsReportTemplate financePendingReceiptsDetailTemplate() {
        AnalyticsReportTemplate template = new AnalyticsReportTemplate();
        template.setTemplateCode("FIN-PENDING-RECEIPTS-DETAIL");
        template.setName("待收款明细");
        template.setDomain("财务");
        template.setCategory("明细");
        template.setDataSourceType("SQL");
        template.setTargetObject("authority.finance.pending_receipts_detail");
        template.setRefreshPolicy("REALTIME");
        template.setSpecJson("""
                {
                  "templateCode":"FIN-PENDING-RECEIPTS-DETAIL",
                  "reportType":"fixed",
                  "displayType":"table",
                  "queryContract":{
                    "sourceType":"AUTHORITY_SQL",
                    "targetObject":"authority.finance.pending_receipts_detail",
                    "databaseName":"园林业务库"
                  },
                  "placeholderReviewRequired":false
                }
                """);
        return template;
    }

    private static AnalyticsReportTemplate financeProjectCollectionProgressTemplate() {
        AnalyticsReportTemplate template = new AnalyticsReportTemplate();
        template.setTemplateCode("FIN-PROJECT-COLLECTION-PROGRESS");
        template.setName("项目回款进度");
        template.setDomain("财务");
        template.setCategory("明细");
        template.setDataSourceType("SQL");
        template.setTargetObject("authority.finance.project_collection_progress");
        template.setRefreshPolicy("REALTIME");
        template.setSpecJson("""
                {
                  "templateCode":"FIN-PROJECT-COLLECTION-PROGRESS",
                  "reportType":"fixed",
                  "displayType":"table",
                  "queryContract":{
                    "sourceType":"AUTHORITY_SQL",
                    "targetObject":"authority.finance.project_collection_progress",
                    "databaseName":"园林业务库"
                  },
                  "placeholderReviewRequired":false
                }
                """);
        return template;
    }

    private static AnalyticsReportTemplate financePendingPaymentApprovalTemplate() {
        AnalyticsReportTemplate template = new AnalyticsReportTemplate();
        template.setTemplateCode("FIN-PENDING-PAYMENT-APPROVAL");
        template.setName("待付款审批");
        template.setDomain("财务");
        template.setCategory("明细");
        template.setDataSourceType("SQL");
        template.setTargetObject("authority.finance.pending_payment_approval");
        template.setRefreshPolicy("REALTIME");
        template.setSpecJson("""
                {
                  "templateCode":"FIN-PENDING-PAYMENT-APPROVAL",
                  "reportType":"fixed",
                  "displayType":"table",
                  "queryContract":{
                    "sourceType":"AUTHORITY_SQL",
                    "targetObject":"authority.finance.pending_payment_approval",
                    "databaseName":"园林业务库"
                  },
                  "placeholderReviewRequired":false
                }
                """);
        return template;
    }

    private static AnalyticsReportTemplate procurementPendingInboundTemplate() {
        AnalyticsReportTemplate template = new AnalyticsReportTemplate();
        template.setTemplateCode("PROC-PENDING-INBOUND-LIST");
        template.setName("待入库清单");
        template.setDomain("采购");
        template.setCategory("明细");
        template.setDataSourceType("SQL");
        template.setTargetObject("authority.procurement.pending_inbound_list");
        template.setRefreshPolicy("REALTIME");
        template.setSpecJson("""
                {
                  "templateCode":"PROC-PENDING-INBOUND-LIST",
                  "reportType":"fixed",
                  "displayType":"table",
                  "queryContract":{
                    "sourceType":"AUTHORITY_SQL",
                    "targetObject":"authority.procurement.pending_inbound_list",
                    "databaseName":"园林业务库"
                  },
                  "placeholderReviewRequired":false
                }
                """);
        return template;
    }

    private static AnalyticsReportTemplate procurementRequestTodoTemplate() {
        AnalyticsReportTemplate template = new AnalyticsReportTemplate();
        template.setTemplateCode("PROC-PURCHASE-REQUEST-TODO");
        template.setName("采购计划待处理");
        template.setDomain("采购");
        template.setCategory("明细");
        template.setDataSourceType("SQL");
        template.setTargetObject("authority.procurement.request_todo");
        template.setRefreshPolicy("REALTIME");
        template.setSpecJson("""
                {
                  "templateCode":"PROC-PURCHASE-REQUEST-TODO",
                  "reportType":"fixed",
                  "displayType":"table",
                  "queryContract":{
                    "sourceType":"AUTHORITY_SQL",
                    "targetObject":"authority.procurement.request_todo",
                    "databaseName":"园林业务库"
                  },
                  "placeholderReviewRequired":false
                }
                """);
        return template;
    }

    private static AnalyticsReportTemplate procurementIntransitBoardTemplate() {
        AnalyticsReportTemplate template = new AnalyticsReportTemplate();
        template.setTemplateCode("PROC-INTRANSIT-BOARD");
        template.setName("在途采购");
        template.setDomain("采购");
        template.setCategory("看板");
        template.setDataSourceType("SQL");
        template.setTargetObject("authority.procurement.intransit_board");
        template.setRefreshPolicy("REALTIME");
        template.setSpecJson("""
                {
                  "templateCode":"PROC-INTRANSIT-BOARD",
                  "reportType":"fixed",
                  "displayType":"board",
                  "queryContract":{
                    "sourceType":"AUTHORITY_SQL",
                    "targetObject":"authority.procurement.intransit_board"
                  },
                  "placeholderReviewRequired":false
                }
                """);
        return template;
    }

    private static AnalyticsReportTemplate procurementArrivalOntimeRateTemplate() {
        AnalyticsReportTemplate template = new AnalyticsReportTemplate();
        template.setTemplateCode("PROC-ARRIVAL-ONTIME-RATE");
        template.setName("到货及时率");
        template.setDomain("采购");
        template.setCategory("趋势");
        template.setDataSourceType("SQL");
        template.setTargetObject("authority.procurement.arrival_ontime_rate");
        template.setRefreshPolicy("REALTIME");
        template.setSpecJson("""
                {
                  "templateCode":"PROC-ARRIVAL-ONTIME-RATE",
                  "reportType":"fixed",
                  "displayType":"line-chart",
                  "queryContract":{
                    "sourceType":"AUTHORITY_SQL",
                    "targetObject":"authority.procurement.arrival_ontime_rate"
                  },
                  "placeholderReviewRequired":false
                }
                """);
        return template;
    }

    private static AnalyticsReportTemplate financeCustomerArRankTemplate() {
        AnalyticsReportTemplate template = new AnalyticsReportTemplate();
        template.setTemplateCode("FIN-CUSTOMER-AR-RANK");
        template.setName("客户欠款排行");
        template.setDomain("财务");
        template.setCategory("排行");
        template.setDataSourceType("SQL");
        template.setTargetObject("authority.finance.customer_ar_rank");
        template.setRefreshPolicy("REALTIME");
        template.setSpecJson("""
                {
                  "templateCode":"FIN-CUSTOMER-AR-RANK",
                  "reportType":"fixed",
                  "displayType":"bar-chart",
                  "queryContract":{
                    "sourceType":"AUTHORITY_SQL",
                    "targetObject":"authority.finance.customer_ar_rank"
                  },
                  "placeholderReviewRequired":false
                }
                """);
        return template;
    }

    private static AnalyticsReportTemplate financeInvoiceReconciliationTemplate() {
        AnalyticsReportTemplate template = new AnalyticsReportTemplate();
        template.setTemplateCode("FIN-INVOICE-RECONCILIATION");
        template.setName("开票管理");
        template.setDomain("财务");
        template.setCategory("对账");
        template.setDataSourceType("SQL");
        template.setTargetObject("authority.finance.invoice_reconciliation");
        template.setRefreshPolicy("REALTIME");
        template.setSpecJson("""
                {
                  "templateCode":"FIN-INVOICE-RECONCILIATION",
                  "reportType":"fixed",
                  "displayType":"reconciliation-table",
                  "queryContract":{
                    "sourceType":"AUTHORITY_SQL",
                    "targetObject":"authority.finance.invoice_reconciliation",
                    "databaseName":"园林业务库"
                  },
                  "placeholderReviewRequired":false
                }
                """);
        return template;
    }

    private static AnalyticsReportTemplate prsDbtScreenTemplate() {
        AnalyticsReportTemplate template = new AnalyticsReportTemplate();
        template.setTemplateCode("PRS-FLOWERBIZ-OVERVIEW");
        template.setName("PRS 租赁经营总览");
        template.setDomain("PRS租赁");
        template.setCategory("总览");
        template.setDataSourceType("DBT_SCREEN");
        template.setTargetObject("screen.prs-flowerbiz-overview-v1");
        template.setRefreshPolicy("DBT_BUILD");
        template.setSpecJson("""
                {
                  "templateCode":"PRS-FLOWERBIZ-OVERVIEW",
                  "reportType":"fixed-screen",
                  "displayType":"table",
	                  "queryContract":{
	                    "sourceType":"DBT_SCREEN",
	                    "targetObject":"screen.prs-flowerbiz-overview-v1",
	                    "primaryDbtModel":"public.xycyl_ads_flowerbiz_overview",
	                    "dbtModels":["public.xycyl_dws_flowerbiz_project_monthly"],
	                    "outputColumns":["年份","业务月份","项目","客户","租金净额全口径"],
	                    "orderBy":[{"column":"租金净额全口径","direction":"desc"}]
	                  },
                  "placeholderReviewRequired":false
                }
                """);
        template.setMetricDefinitionJson("""
                {"primaryDbtModel":"public.xycyl_ads_flowerbiz_overview"}
                """);
        return template;
    }
}
