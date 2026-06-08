package com.yuzhi.dts.metrics.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.metrics.service.MetricGraphDraftService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class MetricGraphResourceTest {

    @Test
    void preflightRejectsOdsAndStgNodes() {
        MetricGraphResource resource = resource();

        Map<String, Object> result = resource.preflightDraft(
            Map.of(
                "base",
                "ods_order_snapshot",
                "measures",
                List.of("order_cnt"),
                "dimensions",
                List.of("stat_date"),
                "nodes",
                List.of(Map.of("id", "ods_order_snapshot", "role", "BASE", "warehouseLayer", "ODS"))
            )
        );

        assertThat(result).containsEntry("status", "GRAPH_BLOCKED");
        assertThat((List<?>) result.get("diagnostics"))
            .anySatisfy(item -> assertThat(asMap(item)).containsEntry("code", "invalid_layer").containsEntry("nodeId", "ods_order_snapshot"));
    }

    @Test
    void preflightRejectsDwdNodeWithoutGrainOrPrimaryKey() {
        MetricGraphResource resource = resource();

        Map<String, Object> result = resource.preflightDraft(
            Map.of(
                "base",
                "dwd_order_detail",
                "measures",
                List.of("order_amount"),
                "dimensions",
                List.of("stat_date"),
                "nodes",
                List.of(Map.of("id", "dwd_order_detail", "role", "BASE", "warehouseLayer", "DWD"))
            )
        );

        assertThat(result).containsEntry("status", "GRAPH_BLOCKED");
        assertThat((List<?>) result.get("diagnostics"))
            .anySatisfy(item -> assertThat(asMap(item)).containsEntry("code", "grain_mismatch").containsEntry("nodeId", "dwd_order_detail"));
    }

    @Test
    void preflightRejectsDwdNodeWithoutStandardCode() {
        MetricGraphResource resource = resource();

        Map<String, Object> result = resource.preflightDraft(
            Map.of(
                "base",
                "dwd_order_detail",
                "measures",
                List.of("order_amount"),
                "dimensions",
                List.of("stat_date"),
                "nodes",
                List.of(Map.of("id", "dwd_order_detail", "role", "BASE", "warehouseLayer", "DWD", "grain", List.of("order_id")))
            )
        );

        assertThat(result).containsEntry("status", "GRAPH_BLOCKED");
        assertThat((List<?>) result.get("diagnostics"))
            .anySatisfy(item -> assertThat(asMap(item)).containsEntry("code", "standard_code_required").containsEntry("nodeId", "dwd_order_detail"));
    }

    @Test
    void createDraftStoresReplayableGraphWithLayerMetadata() {
        MetricGraphResource resource = resource();
        Map<String, Object> graph = Map.of(
            "base",
            "dws_order_day",
            "measures",
            List.of("order_cnt"),
            "dimensions",
            List.of("stat_date"),
            "nodes",
            List.of(Map.of("id", "dws_order_day", "role", "BASE", "warehouseLayer", "DWS", "grain", List.of("stat_date")))
        );

        Map<String, Object> draft = resource.createDraft(graph);
        String draftId = String.valueOf(draft.get("id"));
        Map<String, Object> reloaded = resource.getDraft(draftId);

        assertThat(draft).containsEntry("status", "GRAPH_READY");
        assertThat(reloaded.get("graph")).isEqualTo(graph);
    }

    @Test
    void updateDraftRecomputesDiagnosticsAndKeepsDraftId() {
        MetricGraphResource resource = resource();
        Map<String, Object> graph = readyDwsGraph();
        Map<String, Object> draft = resource.createDraft(graph);
        String draftId = String.valueOf(draft.get("id"));

        Map<String, Object> updated = resource.updateDraft(
            draftId,
            Map.of(
                "base",
                "ods_order_snapshot",
                "measures",
                List.of("order_cnt"),
                "dimensions",
                List.of("stat_date"),
                "nodes",
                List.of(Map.of("id", "ods_order_snapshot", "role", "BASE", "warehouseLayer", "ODS"))
            )
        );

        assertThat(updated).containsEntry("id", draftId).containsEntry("status", "GRAPH_BLOCKED");
    }

    @Test
    void preflightStoredDraftUsesSavedGraph() {
        MetricGraphResource resource = resource();
        Map<String, Object> draft = resource.createDraft(readyDwsGraph());
        String draftId = String.valueOf(draft.get("id"));

        Map<String, Object> result = resource.preflightStoredDraft(draftId);

        assertThat(result).containsEntry("status", "GRAPH_READY");
    }

    @Test
    void preflightRejectsDerivedMetricCycles() {
        MetricGraphResource resource = resource();

        Map<String, Object> result = resource.preflightDraft(
            Map.of(
                "base",
                "dws_order_day",
                "dimensions",
                List.of("stat_date"),
                "derived_metrics",
                List.of(Map.of("id", "gross_margin_rate", "expression", "gross_margin_rate / 100")),
                "nodes",
                List.of(Map.of("id", "dws_order_day", "role", "BASE", "warehouseLayer", "DWS", "grain", List.of("stat_date")))
            )
        );

        assertThat(result).containsEntry("status", "GRAPH_BLOCKED");
        assertThat((List<?>) result.get("diagnostics"))
            .anySatisfy(item -> assertThat(asMap(item)).containsEntry("code", "metric_cycle").containsEntry("nodeId", "gross_margin_rate"));
    }

    @Test
    void preflightRejectsRawSqlDerivedMetricExpression() {
        MetricGraphResource resource = resource();

        Map<String, Object> result = resource.preflightDraft(
            Map.of(
                "base",
                "dws_order_day",
                "measures",
                List.of("order_amount"),
                "dimensions",
                List.of("stat_date"),
                "derived_metrics",
                List.of(Map.of("id", "unsafe_metric", "expression", "sum(order_amount); drop table dws_order_day")),
                "nodes",
                List.of(Map.of("id", "dws_order_day", "role", "BASE", "warehouseLayer", "DWS", "grain", List.of("stat_date")))
            )
        );

        assertThat(result).containsEntry("status", "GRAPH_BLOCKED");
        assertThat((List<?>) result.get("diagnostics"))
            .anySatisfy(item -> assertThat(asMap(item)).containsEntry("code", "unsafe_expression").containsEntry("nodeId", "unsafe_metric"));
    }

    @Test
    void preflightRejectsDerivedMetricUnknownFieldReference() {
        MetricGraphResource resource = resource();

        Map<String, Object> result = resource.preflightDraft(
            Map.of(
                "base",
                "dws_order_day",
                "measures",
                List.of("order_amount"),
                "dimensions",
                List.of("stat_date"),
                "derived_metrics",
                List.of(Map.of("id", "secret_metric", "expression", "sum(secret_amount)")),
                "nodes",
                List.of(Map.of("id", "dws_order_day", "role", "BASE", "warehouseLayer", "DWS", "grain", List.of("stat_date")))
            )
        );

        assertThat(result).containsEntry("status", "GRAPH_BLOCKED");
        assertThat((List<?>) result.get("diagnostics"))
            .anySatisfy(item -> assertThat(asMap(item)).containsEntry("code", "unregistered_field").containsEntry("fieldId", "secret_amount"));
    }

    @Test
    void preflightAcceptsControlledDerivedMetricDsl() {
        MetricGraphResource resource = resource();

        Map<String, Object> result = resource.preflightDraft(
            Map.of(
                "base",
                "dws_order_day",
                "measures",
                List.of("order_amount", "order_count"),
                "dimensions",
                List.of("stat_date"),
                "derived_metrics",
                List.of(Map.of("id", "avg_order_amount", "expression", "ratio(order_amount, order_count)")),
                "nodes",
                List.of(Map.of("id", "dws_order_day", "role", "BASE", "warehouseLayer", "DWS", "grain", List.of("stat_date")))
            )
        );

        assertThat(result).containsEntry("status", "GRAPH_READY");
        assertThat((List<?>) result.get("diagnostics")).isEmpty();
    }

    @Test
    void preflightAcceptsConditionalDerivedMetricDsl() {
        MetricGraphResource resource = resource();

        Map<String, Object> result = resource.preflightDraft(
            Map.of(
                "base",
                "dws_order_day",
                "measures",
                List.of("order_amount", "order_count"),
                "dimensions",
                List.of("stat_date", "status_code"),
                "derived_metrics",
                List.of(
                    Map.of("id", "paid_order_count", "expression", "count_if(status_code, eq, PAID)"),
                    Map.of("id", "paid_order_amount", "expression", "sum_if(order_amount, status_code, eq, PAID)"),
                    Map.of("id", "paid_flag", "expression", "case_when(status_code, eq, PAID, 1, 0)")
                ),
                "nodes",
                List.of(Map.of("id", "dws_order_day", "role", "BASE", "warehouseLayer", "DWS", "grain", List.of("stat_date")))
            )
        );

        assertThat(result).containsEntry("status", "GRAPH_READY");
        assertThat((List<?>) result.get("diagnostics")).isEmpty();
    }

    @Test
    void getDraftReturns404ForMissingDraft() {
        MetricGraphResource resource = resource();

        assertThatThrownBy(() -> resource.getDraft("missing"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404 NOT_FOUND");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> readyDwsGraph() {
        return Map.of(
            "base",
            "dws_order_day",
            "measures",
            List.of("order_cnt"),
            "dimensions",
            List.of("stat_date"),
            "nodes",
            List.of(Map.of("id", "dws_order_day", "role", "BASE", "warehouseLayer", "DWS", "grain", List.of("stat_date")))
        );
    }

    private static MetricGraphResource resource() {
        return new MetricGraphResource(new MetricGraphDraftService());
    }
}
