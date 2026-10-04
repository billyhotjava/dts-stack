package com.yuzhi.dts.copilot.analytics.service.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDatabaseRole;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsReportTemplate;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsDatabaseRepository;
import com.yuzhi.dts.copilot.analytics.service.DatasetQueryService;
import com.yuzhi.dts.copilot.analytics.service.DatasetQueryService.DatasetConstraints;
import com.yuzhi.dts.copilot.analytics.service.DatasetQueryService.DatasetResult;
import com.yuzhi.dts.copilot.analytics.service.report.ReportExecutionPlanService.ReportExecutionPlan;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class DefaultFixedReportExecutionService implements FixedReportExecutionService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_DATABASE_NAME = "园林业务库";
    private static final String DEFAULT_DBT_DATABASE_NAME = "DTS dbt模型库";
    private static final String DATABASE_ROLE_SELECTOR_PREFIX = "__ROLE__:";
    private static final int PREVIEW_LIMIT = 50;
    private static final int QUERY_TIMEOUT_SECONDS = 60;

    private final AnalyticsDatabaseRepository databaseRepository;
    private final DatasetQueryService datasetQueryService;

    public DefaultFixedReportExecutionService(
            AnalyticsDatabaseRepository databaseRepository, DatasetQueryService datasetQueryService) {
        this.databaseRepository = databaseRepository;
        this.datasetQueryService = datasetQueryService;
    }

    @Override
    public Optional<ExecutionResult> execute(
            AnalyticsReportTemplate template, Map<String, Object> parameters, ReportExecutionPlan plan) throws SQLException {
        if (template == null || plan == null) {
            return Optional.empty();
        }

        QueryContract contract = resolveQueryContract(template);
        if (contract == null) {
            return Optional.empty();
        }

        String normalizedTarget = normalize(contract.targetObject());
        String normalizedSourceType = normalize(contract.sourceType());
        if (normalizedSourceType.startsWith("dbt") || normalizedTarget.startsWith("screen.")) {
            return Optional.of(executeDbtScreenTable(contract));
        }
        if ("authority.procurement.purchase_summary".equals(normalizedTarget)) {
            return Optional.of(executeProcurementPurchaseSummary(contract, parameters));
        }
        if ("authority.procurement.order_execution_progress".equals(normalizedTarget)) {
            return Optional.of(executeProcurementOrderExecutionProgress(contract, parameters));
        }
        if ("authority.inventory.stock_overview".equals(normalizedTarget)) {
            return Optional.of(executeWarehouseStockOverview(contract, parameters));
        }
        if ("authority.inventory.low_stock_alert".equals(normalizedTarget)) {
            return Optional.of(executeWarehouseLowStockAlert(contract, parameters));
        }
        if ("authority.finance.settlement_summary".equals(normalizedTarget)) {
            return Optional.of(executeFinanceSettlementSummary(contract, parameters));
        }
        if ("authority.finance.advance_request_status".equals(normalizedTarget)) {
            return Optional.of(executeFinanceAdvanceRequestStatus(contract, parameters));
        }
        if ("authority.finance.reimbursement_status".equals(normalizedTarget)) {
            return Optional.of(executeFinanceReimbursementStatus(contract, parameters));
        }
        if ("authority.finance.invoice_reconciliation".equals(normalizedTarget)) {
            return Optional.of(executeFinanceInvoiceReconciliation(contract, parameters));
        }
        if ("authority.finance.pending_receipts_detail".equals(normalizedTarget)) {
            return Optional.of(executeFinancePendingReceiptsDetail(contract, parameters));
        }
        if ("authority.finance.project_collection_progress".equals(normalizedTarget)) {
            return Optional.of(executeFinanceProjectCollectionProgress(contract, parameters));
        }
        if ("mart.finance.customer_ar_rank_daily".equals(normalizedTarget)
                || "authority.finance.customer_ar_rank".equals(normalizedTarget)) {
            return Optional.of(executeFinanceCustomerArRank(contract, parameters));
        }
        if ("authority.finance.pending_payment_approval".equals(normalizedTarget)) {
            return Optional.of(executeFinancePendingPaymentApproval(contract, parameters));
        }
        if ("authority.procurement.pending_inbound_list".equals(normalizedTarget)) {
            return Optional.of(executeProcurementPendingInboundList(contract, parameters));
        }
        if ("authority.procurement.request_todo".equals(normalizedTarget)) {
            return Optional.of(executeProcurementRequestTodo(contract, parameters));
        }
        if ("authority.procurement.intransit_board".equals(normalizedTarget)) {
            return Optional.of(executeProcurementIntransitBoard(contract, parameters));
        }
        if ("fact.procurement.order_event".equals(normalizedTarget)
                || "authority.procurement.arrival_ontime_rate".equals(normalizedTarget)) {
            return Optional.of(executeProcurementArrivalOntimeRate(contract, parameters));
        }
        // ── Batch 2 reports ──
        if ("authority.flowerbiz.biz_document_summary".equals(normalizedTarget)) {
            return Optional.of(executeFlowerBizDocumentSummary(contract, parameters));
        }
        if ("authority.procurement.delivery_record".equals(normalizedTarget)) {
            return Optional.of(executeProcurementDeliveryRecord(contract, parameters));
        }
        if ("authority.finance.reimbursement_list".equals(normalizedTarget)) {
            return Optional.of(executeFinanceReimbursementList(contract, parameters));
        }
        if ("authority.inventory.inout_record".equals(normalizedTarget)) {
            return Optional.of(executeWarehouseInOutRecord(contract, parameters));
        }
        if ("authority.project.contract_list".equals(normalizedTarget)) {
            return Optional.of(executeProjectContractList(contract, parameters));
        }
        if ("authority.project.supervision_check_summary".equals(normalizedTarget)) {
            return Optional.of(executeProjectSupervisionCheckSummary(contract, parameters));
        }
        if ("authority.project.fulfillment_summary".equals(normalizedTarget)) {
            return Optional.of(executeProjectFulfillmentSummary(contract, parameters));
        }
        return Optional.empty();
    }

    private ExecutionResult executeProcurementPurchaseSummary(QueryContract contract, Map<String, Object> parameters)
            throws SQLException {
        AnalyticsDatabase database = resolveDatabase(contract.databaseName());
        StringBuilder sql = new StringBuilder("""
                SELECT
                    DATE_FORMAT( a.purchase_time, '%Y-%m-%d' ) AS purchaseDate,
                    b.purchase_user_id AS purchaseUserId,
                    b.purchase_user_name AS purchaseUserName,
                    ROUND(COALESCE(SUM(a.parchase_number * a.parchase_price), 0), 2) AS totalAmount,
                    ROUND(COALESCE(SUM(CASE WHEN a.pay_type = 1 THEN a.parchase_number * a.parchase_price ELSE 0 END), 0), 2) AS totalCashAmount,
                    ROUND(COALESCE(SUM(CASE WHEN a.pay_type = 2 THEN a.parchase_number * a.parchase_price ELSE 0 END), 0), 2) AS totalChargeAmount
                FROM t_purchase_price_item a
                LEFT JOIN t_purchase_info b ON b.id = a.purchase_info_id
                LEFT JOIN t_plan_purchase_item c ON c.purchase_price_id = a.id
                LEFT JOIN t_flower_biz_item d ON d.id = c.flower_item_id
                LEFT JOIN t_flower_biz_info f ON f.id = d.flower_biz_id
                WHERE d.status <> -1
                  AND d.id IS NOT NULL
                  AND c.status <> -1
                """);
        List<Object> bindings = new ArrayList<>();

        String purchaseUserId = stringParam(parameters, "purchaseUserId");
        if (purchaseUserId != null) {
            sql.append(" AND b.purchase_user_id = ?");
            bindings.add(purchaseUserId);
        }

        String startDate = stringParam(parameters, "startDate");
        if (startDate != null) {
            sql.append(" AND a.purchase_time >= CONCAT(?, ' 00:00:00')");
            bindings.add(startDate);
        }

        String endDate = stringParam(parameters, "endDate");
        if (endDate != null) {
            sql.append(" AND a.purchase_time <= CONCAT(?, ' 23:59:59')");
            bindings.add(endDate);
        }

        sql.append("""

                GROUP BY DATE_FORMAT( a.purchase_time, '%Y-%m-%d' ), b.purchase_user_id, b.purchase_user_name
                ORDER BY purchaseDate DESC, purchaseUserName ASC
                """);

        DatasetResult result = datasetQueryService.runNative(
                database.getId(),
                sql.toString(),
                new DatasetConstraints(PREVIEW_LIMIT, QUERY_TIMEOUT_SECONDS, null),
                bindings);
        return mapPreview(database, result);
    }

    private ExecutionResult executeDbtScreenTable(QueryContract contract) throws SQLException {
        AnalyticsDatabase database = resolveDatabase(firstNonBlank(contract.databaseName(), DEFAULT_DBT_DATABASE_NAME));
        String dbtModel = trimToNull(contract.primaryDbtModel());
        if (dbtModel == null) {
            throw new IllegalArgumentException("DBT screen template does not define a primary dbt model");
        }
        String relation = validateDbtRelation(dbtModel);
        String sql = "SELECT "
                + buildDbtSelectClause(contract.outputColumns())
                + " FROM "
                + relation
                + buildDbtOrderByClause(contract.orderBy())
                + " LIMIT "
                + PREVIEW_LIMIT;
        DatasetResult result = datasetQueryService.runNative(
                database.getId(),
                sql,
                new DatasetConstraints(PREVIEW_LIMIT, QUERY_TIMEOUT_SECONDS, null),
                List.of());
        return mapPreview(database, result);
    }

    private ExecutionResult executeProcurementOrderExecutionProgress(QueryContract contract, Map<String, Object> parameters)
            throws SQLException {
        AnalyticsDatabase database = resolveDatabase(contract.databaseName());
        StringBuilder sql = new StringBuilder("""
                SELECT
                    COALESCE(b.purchase_user_name, '') AS purchaseUserName,
                    DATE_FORMAT(a.purchase_time, '%Y-%m-%d %H:%i:%s') AS purchaseTime,
                    COALESCE(a.good_name, '') AS goodName,
                    COALESCE(a.good_specs, '') AS goodSpecs,
                    COALESCE(c.real_purchase_number, 0) AS parchaseNumber,
                    COALESCE(a.good_unit, '') AS goodUnit,
                    ROUND(COALESCE(a.parchase_price, 0), 4) AS parchasePrice,
                    ROUND(COALESCE(c.real_purchase_number, 0) * COALESCE(a.parchase_price, 0), 4) AS totalPrice,
                    CASE a.pay_type
                        WHEN 1 THEN '现金'
                        WHEN 2 THEN '挂账'
                        ELSE ''
                    END AS payTypeName,
                    COALESCE(a.supply_name, '') AS supplyName,
                    COALESCE(f.code, '') AS bizCode,
                    COALESCE(f.project_name, '') AS projectName
                FROM t_purchase_price_item a
                LEFT JOIN t_purchase_info b ON a.purchase_info_id = b.id
                LEFT JOIN t_plan_purchase_item c ON c.purchase_price_id = a.id
                LEFT JOIN t_flower_biz_item d ON d.id = c.flower_item_id
                LEFT JOIN t_flower_biz_info f ON f.id = d.flower_biz_id
                WHERE d.status <> -1
                  AND d.id IS NOT NULL
                  AND c.status <> -1
                """);
        List<Object> bindings = new ArrayList<>();

        String purchaseUserId = stringParam(parameters, "purchaseUserId");
        if (purchaseUserId != null) {
            sql.append(" AND b.purchase_user_id = ?");
            bindings.add(purchaseUserId);
        }

        String projectId = stringParam(parameters, "projectId");
        if (projectId != null) {
            sql.append(" AND f.project_id = ?");
            bindings.add(projectId);
        }

        String bizCode = stringParam(parameters, "bizCode");
        if (bizCode != null) {
            sql.append(" AND f.code LIKE ?");
            bindings.add('%' + bizCode + '%');
        }

        String goodName = stringParam(parameters, "goodName");
        if (goodName != null) {
            sql.append(" AND a.good_name LIKE ?");
            bindings.add('%' + goodName + '%');
        }

        String goodSpecs = stringParam(parameters, "goodSpecs");
        if (goodSpecs != null) {
            sql.append(" AND a.good_specs LIKE ?");
            bindings.add('%' + goodSpecs + '%');
        }

        String supplyName = stringParam(parameters, "supplyName");
        if (supplyName != null) {
            sql.append(" AND a.supply_name LIKE ?");
            bindings.add('%' + supplyName + '%');
        }

        String startTime = stringParam(parameters, "startTime");
        if (startTime != null) {
            sql.append(" AND a.purchase_time >= CONCAT(?, ' 00:00:00')");
            bindings.add(startTime);
        }

        String endTime = stringParam(parameters, "endTime");
        if (endTime != null) {
            sql.append(" AND a.purchase_time <= CONCAT(?, ' 23:59:59')");
            bindings.add(endTime);
        }

        String payType = stringParam(parameters, "payType");
        if (payType != null) {
            sql.append(" AND a.pay_type = ?");
            bindings.add(payType);
        }

        sql.append("""

                ORDER BY a.purchase_time DESC, f.project_id DESC, a.id DESC
                """);

        DatasetResult result = datasetQueryService.runNative(
                database.getId(),
                sql.toString(),
                new DatasetConstraints(PREVIEW_LIMIT, QUERY_TIMEOUT_SECONDS, null),
                bindings);
        return mapPreview(database, result);
    }

    private ExecutionResult executeWarehouseStockOverview(QueryContract contract, Map<String, Object> parameters)
            throws SQLException {
        AnalyticsDatabase database = resolveDatabase(contract.databaseName());
        StringBuilder sql = new StringBuilder("""
                SELECT
                    a.storehouse_name AS storehouseName,
                    a.good_name AS goodName,
                    a.good_specs AS goodSpecs,
                    a.good_number AS goodNumber,
                    ROUND(COALESCE(a.out_cost, 0), 4) AS outCost,
                    ROUND(COALESCE(gp.guidance_price, 0), 2) AS salePrice
                FROM s_stock_info a
                LEFT JOIN b_goods_price gp ON gp.id = a.good_price_id
                WHERE a.del_flag = '0'
                """);
        List<Object> bindings = new ArrayList<>();

        String storehouseInfoId = stringParam(parameters, "storehouseInfoId");
        if (storehouseInfoId != null) {
            sql.append(" AND a.storehouse_info_id = ?");
            bindings.add(storehouseInfoId);
        }

        String status = stringParam(parameters, "status");
        if (status != null) {
            sql.append(" AND a.status = ?");
            bindings.add(status);
        }

        String goodType = stringParam(parameters, "goodType");
        if (goodType != null) {
            sql.append(" AND a.good_type = ?");
            bindings.add(goodType);
        }

        String goodName = stringParam(parameters, "goodName");
        if (goodName != null) {
            sql.append(" AND a.good_name LIKE ?");
            bindings.add('%' + goodName + '%');
        }

        String goodSpecs = stringParam(parameters, "goodSpecs");
        if (goodSpecs != null) {
            sql.append(" AND a.good_specs LIKE ?");
            bindings.add('%' + goodSpecs + '%');
        }

        String passNumber = stringParam(parameters, "passNumber");
        if (passNumber != null) {
            sql.append(" AND a.good_number >= ?");
            bindings.add(passNumber);
        }

        String underNumber = stringParam(parameters, "underNumber");
        if (underNumber != null) {
            sql.append(" AND a.good_number <= ?");
            bindings.add(underNumber);
        }

        sql.append("""

                ORDER BY a.good_name DESC, a.good_norms DESC, a.good_specs DESC, a.good_number DESC
                """);

        DatasetResult result = datasetQueryService.runNative(
                database.getId(),
                sql.toString(),
                new DatasetConstraints(PREVIEW_LIMIT, QUERY_TIMEOUT_SECONDS, null),
                bindings);
        return mapPreview(database, result);
    }

    private ExecutionResult executeWarehouseLowStockAlert(QueryContract contract, Map<String, Object> parameters)
            throws SQLException {
        AnalyticsDatabase database = resolveDatabase(contract.databaseName());
        StringBuilder sql = new StringBuilder("""
                SELECT
                    a.storehouse_name AS storehouseName,
                    a.good_name AS goodName,
                    COALESCE(a.good_norms, '') AS goodNorms,
                    COALESCE(a.good_specs, '') AS goodSpecs,
                    a.good_number AS goodNumber,
                    ROUND(COALESCE(a.out_cost, 0), 4) AS outCost,
                    ROUND(COALESCE(gp.guidance_price, 0), 2) AS salePrice,
                    COALESCE(a.leader_user_name, '') AS leaderUserName
                FROM s_stock_info a
                LEFT JOIN b_goods_price gp ON gp.id = a.good_price_id
                WHERE a.del_flag = '0'
                """);
        List<Object> bindings = new ArrayList<>();

        String storehouseInfoId = stringParam(parameters, "storehouseInfoId");
        if (storehouseInfoId != null) {
            sql.append(" AND a.storehouse_info_id = ?");
            bindings.add(storehouseInfoId);
        }

        String goodType = stringParam(parameters, "goodType");
        if (goodType != null) {
            sql.append(" AND a.good_type = ?");
            bindings.add(goodType);
        }

        String goodName = stringParam(parameters, "goodName");
        if (goodName != null) {
            sql.append(" AND a.good_name LIKE ?");
            bindings.add('%' + goodName + '%');
        }

        String goodSpecs = stringParam(parameters, "goodSpecs");
        if (goodSpecs != null) {
            sql.append(" AND a.good_specs LIKE ?");
            bindings.add('%' + goodSpecs + '%');
        }

        String underNumber = stringParam(parameters, "underNumber");
        if (underNumber == null) {
            underNumber = "10";
        }
        sql.append(" AND a.good_number <= ?");
        bindings.add(underNumber);

        String status = stringParam(parameters, "status");
        if (status != null) {
            sql.append(" AND a.status = ?");
            bindings.add(status);
        }

        sql.append("""

                ORDER BY a.good_number ASC, a.storehouse_name ASC, a.good_name ASC, a.good_specs ASC
                """);

        DatasetResult result = datasetQueryService.runNative(
                database.getId(),
                sql.toString(),
                new DatasetConstraints(PREVIEW_LIMIT, QUERY_TIMEOUT_SECONDS, null),
                bindings);
        return mapPreview(database, result);
    }

    private ExecutionResult executeFinanceSettlementSummary(QueryContract contract, Map<String, Object> parameters)
            throws SQLException {
        AnalyticsDatabase database = resolveDatabase(contract.databaseName());
        StringBuilder sql = new StringBuilder("""
                SELECT
                    a.account_period AS accountPeriod,
                    a.project_name AS projectName,
                    COALESCE(a.biz_type_name, '') AS bizTypeName,
                    COALESCE(a.subject_full_name, '') AS subjectFullName,
                    COALESCE(a.fee_name, '') AS feeName,
                    ROUND(COALESCE(a.fee_number, 0), 4) AS feeNumber,
                    CASE a.fee_belong
                        WHEN 1 THEN '养护人员'
                        WHEN 2 THEN '负责人'
                        WHEN 3 THEN '公司'
                        ELSE '客户'
                    END AS feeBelongName,
                    CASE a.settle_handle
                        WHEN 0 THEN '无需处理'
                        WHEN 1 THEN '成本/采购报销'
                        WHEN 2 THEN '成本/采购冲抵备用金'
                        WHEN 3 THEN '成本/供应商挂帐'
                        WHEN 4 THEN '成本/养护工资挂帐'
                        WHEN 5 THEN '成本/项目备品成本挂帐'
                        WHEN 6 THEN '收入/项目销售额挂帐'
                        WHEN 7 THEN '收入/项目租摆费挂帐'
                        WHEN 8 THEN '收入/项目经理挂帐'
                        ELSE ''
                    END AS settleHandleName,
                    COALESCE(a.fee_user_name, '') AS feeUserName,
                    COALESCE(a.remark, '') AS remark
                FROM f_settled_items a
                WHERE a.status > 0
                """);
        List<Object> bindings = new ArrayList<>();

        String accountPeriod = stringParam(parameters, "accountPeriod");
        if (accountPeriod != null) {
            sql.append(" AND a.account_period = ?");
            bindings.add(accountPeriod);
        }

        String projectId = stringParam(parameters, "projectId");
        if (projectId != null) {
            sql.append(" AND a.project_id = ?");
            bindings.add(projectId);
        }

        String feeUserId = stringParam(parameters, "feeUserId");
        if (feeUserId != null) {
            sql.append(" AND a.fee_user_id = ?");
            bindings.add(feeUserId);
        }

        String status = stringParam(parameters, "status");
        if (status != null) {
            sql.append(" AND a.status = ?");
            bindings.add(status);
        }

        sql.append("""

                ORDER BY a.account_period ASC, a.biz_type ASC, a.biz_id ASC, a.biz_type_name ASC,
                         a.settle_type ASC, a.fee_user_id DESC, a.fee_type ASC, a.fee_user_id ASC
                """);

        DatasetResult result = datasetQueryService.runNative(
                database.getId(),
                sql.toString(),
                new DatasetConstraints(PREVIEW_LIMIT, QUERY_TIMEOUT_SECONDS, null),
                bindings);
        return mapPreview(database, result);
    }

    private ExecutionResult executeFinanceAdvanceRequestStatus(QueryContract contract, Map<String, Object> parameters)
            throws SQLException {
        AnalyticsDatabase database = resolveDatabase(contract.databaseName());
        StringBuilder sql = new StringBuilder("""
                SELECT
                    COALESCE(a.apply_user_name, '') AS applyUserName,
                    COALESCE(a.code, '') AS code,
                    CASE a.type
                        WHEN 1 THEN '工资'
                        WHEN 2 THEN '任务'
                        WHEN 3 THEN '备用金'
                        ELSE ''
                    END AS typeName,
                    ROUND(COALESCE(a.total_amount, 0), 4) AS totalAmount,
                    ROUND(COALESCE(a.pay_amount, 0), 4) AS payAmount,
                    ROUND(COALESCE(a.offset_total_amount, 0), 4) AS offsetTotalAmount,
                    CASE a.status
                        WHEN 1 THEN '草稿'
                        WHEN 2 THEN '审批中'
                        WHEN 3 THEN '待付款'
                        WHEN 4 THEN '待冲抵'
                        WHEN 5 THEN '超额审批中'
                        WHEN 6 THEN '超额付款中'
                        WHEN 7 THEN '出纳确认中'
                        WHEN 9 THEN '待提供发票'
                        WHEN 10 THEN '已结束'
                        WHEN 20 THEN '审批拒绝'
                        WHEN -1 THEN '已作废'
                        ELSE ''
                    END AS statusName,
                    CASE a.invoice_status
                        WHEN 1 THEN '无'
                        WHEN 2 THEN '有'
                        ELSE ''
                    END AS invoiceStatusName,
                    CASE a.invoice_offer
                        WHEN 1 THEN '未提供'
                        WHEN 2 THEN '已提供'
                        ELSE ''
                    END AS invoiceOfferName,
                    COALESCE(a.title, '') AS title,
                    DATE_FORMAT(a.apply_time, '%Y-%m-%d %H:%i:%s') AS applyTime,
                    DATE_FORMAT(a.pay_time, '%Y-%m-%d %H:%i:%s') AS payTime
                FROM f_advance_info a
                WHERE 1 = 1
                """);
        List<Object> bindings = new ArrayList<>();

        String code = stringParam(parameters, "code");
        if (code != null) {
            sql.append(" AND a.code LIKE ?");
            bindings.add('%' + code + '%');
        }

        String status = stringParam(parameters, "status");
        if (status != null) {
            sql.append(" AND a.status = ?");
            bindings.add(status);
        }

        String applyUserId = stringParam(parameters, "applyUserId");
        if (applyUserId != null) {
            sql.append(" AND a.apply_user_id = ?");
            bindings.add(applyUserId);
        }

        sql.append("""

                ORDER BY a.apply_time DESC, a.id DESC
                """);

        DatasetResult result = datasetQueryService.runNative(
                database.getId(),
                sql.toString(),
                new DatasetConstraints(PREVIEW_LIMIT, QUERY_TIMEOUT_SECONDS, null),
                bindings);
        return mapPreview(database, result);
    }

    private ExecutionResult executeFinanceReimbursementStatus(QueryContract contract, Map<String, Object> parameters)
            throws SQLException {
        AnalyticsDatabase database = resolveDatabase(contract.databaseName());
        StringBuilder sql = new StringBuilder("""
                SELECT
                    COALESCE(a.code, '') AS code,
                    COALESCE(a.title, '') AS title,
                    COALESCE(a.apply_user_name, '') AS applyUserName,
                    DATE_FORMAT(a.apply_time, '%Y-%m-%d %H:%i:%s') AS applyTime,
                    CASE a.status
                        WHEN -1 THEN '已作废'
                        WHEN 0 THEN '草稿'
                        WHEN 1 THEN '审核中'
                        WHEN 2 THEN '待付款'
                        WHEN 3 THEN '已结束'
                        WHEN 9 THEN '待提供发票'
                        WHEN 20 THEN '已驳回'
                        ELSE ''
                    END AS statusName,
                    ROUND(COALESCE(a.total_amount, 0), 2) AS totalAmount,
                    CASE a.invoice_status
                        WHEN 1 THEN '无'
                        WHEN 2 THEN '有'
                        ELSE ''
                    END AS invoiceStatusName,
                    CASE a.invoice_offer
                        WHEN 1 THEN '未提供'
                        WHEN 2 THEN '已提供'
                        ELSE ''
                    END AS invoiceOfferName,
                    DATE_FORMAT(a.pay_time, '%Y-%m-%d %H:%i:%s') AS payTime,
                    CASE a.pay_type
                        WHEN 1 THEN '转账'
                        WHEN 2 THEN '现金'
                        WHEN 3 THEN '汇款'
                        ELSE ''
                    END AS payTypeName,
                    COALESCE(a.collect_name, '') AS collectName,
                    COALESCE(a.remark, '') AS remark
                FROM f_expense_account_info a
                WHERE a.expense_type = 2
                """);
        List<Object> bindings = new ArrayList<>();

        String code = stringParam(parameters, "code");
        if (code != null) {
            sql.append(" AND a.code LIKE ?");
            bindings.add('%' + code + '%');
        }

        String status = stringParam(parameters, "status");
        if (status != null) {
            sql.append(" AND a.status = ?");
            bindings.add(status);
        }

        String applyUserId = stringParam(parameters, "applyUserId");
        if (applyUserId != null) {
            sql.append(" AND a.apply_user_id = ?");
            bindings.add(applyUserId);
        }

        String collectName = stringParam(parameters, "collectName");
        if (collectName != null) {
            sql.append(" AND a.collect_name LIKE ?");
            bindings.add('%' + collectName + '%');
        }

        String payType = stringParam(parameters, "payType");
        if (payType != null) {
            sql.append(" AND a.pay_type = ?");
            bindings.add(payType);
        }

        String remark = stringParam(parameters, "remark");
        if (remark != null) {
            sql.append(" AND a.remark LIKE ?");
            bindings.add('%' + remark + '%');
        }

        sql.append("""

                ORDER BY a.apply_time DESC, a.id DESC
                """);

        DatasetResult result = datasetQueryService.runNative(
                database.getId(),
                sql.toString(),
                new DatasetConstraints(PREVIEW_LIMIT, QUERY_TIMEOUT_SECONDS, null),
                bindings);
        return mapPreview(database, result);
    }

    private ExecutionResult executeFinanceInvoiceReconciliation(QueryContract contract, Map<String, Object> parameters)
            throws SQLException {
        AnalyticsDatabase database = resolveDatabase(contract.databaseName());
        StringBuilder sql = new StringBuilder("""
                SELECT
                    COALESCE(a.project_name, '') AS projectName,
                    CASE a.bill_type
                        WHEN 1 THEN '租摆账单'
                        WHEN 2 THEN '销售账单'
                        WHEN 3 THEN '个人销售账单'
                        WHEN 4 THEN '预收'
                        ELSE ''
                    END AS billTypeName,
                    CASE a.status
                        WHEN -1 THEN '已作废'
                        WHEN 1 THEN '审核中'
                        WHEN 2 THEN '待开票'
                        WHEN 3 THEN '待领取'
                        WHEN 4 THEN '待回款'
                        WHEN 10 THEN '已结束'
                        WHEN 20 THEN '已驳回'
                        ELSE ''
                    END AS statusName,
                    ROUND(COALESCE(a.apply_invoice_amoney, 0), 4) AS applyInvoiceAmoney,
                    ROUND(COALESCE(a.invoice_amoney, 0), 4) AS invoiceAmoney,
                    ROUND(COALESCE(a.pay_total_amount, 0), 4) AS payTotalAmount,
                    COALESCE(a.apply_user_name, '') AS applyUserName,
                    DATE_FORMAT(a.apply_time, '%Y-%m-%d %H:%i:%s') AS applyTime,
                    COALESCE(a.invoice_user_name, '') AS invoiceUserName,
                    DATE_FORMAT(a.invoice_time, '%Y-%m-%d %H:%i:%s') AS invoiceTime,
                    COALESCE(a.code, '') AS code,
                    COALESCE(a.title, '') AS title
                FROM a_invoice_info a
                WHERE 1 = 1
                """);
        List<Object> bindings = new ArrayList<>();

        String projectId = stringParam(parameters, "projectId");
        if (projectId != null) {
            sql.append(" AND a.project_id = ?");
            bindings.add(projectId);
        }

        String status = stringParam(parameters, "status");
        if (status != null) {
            sql.append(" AND a.status = ?");
            bindings.add(status);
        }

        String billType = stringParam(parameters, "billType");
        if (billType != null) {
            sql.append(" AND a.bill_type = ?");
            bindings.add(billType);
        }

        String code = stringParam(parameters, "code");
        if (code != null) {
            sql.append(" AND a.code LIKE ?");
            bindings.add('%' + code + '%');
        }

        String itemTitle = stringParam(parameters, "itemTitle");
        if (itemTitle != null) {
            sql.append("""
                     AND EXISTS (
                        SELECT 1
                        FROM a_invoice_item ai
                        WHERE ai.invoice_info_id = a.id
                          AND ai.biz_code LIKE ?
                    )
                    """);
            bindings.add('%' + itemTitle + '%');
        }

        String applyUserId = stringParam(parameters, "applyUserId");
        if (applyUserId != null) {
            sql.append(" AND a.apply_user_id = ?");
            bindings.add(applyUserId);
        }

        String applyStartTime = stringParam(parameters, "applyStartTime");
        if (applyStartTime != null) {
            sql.append(" AND a.apply_time >= CONCAT(?, ' 00:00:00')");
            bindings.add(applyStartTime);
        }

        String applyEndTime = stringParam(parameters, "applyEndTime");
        if (applyEndTime != null) {
            sql.append(" AND a.apply_time <= CONCAT(?, ' 23:59:59')");
            bindings.add(applyEndTime);
        }

        String invoiceStartTime = stringParam(parameters, "invoiceStartTime");
        if (invoiceStartTime != null) {
            sql.append(" AND a.invoice_time >= CONCAT(?, ' 00:00:00')");
            bindings.add(invoiceStartTime);
        }

        String invoiceEndTime = stringParam(parameters, "invoiceEndTime");
        if (invoiceEndTime != null) {
            sql.append(" AND a.invoice_time <= CONCAT(?, ' 23:59:59')");
            bindings.add(invoiceEndTime);
        }

        sql.append("""

                ORDER BY a.create_time DESC, a.id DESC
                """);

        DatasetResult result = datasetQueryService.runNative(
                database.getId(),
                sql.toString(),
                new DatasetConstraints(PREVIEW_LIMIT, QUERY_TIMEOUT_SECONDS, null),
                bindings);
        return mapPreview(database, result);
    }

    private ExecutionResult executeFinancePendingReceiptsDetail(QueryContract contract, Map<String, Object> parameters)
            throws SQLException {
        AnalyticsDatabase database = resolveDatabase(contract.databaseName());
        StringBuilder sql = new StringBuilder("""
                SELECT
                    ma.year_and_month AS accountPeriod,
                    ma.company_name AS customerName,
                    ma.project_name AS projectName,
                    ma.receivable_total_amount AS receivableAmount,
                    ma.net_receipt_total_amount AS receiptAmount,
                    (COALESCE(ma.receivable_total_amount, 0) - COALESCE(ma.net_receipt_total_amount, 0)) AS arrearsAmount,
                    ma.discount_rate AS discountRate,
                    ma.project_manage_user_name AS managerName,
                    ma.biz_user_name AS bizUserName
                FROM a_month_accounting ma
                WHERE ma.status = 1
                  AND (COALESCE(ma.receivable_total_amount, 0) - COALESCE(ma.net_receipt_total_amount, 0)) > 0
                """);
        List<Object> bindings = new ArrayList<>();

        String projectName = stringParam(parameters, "projectName");
        if (projectName != null) {
            sql.append(" AND ma.project_name LIKE ?");
            bindings.add('%' + projectName + '%');
        }

        String yearAndMonth = stringParam(parameters, "yearAndMonth");
        if (yearAndMonth != null) {
            sql.append(" AND ma.year_and_month = ?");
            bindings.add(yearAndMonth);
        }

        String companyName = stringParam(parameters, "companyName");
        if (companyName != null) {
            sql.append(" AND ma.company_name LIKE ?");
            bindings.add('%' + companyName + '%');
        }

        sql.append("""

                ORDER BY ma.year_and_month DESC, arrearsAmount DESC, ma.project_name ASC
                """);

        DatasetResult result = datasetQueryService.runNative(
                database.getId(),
                sql.toString(),
                new DatasetConstraints(PREVIEW_LIMIT, QUERY_TIMEOUT_SECONDS, null),
                bindings);
        return mapPreview(database, result);
    }

    private ExecutionResult executeFinanceProjectCollectionProgress(QueryContract contract, Map<String, Object> parameters)
            throws SQLException {
        AnalyticsDatabase database = resolveDatabase(contract.databaseName());
        StringBuilder sql = new StringBuilder("""
                SELECT
                    ma.project_name AS projectName,
                    ma.year_and_month AS accountPeriod,
                    ma.receivable_total_amount AS receivableAmount,
                    ma.net_receipt_total_amount AS receiptAmount,
                    (COALESCE(ma.receivable_total_amount, 0) - COALESCE(ma.net_receipt_total_amount, 0)) AS arrearsAmount,
                    CASE WHEN COALESCE(ma.receivable_total_amount, 0) > 0
                         THEN ROUND(COALESCE(ma.net_receipt_total_amount, 0) / ma.receivable_total_amount * 100, 1)
                         ELSE 0 END AS collectionRate,
                    COALESCE(cr.collectionRecordCount, 0) AS collectionRecordCount,
                    DATE_FORMAT(cr.lastCollectionTime, '%Y-%m-%d %H:%i:%s') AS lastCollectionTime,
                    ma.project_manage_user_name AS managerName
                FROM a_month_accounting ma
                LEFT JOIN (
                    SELECT
                        cr.project_id,
                        DATE_FORMAT(cr.pay_time, '%Y%m') AS accountPeriod,
                        COUNT(1) AS collectionRecordCount,
                        MAX(cr.pay_time) AS lastCollectionTime
                    FROM a_collection_record cr
                    WHERE cr.status = 1
                    GROUP BY cr.project_id, DATE_FORMAT(cr.pay_time, '%Y%m')
                ) cr ON cr.project_id = ma.project_id
                    AND cr.accountPeriod = ma.year_and_month
                WHERE 1 = 1
                """);
        List<Object> bindings = new ArrayList<>();

        String projectName = stringParam(parameters, "projectName");
        if (projectName != null) {
            sql.append(" AND ma.project_name LIKE ?");
            bindings.add('%' + projectName + '%');
        }

        String yearAndMonth = stringParam(parameters, "yearAndMonth");
        if (yearAndMonth != null) {
            sql.append(" AND ma.year_and_month = ?");
            bindings.add(yearAndMonth);
        }

        sql.append("""

                ORDER BY ma.project_name, ma.year_and_month DESC
                """);

        DatasetResult result = datasetQueryService.runNative(
                database.getId(),
                sql.toString(),
                new DatasetConstraints(PREVIEW_LIMIT, QUERY_TIMEOUT_SECONDS, null),
                bindings);
        return mapPreview(database, result);
    }

    private ExecutionResult executeFinanceCustomerArRank(QueryContract contract, Map<String, Object> parameters)
            throws SQLException {
        AnalyticsDatabase database = resolveDatabase(contract.databaseName());
        StringBuilder sql = new StringBuilder("""
                SELECT
                    ma.company_name AS customerName,
                    SUM(COALESCE(ma.receivable_total_amount, 0) - COALESCE(ma.net_receipt_total_amount, 0)) AS totalArrears,
                    COUNT(DISTINCT ma.project_id) AS projectCount,
                    SUM(ma.receivable_total_amount) AS totalReceivable,
                    SUM(ma.net_receipt_total_amount) AS totalReceipt
                FROM a_month_accounting ma
                WHERE (COALESCE(ma.receivable_total_amount, 0) - COALESCE(ma.net_receipt_total_amount, 0)) > 0
                """);
        List<Object> bindings = new ArrayList<>();

        String yearAndMonth = stringParam(parameters, "yearAndMonth");
        if (yearAndMonth != null) {
            sql.append(" AND ma.year_and_month = ?");
            bindings.add(yearAndMonth);
        }

        appendLikeIfPresent(sql, bindings, parameters, "companyName", " AND ma.company_name LIKE ?");

        sql.append("""

                GROUP BY ma.company_name
                ORDER BY totalArrears DESC
                """);

        DatasetResult result = datasetQueryService.runNative(
                database.getId(),
                sql.toString(),
                new DatasetConstraints(PREVIEW_LIMIT, QUERY_TIMEOUT_SECONDS, null),
                bindings);
        return mapPreview(database, result);
    }

    private ExecutionResult executeFinancePendingPaymentApproval(QueryContract contract, Map<String, Object> parameters)
            throws SQLException {
        AnalyticsDatabase database = resolveDatabase(contract.databaseName());
        StringBuilder sql = new StringBuilder("""
                SELECT
                    a.code AS code,
                    a.title AS title,
                    CASE a.business_type
                        WHEN 1 THEN '报销支出'
                        WHEN 2 THEN '工资支出'
                        WHEN 3 THEN '任务支出'
                        WHEN 4 THEN '备用金支出'
                        WHEN 7 THEN '采购报销支出'
                        ELSE ''
                    END AS businessTypeName,
                    CASE a.status
                        WHEN 1 THEN '未确认'
                        WHEN 2 THEN '已确认'
                        ELSE ''
                    END AS statusName,
                    a.drawee_user_name AS draweeUserName,
                    DATE_FORMAT(a.drawee_date, '%Y-%m-%d %H:%i:%s') AS draweeDate,
                    a.drawee_price AS draweePrice,
                    a.collection_name AS collectionName,
                    a.pay_bank_name AS payBankName,
                    CASE (
                        CASE a.business_type
                            WHEN 1 THEN c.invoice_status
                            WHEN 2 THEN b.invoice_status
                            WHEN 3 THEN b.invoice_status
                            WHEN 4 THEN b.invoice_status
                            WHEN 7 THEN c.invoice_status
                        END
                    )
                        WHEN 1 THEN '无'
                        WHEN 2 THEN '有'
                        ELSE ''
                    END AS invoiceStatusName,
                    CASE (
                        CASE a.business_type
                            WHEN 1 THEN c.invoice_offer
                            WHEN 2 THEN b.invoice_offer
                            WHEN 3 THEN b.invoice_offer
                            WHEN 4 THEN b.invoice_offer
                            WHEN 7 THEN c.invoice_offer
                        END
                    )
                        WHEN 1 THEN '未提供'
                        WHEN 2 THEN '已提供'
                        ELSE ''
                    END AS invoiceOfferName
                FROM f_pay_record a
                LEFT JOIN f_advance_info b on b.id=a.business_id
                LEFT JOIN f_expense_account_info c on c.id=a.business_id
                WHERE a.status = 1
                """);
        List<Object> bindings = new ArrayList<>();

        String code = stringParam(parameters, "code");
        if (code != null) {
            sql.append(" AND a.code LIKE ?");
            bindings.add('%' + code + '%');
        }

        String businessType = stringParam(parameters, "businessType");
        if (businessType != null) {
            sql.append(" AND a.business_type = ?");
            bindings.add(businessType);
        }

        String draweeId = stringParam(parameters, "draweeId");
        if (draweeId != null) {
            sql.append(" AND a.drawee_id = ?");
            bindings.add(draweeId);
        }

        String payBankName = stringParam(parameters, "payBankName");
        if (payBankName != null) {
            sql.append(" AND a.pay_bank_name LIKE ?");
            bindings.add('%' + payBankName + '%');
        }

        String invoiceStatus = stringParam(parameters, "invoiceStatus");
        if (invoiceStatus != null) {
            sql.append("""
                     AND (
                        CASE a.business_type
                            WHEN 1 THEN c.invoice_status
                            WHEN 2 THEN b.invoice_status
                            WHEN 3 THEN b.invoice_status
                            WHEN 4 THEN b.invoice_status
                            WHEN 7 THEN c.invoice_status
                        END
                     ) = ?
                    """);
            bindings.add(invoiceStatus);
        }

        String invoiceOffer = stringParam(parameters, "invoiceOffer");
        if (invoiceOffer != null) {
            sql.append("""
                     AND (
                        CASE a.business_type
                            WHEN 1 THEN c.invoice_offer
                            WHEN 2 THEN b.invoice_offer
                            WHEN 3 THEN b.invoice_offer
                            WHEN 4 THEN b.invoice_offer
                            WHEN 7 THEN c.invoice_offer
                        END
                     ) = ?
                    """);
            bindings.add(invoiceOffer);
        }

        sql.append("""

                ORDER BY a.drawee_date DESC, a.id DESC
                """);

        DatasetResult result = datasetQueryService.runNative(
                database.getId(),
                sql.toString(),
                new DatasetConstraints(PREVIEW_LIMIT, QUERY_TIMEOUT_SECONDS, null),
                bindings);
        return mapPreview(database, result);
    }

    private ExecutionResult executeProcurementPendingInboundList(QueryContract contract, Map<String, Object> parameters)
            throws SQLException {
        AnalyticsDatabase database = resolveDatabase(contract.databaseName());
        StringBuilder sql = new StringBuilder("""
                SELECT
                    sh.name AS storehouseName,
                    a.code AS code,
                    a.title AS title,
                    (SELECT SUM(b.good_number) FROM t_warehousing_item b WHERE a.id = b.warehousing_info_id) AS totalNumber,
                    CASE a.warehousing_type
                        WHEN 1 THEN '调花入库'
                        WHEN 2 THEN '报花入库'
                        WHEN 3 THEN '自主采购入库'
                        WHEN 4 THEN '回收入库'
                        WHEN 5 THEN '初摆采购入库'
                        WHEN 6 THEN '库存调整入库'
                        WHEN 7 THEN '其他入库'
                        ELSE ''
                    END AS warehousingTypeName,
                    COALESCE(
                        (SELECT b.code FROM t_allocation b WHERE a.biz_id = b.id LIMIT 1),
                        (SELECT b.code FROM t_flower_biz_info b WHERE a.biz_id = b.id LIMIT 1),
                        (SELECT b.code FROM t_self_mining_info b WHERE a.biz_id = b.id LIMIT 1),
                        (SELECT b.code FROM t_ex_warehouse_info b WHERE a.biz_id = b.id LIMIT 1)
                    ) AS realBizCode,
                    CASE a.status
                        WHEN 1 THEN '待入库'
                        WHEN 2 THEN '已入库'
                        WHEN -1 THEN '已作废'
                        ELSE ''
                    END AS statusName,
                    a.warehousing_user_name AS warehousingUserName,
                    DATE_FORMAT(a.warehousing_time, '%Y-%m-%d %H:%i:%s') AS warehousingTime,
                    DATE_FORMAT(a.create_time, '%Y-%m-%d %H:%i:%s') AS createTime,
                    CASE a.print_status
                        WHEN 1 THEN '已打印'
                        ELSE '未打印'
                    END AS printStatusName,
                    a.remark AS remark
                FROM t_warehousing_info a
                LEFT JOIN s_storehouse_info sh ON sh.id = a.storehouse_info_id
                WHERE a.del_flag = '0'
                """);
        List<Object> bindings = new ArrayList<>();

        String status = stringParam(parameters, "status");
        if (status != null) {
            sql.append(" AND a.status = ?");
            bindings.add(status);
        }

        String storehouseInfoId = stringParam(parameters, "storehouseInfoId");
        if (storehouseInfoId != null) {
            sql.append(" AND a.storehouse_info_id = ?");
            bindings.add(storehouseInfoId);
        }

        String code = stringParam(parameters, "code");
        if (code != null) {
            sql.append(" AND a.code LIKE ?");
            bindings.add('%' + code + '%');
        }

        String goodName = stringParam(parameters, "goodName");
        if (goodName != null) {
            sql.append(" AND EXISTS(select * from t_warehousing_item b where a.id=b.warehousing_info_id and b.good_name like ?)");
            bindings.add('%' + goodName + '%');
        }

        String goodSpecs = stringParam(parameters, "goodSpecs");
        if (goodSpecs != null) {
            sql.append(" AND EXISTS(select * from t_warehousing_item b where a.id=b.warehousing_info_id and b.good_specs like ?)");
            bindings.add('%' + goodSpecs + '%');
        }

        String bizCode = stringParam(parameters, "bizCode");
        if (bizCode != null) {
            sql.append("""
                     AND COALESCE(
                        (SELECT b.code FROM t_allocation b WHERE a.biz_id = b.id LIMIT 1),
                        (SELECT b.code FROM t_flower_biz_info b WHERE a.biz_id = b.id LIMIT 1),
                        (SELECT b.code FROM t_self_mining_info b WHERE a.biz_id = b.id LIMIT 1),
                        (SELECT b.code FROM t_ex_warehouse_info b WHERE a.biz_id = b.id LIMIT 1)
                     ) LIKE ?
                    """);
            bindings.add('%' + bizCode + '%');
        }

        sql.append("""

                ORDER BY a.create_time DESC, a.id DESC
                """);

        DatasetResult result = datasetQueryService.runNative(
                database.getId(),
                sql.toString(),
                new DatasetConstraints(PREVIEW_LIMIT, QUERY_TIMEOUT_SECONDS, null),
                bindings);
        return mapPreview(database, result);
    }

    private ExecutionResult executeProcurementRequestTodo(QueryContract contract, Map<String, Object> parameters)
            throws SQLException {
        AnalyticsDatabase database = resolveDatabase(contract.databaseName());
        StringBuilder sql = new StringBuilder("""
                SELECT
                    b.project_name AS projectName,
                    COALESCE(fi.position_full_name, p.region_full, p.region, fi.position_name, '') AS positionFullName,
                    c.code AS flowerCode,
                    c.title AS flowerTitle,
                    a.good_name AS goodName,
                    a.good_specs AS goodSpecs,
                    a.plan_purchase_number AS planPurchaseNumber,
                    a.real_purchase_number AS realPurchaseNumber,
                    pp.parchase_price AS purchasePrice,
                    DATE_FORMAT(pp.purchase_time, '%Y-%m-%d %H:%i:%s') AS purchaseTime,
                    CASE a.status
                        WHEN 1 THEN '待采购'
                        WHEN 2 THEN '已采购'
                        ELSE ''
                    END AS statusName,
                    b.purchase_user_name AS purchaseUserName,
                    DATE_FORMAT(b.plant_purchase_time, '%Y-%m-%d %H:%i:%s') AS planPurchaseTime,
                    b.distribution_user_name AS distributionUserName,
                    DATE_FORMAT(b.distribution_time, '%Y-%m-%d %H:%i:%s') AS distributionTime
                FROM t_plan_purchase_item a
                LEFT JOIN t_plan_purchase_info b ON b.id = a.plan_purchase_info_id
                LEFT JOIN t_flower_biz_info c ON c.id = b.flower_biz_info_id
                LEFT JOIN t_flower_biz_item fi ON fi.id = a.flower_item_id
                LEFT JOIN p_position p ON p.id = fi.position_id
                LEFT JOIN t_purchase_price_item pp ON pp.id = a.purchase_price_id
                WHERE b.del_flag = '0'
                """);
        List<Object> bindings = new ArrayList<>();

        String status = stringParam(parameters, "status");
        if (status != null) {
            sql.append(" AND a.status = ?");
            bindings.add(status);
        }

        String projectId = stringParam(parameters, "projectId");
        if (projectId != null) {
            sql.append(" AND b.project_id = ?");
            bindings.add(projectId);
        }

        String purchaseUserId = stringParam(parameters, "purchaseUserId");
        if (purchaseUserId != null) {
            sql.append(" AND b.purchase_user_id = ?");
            bindings.add(purchaseUserId);
        }

        String flowerCode = stringParam(parameters, "flowerCode");
        if (flowerCode != null) {
            sql.append(" AND c.code LIKE ?");
            bindings.add('%' + flowerCode + '%');
        }

        String goodName = stringParam(parameters, "goodName");
        if (goodName != null) {
            sql.append(" AND a.good_name LIKE ?");
            bindings.add('%' + goodName + '%');
        }

        sql.append("""

                ORDER BY b.create_time DESC, a.id DESC
                """);

        DatasetResult result = datasetQueryService.runNative(
                database.getId(),
                sql.toString(),
                new DatasetConstraints(PREVIEW_LIMIT, QUERY_TIMEOUT_SECONDS, null),
                bindings);
        return mapPreview(database, result);
    }

    private ExecutionResult executeProcurementIntransitBoard(QueryContract contract, Map<String, Object> parameters)
            throws SQLException {
        AnalyticsDatabase database = resolveDatabase(contract.databaseName());
        StringBuilder sql = new StringBuilder("""
                SELECT
                    t.project_name AS projectName,
                    t.biz_code AS bizCode,
                    t.code AS deliveryCode,
                    t.totalNumber AS totalNumber,
                    CASE t.status
                        WHEN 1 THEN '配送中'
                        WHEN 2 THEN '运费确认中'
                        WHEN 3 THEN '待接收'
                        WHEN 4 THEN '已结束'
                        WHEN -1 THEN '已作废'
                        ELSE ''
                    END AS statusName,
                    CASE t.delivery_mode
                        WHEN 1 THEN '市场->项目点'
                        WHEN 2 THEN '库房->项目点'
                        WHEN 3 THEN '库房->库房'
                        WHEN 4 THEN '项目点->库房'
                        WHEN 5 THEN '库房->项目点'
                        WHEN 6 THEN '库房->项目点'
                        WHEN 7 THEN '市场->库房'
                        ELSE ''
                    END AS deliveryModeName,
                    t.delivery_user_name AS deliveryUserName,
                    t.receive_user_name AS receiveUserName,
                    DATE_FORMAT(t.start_delivery_time, '%Y-%m-%d %H:%i:%s') AS startDeliveryTime,
                    DATE_FORMAT(t.receive_time, '%Y-%m-%d %H:%i:%s') AS receiveTime
                FROM (
                    SELECT
                        b.code AS biz_code,
                        b.project_name AS project_name,
                        b.project_id AS project_id,
                        ( SELECT sum( c.good_number ) FROM t_delivery_item c WHERE c.delivery_info_id = a.id ) AS totalNumber,
                        a.*
                    FROM t_delivery_info a
                    LEFT JOIN t_flower_biz_info b ON a.biz_id = b.id
                    WHERE a.delivery_mode IN (1, 2, 4)
                    UNION ALL
                    SELECT
                        b.code AS biz_code,
                        NULL AS project_name,
                        NULL AS project_id,
                        ( SELECT sum( c.good_number ) FROM t_delivery_item c WHERE c.delivery_info_id = a.id ) AS totalNumber,
                        a.*
                    FROM t_delivery_info a
                    LEFT JOIN t_allocation b ON a.biz_id = b.id
                    WHERE a.delivery_mode IN (3)
                    UNION ALL
                    SELECT
                        b.code AS biz_code,
                        NULL AS project_name,
                        NULL AS project_id,
                        ( SELECT sum( c.good_number ) FROM t_delivery_item c WHERE c.delivery_info_id = a.id ) AS totalNumber,
                        a.*
                    FROM t_delivery_info a
                    LEFT JOIN t_self_mining_info b ON a.biz_id = b.id
                    WHERE a.delivery_mode IN (7)
                ) t
                WHERE t.del_flag = '0'
                """);
        List<Object> bindings = new ArrayList<>();

        String projectId = stringParam(parameters, "projectId");
        if (projectId != null) {
            sql.append(" AND t.project_id = ?");
            bindings.add(projectId);
        }

        String status = stringParam(parameters, "status");
        if (status != null) {
            sql.append(" AND t.status = ?");
            bindings.add(status);
        }

        String code = stringParam(parameters, "code");
        if (code != null) {
            sql.append(" AND t.code like ?");
            bindings.add('%' + code + '%');
        }

        String bizCode = stringParam(parameters, "bizCode");
        if (bizCode != null) {
            sql.append(" AND t.biz_code like ?");
            bindings.add('%' + bizCode + '%');
        }

        String receiveUserId = stringParam(parameters, "receiveUserId");
        if (receiveUserId != null) {
            sql.append(" AND t.receive_user_id = ?");
            bindings.add(receiveUserId);
        }

        sql.append("""

                ORDER BY t.start_delivery_time DESC, t.id DESC
                """);

        DatasetResult result = datasetQueryService.runNative(
                database.getId(),
                sql.toString(),
                new DatasetConstraints(PREVIEW_LIMIT, QUERY_TIMEOUT_SECONDS, null),
                bindings);
        return mapPreview(database, result);
    }

    private ExecutionResult executeProcurementArrivalOntimeRate(QueryContract contract, Map<String, Object> parameters)
            throws SQLException {
        AnalyticsDatabase database = resolveDatabase(contract.databaseName());
        StringBuilder sql = new StringBuilder("""
                SELECT
                    DATE_FORMAT(a.start_delivery_time, '%Y-%m-%d') AS statDate,
                    COUNT(*) AS totalDeliveries,
                    SUM(CASE
                        WHEN a.receive_time IS NOT NULL
                         AND TIMESTAMPDIFF(HOUR, a.start_delivery_time, a.receive_time) <= 24
                        THEN 1 ELSE 0 END) AS ontimeDeliveries,
                    ROUND(
                        SUM(CASE
                            WHEN a.receive_time IS NOT NULL
                             AND TIMESTAMPDIFF(HOUR, a.start_delivery_time, a.receive_time) <= 24
                            THEN 1 ELSE 0 END) / COUNT(*) * 100,
                        1
                    ) AS ontimeRate
                FROM t_delivery_info a
                WHERE a.del_flag = '0'
                  AND a.start_delivery_time IS NOT NULL
                """);
        List<Object> bindings = new ArrayList<>();

        String startDate = stringParam(parameters, "startDate");
        if (startDate != null) {
            sql.append(" AND DATE_FORMAT(a.start_delivery_time, '%Y-%m-%d') >= ?");
            bindings.add(startDate);
        }

        String endDate = stringParam(parameters, "endDate");
        if (endDate != null) {
            sql.append(" AND DATE_FORMAT(a.start_delivery_time, '%Y-%m-%d') <= ?");
            bindings.add(endDate);
        }

        sql.append("""

                GROUP BY DATE_FORMAT(a.start_delivery_time, '%Y-%m-%d')
                ORDER BY statDate DESC
                """);

        DatasetResult result = datasetQueryService.runNative(
                database.getId(),
                sql.toString(),
                new DatasetConstraints(PREVIEW_LIMIT, QUERY_TIMEOUT_SECONDS, null),
                bindings);
        return mapPreview(database, result);
    }

    private AnalyticsDatabase resolveDatabase(String preferredDatabaseName) {
        AnalyticsDatabaseRole preferredRole = parseDatabaseRoleSelector(preferredDatabaseName);
        if (preferredRole != null) {
            return databaseRepository.findAll().stream()
                    .filter(db -> db.getDatabaseRole() == preferredRole)
                    .findFirst()
                    .or(() -> databaseRepository.findAll().stream()
                            .filter(this::isBusinessOperationalDatabase)
                            .findFirst())
                    .orElseThrow(() -> new IllegalArgumentException("Fixed report database not found for role: " + preferredRole));
        }
        String databaseName = trimToNull(preferredDatabaseName) == null ? DEFAULT_DATABASE_NAME : preferredDatabaseName.trim();
        AnalyticsDatabase preferred = databaseRepository.findFirstByNameIgnoreCase(databaseName).orElse(null);
        if (preferred != null && preferred.getDatabaseRole() != AnalyticsDatabaseRole.SYSTEM_RUNTIME) {
            return preferred;
        }
        return databaseRepository.findAll().stream()
                .filter(db -> db.getDatabaseRole() == AnalyticsDatabaseRole.BUSINESS_PRIMARY)
                .findFirst()
                .or(() -> databaseRepository.findAll().stream()
                .filter(this::isBusinessOperationalDatabase)
                .findFirst())
                .orElseThrow(() -> new IllegalArgumentException("Fixed report database not found: " + databaseName));
    }

    private boolean isBusinessOperationalDatabase(AnalyticsDatabase database) {
        if (database == null) {
            return false;
        }
        if (database.getDatabaseRole() == AnalyticsDatabaseRole.SYSTEM_RUNTIME) {
            return false;
        }
        if (database.getDatabaseRole() == AnalyticsDatabaseRole.BUSINESS_PRIMARY
                || database.getDatabaseRole() == AnalyticsDatabaseRole.BUSINESS_SECONDARY) {
            return true;
        }
        String engine = normalize(database.getEngine());
        return engine.isEmpty() || "mysql".equals(engine);
    }

    private ExecutionResult mapPreview(AnalyticsDatabase database, DatasetResult result) {
        List<PreviewColumn> columns = result.cols().stream()
                .map(column -> new PreviewColumn(
                        stringValue(column.get("name")),
                        firstNonBlank(stringValue(column.get("display_name")), stringValue(column.get("name"))),
                        stringValue(column.get("base_type"))))
                .toList();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (List<Object> row : result.rows()) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            for (int i = 0; i < columns.size() && i < row.size(); i++) {
                mapped.put(columns.get(i).key(), row.get(i));
            }
            rows.add(mapped);
        }

        return new ExecutionResult(
                database.getId(),
                database.getName(),
                columns,
                rows,
                rows.size(),
                rows.size() >= PREVIEW_LIMIT);
    }

    private QueryContract resolveQueryContract(AnalyticsReportTemplate template) {
        JsonNode spec = parseJson(template.getSpecJson());
        JsonNode queryContract = spec == null ? null : spec.get("queryContract");
        if (queryContract == null || !queryContract.isObject()) {
            return null;
        }
        String targetObject = firstNonBlank(
                stringValue(queryContract.get("targetObject")),
                trimToNull(template.getTargetObject()));
        if (targetObject == null) {
            return null;
        }
        String sourceType = firstNonBlank(
                stringValue(queryContract.get("sourceType")),
                trimToNull(template.getDataSourceType()));
        String databaseName = firstNonBlank(
                toRoleSelector(stringValue(queryContract.get("databaseRole"))),
                stringValue(queryContract.get("databaseName")));
        String primaryDbtModel = firstNonBlank(
                stringValue(queryContract.get("primaryDbtModel")),
                firstDbtModel(queryContract.get("dbtModels")));
        JsonNode metricDefinition = parseJson(template.getMetricDefinitionJson());
        primaryDbtModel = firstNonBlank(
                primaryDbtModel,
                metricDefinition == null ? null : stringValue(metricDefinition.get("primaryDbtModel")));
        return new QueryContract(
                sourceType,
                targetObject,
                databaseName,
                primaryDbtModel,
                stringArray(queryContract.get("outputColumns")),
                orderByColumns(queryContract.get("orderBy")));
    }

    private static String buildDbtSelectClause(List<String> outputColumns) {
        if (outputColumns == null || outputColumns.isEmpty()) {
            return "*";
        }
        return outputColumns.stream()
                .map(DefaultFixedReportExecutionService::quoteDbtIdentifier)
                .reduce((left, right) -> left + ", " + right)
                .orElse("*");
    }

    private static String buildDbtOrderByClause(List<OrderByColumn> orderBy) {
        if (orderBy == null || orderBy.isEmpty()) {
            return "";
        }
        String clause = orderBy.stream()
                .map(column -> quoteDbtIdentifier(column.column()) + " " + normalizeSortDirection(column.direction()) + " NULLS LAST")
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        return clause.isBlank() ? "" : " ORDER BY " + clause;
    }

    private static String quoteDbtIdentifier(String identifier) {
        String normalized = trimToNull(identifier);
        if (normalized == null) {
            throw new IllegalArgumentException("DBT output column is required");
        }
        if (!normalized.matches("[\\p{L}\\p{N}_]+")) {
            throw new IllegalArgumentException("Invalid DBT output column: " + identifier);
        }
        return "\"" + normalized.replace("\"", "\"\"") + "\"";
    }

    private static String normalizeSortDirection(String direction) {
        String normalized = normalize(direction);
        if ("asc".equals(normalized)) {
            return "ASC";
        }
        return "DESC";
    }

    private static String firstDbtModel(JsonNode dbtModels) {
        if (dbtModels == null || !dbtModels.isArray()) {
            return null;
        }
        for (JsonNode model : dbtModels) {
            String value = stringValue(model);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static List<String> stringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = stringValue(item);
            if (value != null) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private static List<OrderByColumn> orderByColumns(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (node.isObject()) {
            OrderByColumn column = orderByColumn(node);
            return column == null ? List.of() : List.of(column);
        }
        if (!node.isArray()) {
            return List.of();
        }
        List<OrderByColumn> columns = new ArrayList<>();
        for (JsonNode item : node) {
            OrderByColumn column = orderByColumn(item);
            if (column != null) {
                columns.add(column);
            }
        }
        return List.copyOf(columns);
    }

    private static OrderByColumn orderByColumn(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String column = stringValue(node.get("column"));
        if (column == null) {
            return null;
        }
        return new OrderByColumn(column, stringValue(node.get("direction")));
    }

    private static String validateDbtRelation(String relation) {
        String normalized = trimToNull(relation);
        if (normalized == null) {
            throw new IllegalArgumentException("DBT relation is required");
        }
        String[] parts = normalized.split("\\.");
        if (parts.length < 1 || parts.length > 2) {
            throw new IllegalArgumentException("Invalid DBT relation: " + relation);
        }
        for (String part : parts) {
            if (!part.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IllegalArgumentException("Invalid DBT relation: " + relation);
            }
        }
        return normalized;
    }

    private static JsonNode parseJson(String rawJson) {
        String raw = trimToNull(rawJson);
        if (raw == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(raw);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String stringParam(Map<String, Object> parameters, String key) {
        if (parameters == null || key == null) {
            return null;
        }
        Object value = parameters.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JsonNode node) {
            return trimToNull(node.isTextual() ? node.asText() : node.toString());
        }
        return trimToNull(value.toString());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String firstNonBlank(String first, String second) {
        return trimToNull(first) != null ? first.trim() : trimToNull(second);
    }

    private static String toRoleSelector(String databaseRole) {
        String normalized = trimToNull(databaseRole);
        if (normalized == null) {
            return null;
        }
        return DATABASE_ROLE_SELECTOR_PREFIX + normalized.toUpperCase(Locale.ROOT);
    }

    private static AnalyticsDatabaseRole parseDatabaseRoleSelector(String selector) {
        String normalized = trimToNull(selector);
        if (normalized == null || !normalized.startsWith(DATABASE_ROLE_SELECTOR_PREFIX)) {
            return null;
        }
        String roleValue = normalized.substring(DATABASE_ROLE_SELECTOR_PREFIX.length()).trim();
        if (roleValue.isEmpty()) {
            return null;
        }
        try {
            return AnalyticsDatabaseRole.valueOf(roleValue);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // ── Batch 2: 报花单据汇总 ─────────────────────────────────────────

    private ExecutionResult executeFlowerBizDocumentSummary(QueryContract contract, Map<String, Object> parameters)
            throws SQLException {
        AnalyticsDatabase database = resolveDatabase(contract.databaseName());
        StringBuilder sql = new StringBuilder("""
                SELECT
                    a.project_name AS projectName,
                    CASE a.biz_type
                        WHEN 1 THEN '加花' WHEN 2 THEN '减花' WHEN 3 THEN '换花'
                        WHEN 4 THEN '调花' WHEN 5 THEN '初摆' WHEN 10 THEN '售花'
                        WHEN 11 THEN '内购' ELSE '其他'
                    END AS bizTypeName,
                    a.code,
                    a.title,
                    CASE a.status
                        WHEN 1 THEN '草稿' WHEN 2 THEN '审核中' WHEN 3 THEN '已通过'
                        WHEN 4 THEN '已完成' WHEN -1 THEN '已作废' ELSE '未知'
                    END AS statusName,
                    CASE a.urgent WHEN 1 THEN '是' ELSE '否' END AS urgentName,
                    COALESCE(a.apply_use_name, '') AS applyUserName,
                    COALESCE(a.curing_user_name, '') AS curingUserName,
                    DATE_FORMAT(a.apply_time, '%Y-%m-%d %H:%i') AS applyTime,
                    DATE_FORMAT(a.finish_time, '%Y-%m-%d %H:%i') AS finishTime
                FROM t_flower_biz_info a
                WHERE (a.del_flag IS NULL OR a.del_flag = '0')
                """);
        List<Object> bindings = new ArrayList<>();
        appendIfPresent(sql, bindings, parameters, "projectId", " AND a.project_id = ?");
        appendIfPresent(sql, bindings, parameters, "bizType", " AND a.biz_type = ?");
        appendIfPresent(sql, bindings, parameters, "status", " AND a.status = ?");
        appendDateRange(sql, bindings, parameters, "a.apply_time");
        sql.append(" ORDER BY a.apply_time DESC, a.id DESC");
        return runAndMap(database, sql, bindings);
    }

    // ── Batch 2: 配送记录 ───────────────────────────────────────────

    private ExecutionResult executeProcurementDeliveryRecord(QueryContract contract, Map<String, Object> parameters)
            throws SQLException {
        AnalyticsDatabase database = resolveDatabase(contract.databaseName());
        StringBuilder sql = new StringBuilder("""
                SELECT
                    a.code,
                    a.title,
                    CASE a.status WHEN 1 THEN '配送中' WHEN 2 THEN '已结束' ELSE '未知' END AS statusName,
                    CASE a.delivery_mode
                        WHEN 1 THEN '市场->项目点' WHEN 2 THEN '库房->项目点'
                        WHEN 3 THEN '库房->库房' ELSE '其他'
                    END AS deliveryModeName,
                    COALESCE(a.source_address, '') AS sourceAddress,
                    COALESCE(a.destination, '') AS destination,
                    COALESCE(a.delivery_user_name, '') AS deliveryUserName,
                    COALESCE(a.receive_user_name, '') AS receiveUserName,
                    DATE_FORMAT(a.start_delivery_time, '%Y-%m-%d %H:%i') AS deliveryTime,
                    DATE_FORMAT(a.receive_time, '%Y-%m-%d %H:%i') AS receiveTime
                FROM t_delivery_info a
                WHERE (a.del_flag IS NULL OR a.del_flag = '0')
                """);
        List<Object> bindings = new ArrayList<>();
        appendIfPresent(sql, bindings, parameters, "status", " AND a.status = ?");
        appendDateRange(sql, bindings, parameters, "a.start_delivery_time");
        sql.append(" ORDER BY a.start_delivery_time DESC, a.id DESC");
        return runAndMap(database, sql, bindings);
    }

    // ── Batch 2: 日常报销 ───────────────────────────────────────────

    private ExecutionResult executeFinanceReimbursementList(QueryContract contract, Map<String, Object> parameters)
            throws SQLException {
        AnalyticsDatabase database = resolveDatabase(contract.databaseName());
        StringBuilder sql = new StringBuilder("""
                SELECT
                    a.code,
                    a.title,
                    COALESCE(a.apply_user_name, '') AS applyUserName,
                    ROUND(COALESCE(a.total_amount, 0), 2) AS totalAmount,
                    CASE a.status
                        WHEN 1 THEN '草稿' WHEN 2 THEN '审核中' WHEN 3 THEN '待付款'
                        WHEN 4 THEN '已付款' WHEN -1 THEN '已作废' ELSE '未知'
                    END AS statusName,
                    CASE a.invoice_status WHEN 1 THEN '无' WHEN 2 THEN '有' ELSE '' END AS invoiceStatusName,
                    COALESCE(a.collect_name, '') AS collectName,
                    DATE_FORMAT(a.apply_time, '%Y-%m-%d %H:%i') AS applyTime,
                    DATE_FORMAT(a.pay_time, '%Y-%m-%d %H:%i') AS payTime
                FROM f_expense_account_info a
                WHERE 1 = 1
                """);
        List<Object> bindings = new ArrayList<>();
        appendLikeIfPresent(sql, bindings, parameters, "code", " AND a.code LIKE ?");
        appendIfPresent(sql, bindings, parameters, "status", " AND a.status = ?");
        appendIfPresent(sql, bindings, parameters, "applyUserId", " AND a.apply_user_id = ?");
        sql.append(" ORDER BY a.apply_time DESC, a.id DESC");
        return runAndMap(database, sql, bindings);
    }

    // ── Batch 2: 出入库记录 ─────────────────────────────────────────

    private ExecutionResult executeWarehouseInOutRecord(QueryContract contract, Map<String, Object> parameters)
            throws SQLException {
        AnalyticsDatabase database = resolveDatabase(contract.databaseName());
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM (
                    SELECT '入库' AS direction, wi.code, sh.name AS storehouseName,
                           a.good_name AS goodName, COALESCE(a.good_specs, '') AS goodSpecs,
                           a.good_number AS quantity, ROUND(COALESCE(a.price, 0), 2) AS unitPrice,
                           DATE_FORMAT(wi.warehousing_time, '%Y-%m-%d') AS recordDate
                    FROM t_warehousing_item a
                    JOIN t_warehousing_info wi ON wi.id = a.warehousing_info_id
                    JOIN s_storehouse_info sh ON sh.id = wi.storehouse_info_id
                    UNION ALL
                    SELECT '出库', ei.code, sh.name,
                           a.good_name, COALESCE(a.good_specs, ''),
                           a.good_number, ROUND(COALESCE(a.price, 0), 2),
                           DATE_FORMAT(ei.ex_warehouse_time, '%Y-%m-%d')
                    FROM t_ex_warehouse_item a
                    JOIN t_ex_warehouse_info ei ON ei.id = a.ex_warehouse_info_id
                    JOIN s_storehouse_info sh ON sh.id = ei.storehouse_info_id
                ) t WHERE 1 = 1
                """);
        List<Object> bindings = new ArrayList<>();
        appendLikeIfPresent(sql, bindings, parameters, "goodName", " AND t.goodName LIKE ?");
        String startDate = stringParam(parameters, "startDate");
        if (startDate != null) { sql.append(" AND t.recordDate >= ?"); bindings.add(startDate); }
        String endDate = stringParam(parameters, "endDate");
        if (endDate != null) { sql.append(" AND t.recordDate <= ?"); bindings.add(endDate); }
        sql.append(" ORDER BY t.recordDate DESC");
        return runAndMap(database, sql, bindings);
    }

    // ── Batch 2: 合同管理 ───────────────────────────────────────────

    private ExecutionResult executeProjectContractList(QueryContract contract, Map<String, Object> parameters)
            throws SQLException {
        AnalyticsDatabase database = resolveDatabase(contract.databaseName());
        StringBuilder sql = new StringBuilder("""
                SELECT
                    a.code,
                    a.title,
                    COALESCE(b.name, '') AS customerName,
                    CASE a.status WHEN 1 THEN '执行中' WHEN 2 THEN '已结束' ELSE '未知' END AS statusName,
                    DATE_FORMAT(a.signing_time, '%Y-%m-%d') AS signDate,
                    DATE_FORMAT(a.start_date, '%Y-%m-%d') AS startDate,
                    DATE_FORMAT(a.end_date, '%Y-%m-%d') AS endDate,
                    COALESCE(a.settlement_period, '') AS settlePeriod,
                    COALESCE(a.business_personnel_name, '') AS bizPersonnelName
                FROM p_contract a
                LEFT JOIN p_customer b ON b.id = a.customer_id
                WHERE (a.del_flag IS NULL OR a.del_flag = '0')
                """);
        List<Object> bindings = new ArrayList<>();
        appendLikeIfPresent(sql, bindings, parameters, "customerName", " AND b.name LIKE ?");
        appendIfPresent(sql, bindings, parameters, "status", " AND a.status = ?");
        sql.append(" ORDER BY a.signing_time DESC, a.id DESC");
        return runAndMap(database, sql, bindings);
    }

    // ── Batch 2: 监管盘点汇总 ───────────────────────────────────────

    private ExecutionResult executeProjectSupervisionCheckSummary(QueryContract contract, Map<String, Object> parameters)
            throws SQLException {
        AnalyticsDatabase database = resolveDatabase(contract.databaseName());
        StringBuilder sql = new StringBuilder("""
                SELECT
                    a.project_name AS projectName,
                    a.title AS checkPeriod,
                    COALESCE(a.supervise_user_name, '') AS checkUserName,
                    CASE a.status WHEN 1 THEN '盘点中' WHEN 2 THEN '已结束' ELSE '未知' END AS statusName,
                    COALESCE(a.total_group_number, 0) AS totalGroupCount,
                    COALESCE(a.finish_check_number, 0) AS checkedGroupCount,
                    COALESCE(a.total_position_number, 0) AS totalPositionCount,
                    COALESCE(a.finishcheck_position_number, 0) AS checkedPositionCount,
                    CASE WHEN a.total_position_number > 0
                         THEN ROUND(a.finishcheck_position_number * 100.0 / a.total_position_number, 1)
                         ELSE 0
                    END AS progressPct
                FROM t_supervise_check_batch a
                WHERE 1 = 1
                """);
        List<Object> bindings = new ArrayList<>();
        appendIfPresent(sql, bindings, parameters, "projectId", " AND a.project_id = ?");
        appendIfPresent(sql, bindings, parameters, "status", " AND a.status = ?");
        sql.append(" ORDER BY a.start_time DESC, a.project_name ASC");
        return runAndMap(database, sql, bindings);
    }

    // ── Batch 2: 项目点经营汇总 ─────────────────────────────────────

    private ExecutionResult executeProjectFulfillmentSummary(QueryContract contract, Map<String, Object> parameters)
            throws SQLException {
        AnalyticsDatabase database = resolveDatabase(contract.databaseName());
        StringBuilder sql = new StringBuilder("""
                SELECT
                    p.name AS projectName,
                    COALESCE(cu.name, '') AS customerName,
                    COALESCE(p.manager_name, '') AS managerName,
                    COALESCE(p.biz_user_name, '') AS bizUserName,
                    CASE p.status WHEN 1 THEN '正常' WHEN 2 THEN '暂停' WHEN 3 THEN '结束' ELSE '未知' END AS statusName,
                    COALESCE(ct.title, '') AS contractTitle,
                    (SELECT COUNT(*) FROM p_project_green pg WHERE pg.project_id = p.id AND (pg.del_flag IS NULL OR pg.del_flag = '0')) AS greenCount,
                    (SELECT COUNT(*) FROM p_position pp WHERE pp.project_id = p.id AND (pp.del_flag IS NULL OR pp.del_flag = '0')) AS positionCount,
                    (SELECT COUNT(*) FROM t_flower_biz_info fb WHERE fb.project_id = p.id AND (fb.del_flag IS NULL OR fb.del_flag = '0') AND fb.status = 4) AS completedBizCount
                FROM p_project p
                LEFT JOIN p_contract ct ON ct.id = p.contract_id
                LEFT JOIN p_customer cu ON cu.id = ct.customer_id
                WHERE (p.del_flag IS NULL OR p.del_flag = '0')
                """);
        List<Object> bindings = new ArrayList<>();
        appendIfPresent(sql, bindings, parameters, "projectId", " AND p.id = ?");
        appendIfPresent(sql, bindings, parameters, "status", " AND p.status = ?");
        sql.append(" ORDER BY p.name ASC");
        return runAndMap(database, sql, bindings);
    }

    // ── Batch 2 helpers ─────────────────────────────────────────────

    private ExecutionResult runAndMap(AnalyticsDatabase database, StringBuilder sql, List<Object> bindings)
            throws SQLException {
        DatasetResult result = datasetQueryService.runNative(
                database.getId(), sql.toString(),
                new DatasetConstraints(PREVIEW_LIMIT, QUERY_TIMEOUT_SECONDS, null), bindings);
        return mapPreview(database, result);
    }

    private void appendIfPresent(StringBuilder sql, List<Object> bindings,
                                  Map<String, Object> parameters, String paramName, String clause) {
        String value = stringParam(parameters, paramName);
        if (value != null) {
            sql.append(clause);
            bindings.add(value);
        }
    }

    private void appendLikeIfPresent(StringBuilder sql, List<Object> bindings,
                                      Map<String, Object> parameters, String paramName, String clause) {
        String value = stringParam(parameters, paramName);
        if (value != null) {
            sql.append(clause);
            bindings.add('%' + value + '%');
        }
    }

    private void appendDateRange(StringBuilder sql, List<Object> bindings,
                                  Map<String, Object> parameters, String column) {
        String startDate = stringParam(parameters, "startDate");
        if (startDate != null) {
            sql.append(" AND ").append(column).append(" >= CONCAT(?, ' 00:00:00')");
            bindings.add(startDate);
        }
        String endDate = stringParam(parameters, "endDate");
        if (endDate != null) {
            sql.append(" AND ").append(column).append(" <= CONCAT(?, ' 23:59:59')");
            bindings.add(endDate);
        }
    }

    private record QueryContract(
            String sourceType,
            String targetObject,
            String databaseName,
            String primaryDbtModel,
            List<String> outputColumns,
            List<OrderByColumn> orderBy) {}

    private record OrderByColumn(String column, String direction) {}
}
