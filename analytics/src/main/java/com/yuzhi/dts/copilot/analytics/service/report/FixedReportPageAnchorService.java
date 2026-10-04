package com.yuzhi.dts.copilot.analytics.service.report;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class FixedReportPageAnchorService {

    private static final Map<String, PageAnchor> PAGE_ANCHORS = Map.ofEntries(
            Map.entry("FIN-AR-OVERVIEW", new PageAnchor("财务结算", "/operate/settlement")),
            Map.entry("FIN-CUSTOMER-AR-RANK", new PageAnchor("财务结算", "/operate/settlement")),
            Map.entry("FIN-PROJECT-COLLECTION-PROGRESS", new PageAnchor("财务结算", "/operate/settlement")),
            Map.entry("FIN-PENDING-RECEIPTS-DETAIL", new PageAnchor("财务结算", "/operate/settlement")),
            Map.entry("FIN-PENDING-PAYMENT-APPROVAL", new PageAnchor("支出管理", "/operate/listPayRecord")),
            Map.entry("FIN-ADVANCE-REQUEST-STATUS", new PageAnchor("预支申请", "/operate/listAdvance")),
            Map.entry("FIN-REIMBURSEMENT-STATUS", new PageAnchor("日常报销", "/operate/list-daily")),
            Map.entry("FIN-INVOICE-RECONCILIATION", new PageAnchor("开票管理", "/operate/listInvoice")),
            Map.entry("PROC-PURCHASE-REQUEST-TODO", new PageAnchor("采购计划明细", "/purchase/plan")),
            Map.entry("PROC-ORDER-EXECUTION-PROGRESS", new PageAnchor("采购明细", "/purchase/purchaseitem")),
            Map.entry("PROC-SUPPLIER-AMOUNT-RANK", new PageAnchor("采购汇总", "/purchase/purchase")),
            Map.entry("PROC-ARRIVAL-ONTIME-RATE", new PageAnchor("配送记录", "/purchase/list-delivery")),
            Map.entry("PROC-PENDING-INBOUND-LIST", new PageAnchor("入库管理", "/stock/warehouseIn")),
            Map.entry("PROC-INTRANSIT-BOARD", new PageAnchor("配送记录", "/purchase/list-delivery")),
            Map.entry("WH-STOCK-OVERVIEW", new PageAnchor("库存管理", "/stock/store-index")),
            Map.entry("WH-LOW-STOCK-ALERT", new PageAnchor("库存管理", "/stock/store-index"))
    );

    public Optional<PageAnchor> resolve(String templateCode) {
        if (templateCode == null || templateCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(PAGE_ANCHORS.get(templateCode.trim().toUpperCase(Locale.ROOT)));
    }

    public record PageAnchor(String title, String path) {}
}
