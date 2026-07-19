package com.yuzhi.dts.metrics.web.rest;

import com.yuzhi.dts.metrics.service.PlatformContractClient;
import com.yuzhi.dts.metrics.service.PlatformContractClient.PlatformContractException;
import com.yuzhi.dts.metrics.service.dto.VisualAssetSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/metrics/visual-assets")
public class MetricVisualAssetResource {

    private static final List<String> DEFAULT_LAYERS = List.of("DWS", "ADS");

    private final PlatformContractClient platformClient;

    public MetricVisualAssetResource(PlatformContractClient platformClient) {
        this.platformClient = platformClient;
    }

    @GetMapping
    public Map<String, Object> listVisualAssets(
        @RequestParam(value = "layers", required = false) String layers,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", required = false, defaultValue = "0") int page,
        @RequestParam(value = "size", required = false, defaultValue = "20") int size,
        @RequestParam(value = "includeDrilldown", required = false, defaultValue = "false") boolean includeDrilldown
    ) {
        List<String> acceptedLayers = parseLayers(layers, includeDrilldown);
        List<VisualAssetSummary> data = new ArrayList<>();
        long total = 0;
        for (String layer : acceptedLayers) {
            Map<String, Object> platform = readPlatformAssets(layer, keyword, page, size);
            Map<String, Object> platformData = object(platform.get("data"));
            total += number(platformData.get("total"));
            for (Map<String, Object> item : listOfObjects(platformData.get("content"))) {
                data.add(toVisualAsset(item, layer));
            }
        }
        return Map.of(
            "data",
            data,
            "meta",
            Map.of("page", page, "size", size, "total", total, "layers", acceptedLayers, "source", "dts-platform catalog assets-v2")
        );
    }

    @GetMapping("/{assetId}")
    public Map<String, Object> getVisualAsset(@PathVariable String assetId) {
        if (!StringUtils.hasText(assetId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "assetId is required");
        }
        Map<String, Object> platform = readPlatformSchema(assetId);
        Map<String, Object> data = object(platform.get("data"));
        return Map.of("data", data, "meta", Map.of("source", "dts-platform catalog schema-contract"));
    }

    private Map<String, Object> readPlatformAssets(String layer, String keyword, int page, int size) {
        try {
            return platformClient.listCatalogAssets(layer, keyword, Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        } catch (PlatformContractException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "platform catalog contract unavailable", e);
        }
    }

    private Map<String, Object> readPlatformSchema(String assetId) {
        try {
            return platformClient.getCatalogAssetSchemaContract(assetId);
        } catch (PlatformContractException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "platform asset schema contract unavailable", e);
        }
    }

    private List<String> parseLayers(String rawLayers, boolean includeDrilldown) {
        List<String> layers = StringUtils.hasText(rawLayers)
            ? java.util.Arrays
                .stream(rawLayers.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .distinct()
                .toList()
            : DEFAULT_LAYERS;
        if (layers.isEmpty()) {
            return DEFAULT_LAYERS;
        }
        for (String layer : layers) {
            if ("ODS".equals(layer) || "STG".equals(layer)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_layer: ODS/STG are lineage-only layers");
            }
            if ("DWD".equals(layer) && !includeDrilldown) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_layer: DWD requires includeDrilldown=true");
            }
            if (!"DWD".equals(layer) && !"DWS".equals(layer) && !"ADS".equals(layer)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_layer: " + layer);
            }
        }
        return layers;
    }

    private VisualAssetSummary toVisualAsset(Map<String, Object> item, String layer) {
        String id = text(item.get("id"));
        // T03: surface the platform's per-asset permission verdict verbatim when present (forward-compatible
        // with the contracted /internal/metrics/visual-assets endpoint). The generic /catalog/assets-v2 payload
        // carries no per-asset decision today, so fall back to PLATFORM_FILTERED rather than overclaiming ALLOWED.
        String permissionDecision = firstText(item.get("permissionDecision"), item.get("permission_decision"), "PLATFORM_FILTERED");
        return new VisualAssetSummary(
            id,
            firstText(item.get("fqn"), "catalog:asset:" + id),
            firstText(item.get("displayName"), item.get("table"), item.get("fqn"), id),
            firstText(item.get("warehouseLayer"), layer),
            text(item.get("domainId")),
            firstText(item.get("modelRef"), item.get("modelSpecId"), id),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            firstText(item.get("governanceStatus"), "PENDING_GOVERNANCE"),
            firstText(item.get("matchStatus"), "UNKNOWN"),
            permissionDecision,
            firstText(item.get("classification"), "UNCLASSIFIED"),
            text(item.get("ownerDept")),
            text(item.get("description"))
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static List<Map<String, Object>> listOfObjects(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                result.add(typed);
            }
        }
        return result;
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return "";
    }

    private static String text(Object value) {
        return value != null ? String.valueOf(value).trim() : "";
    }
}
