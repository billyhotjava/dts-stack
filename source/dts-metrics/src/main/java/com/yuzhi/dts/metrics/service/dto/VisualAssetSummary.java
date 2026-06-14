package com.yuzhi.dts.metrics.service.dto;

import java.util.List;

/**
 * Typed shape of one可视化资产 row returned by {@code GET /api/metrics/visual-assets} (F5 类型化, 缺陷 #4).
 *
 * <p>Replaces the former stringly-typed {@code Map<String,Object>} at the web boundary. Field names and
 * declaration order mirror the previous map exactly, so the serialized JSON (and the frontend
 * {@code VisualAssetSummary} contract) is unchanged. Per the API contract this must carry at least
 * {@code warehouseLayer}, {@code grain}, {@code governanceStatus}, {@code permissionDecision}.
 */
public record VisualAssetSummary(
    String assetId,
    String assetKey,
    String name,
    String warehouseLayer,
    String domainCode,
    String businessObjectCode,
    List<String> grain,
    List<String> primaryKeys,
    List<String> timeColumns,
    List<String> dimensionColumns,
    List<String> metricColumns,
    String governanceStatus,
    String lineageStatus,
    String permissionDecision,
    String classification,
    String ownerDept,
    String description
) {}
