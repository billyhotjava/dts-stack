package com.yuzhi.dts.platform.service.modeling.warehouse;

import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerException.Kind.BAD_REQUEST;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerException.Kind.CONFLICT;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerException.Kind.NOT_FOUND;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerException.Kind.UNPROCESSABLE;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.modeling.WarehouseLayerRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerContract.CreateWarehouseLayerCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerContract.ResolvedWarehouseLayer;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerContract.StoredWarehouseLayer;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerContract.WarehouseLayerView;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceContract;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceContract.WarehouseLayerDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Governs the global custom warehouse-layer registry and ModelSpec layer selection.
 *
 * <p>Built-in layers stay code-owned by {@link Sprint64GovernanceContract}; this service only
 * persists custom layers and merges both sources into the canonical projection. Deletion is
 * logical, permanently reserves the code, and is blocked while any non-archived ModelSpec
 * references the layer.</p>
 */
@Service
public class WarehouseLayerApplicationService {

    private static final Map<String, Layer> SYSTEM_TO_CANONICAL = Map.of(
        "ODS_RAW", Layer.ODS,
        "ODS_STANDARDIZED", Layer.ODS,
        "STG", Layer.STG,
        "DWD", Layer.DWD,
        "DWS", Layer.DWS,
        "ADS", Layer.ADS
    );

    private static final Map<String, Integer> SYSTEM_LAYER_ORDER = Map.of(
        "ODS_RAW", 0,
        "ODS_STANDARDIZED", 1,
        "STG", 2,
        "DWD", 3,
        "DWS", 4,
        "ADS", 5
    );

    private static final String BUILTIN_DISABLED_REASON = "平台内置分层不可删除";

    private final WarehouseLayerRepository repository;
    private final AuditService auditService;

    public WarehouseLayerApplicationService(WarehouseLayerRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    public List<WarehouseLayerView> list() {
        List<WarehouseLayerView> views = new ArrayList<>();
        for (WarehouseLayerDto builtin : Sprint64GovernanceContract.warehouseLayers()) {
                views.add(
                    new WarehouseLayerView(
                        builtin.code(),
                        builtin.title(),
                        builtin.code(),
                        builtin.kind(),
                        builtin.responsibility(),
                        builtin.namingPrefixes(),
                        builtin.optional(),
                        true,
                        false,
                        BUILTIN_DISABLED_REASON,
                        WarehouseLayerContract.groupOf(builtin.code()),
                        WarehouseLayerContract.modelTypesOf(builtin.code())
                    )
                );
            }
        List<StoredWarehouseLayer> customs = repository.findAllActive().stream()
            .sorted(
                java.util.Comparator
                    .comparing((StoredWarehouseLayer layer) -> SYSTEM_LAYER_ORDER.getOrDefault(layer.systemLayerCode(), 99))
                    .thenComparing(StoredWarehouseLayer::name)
                    .thenComparing(StoredWarehouseLayer::code)
            )
            .toList();
        for (StoredWarehouseLayer custom : customs) {
            views.add(toCustomView(custom));
        }
        return List.copyOf(views);
    }

    public WarehouseLayerView create(String actor, CreateWarehouseLayerCommand command) {
        requireActor(actor);
        if (command == null) {
            throw error("WAREHOUSE_LAYER_CODE_INVALID", "请求体不能为空", BAD_REQUEST, Map.of("field", "code"));
        }
        String code = normalizeCode(command.code());
        String name = trimToNull(command.name());
        if (name == null) {
            throw error("WAREHOUSE_LAYER_NAME_REQUIRED", "分层名称不能为空", BAD_REQUEST, Map.of("field", "name"));
        }
        String systemLayerCode = resolveSystemLayerCode(command.systemLayerCode());
        String prefix = normalizePrefix(command.namingPrefix());
        String description = trimToNull(command.description());

        if (Sprint64GovernanceContract.resolveLayer(code).isPresent() || repository.codeExists(code)) {
            throw error(
                "WAREHOUSE_LAYER_CODE_CONFLICT",
                "分层编码与平台内置或其他历史自定义编码冲突: " + code,
                CONFLICT,
                Map.of("field", "code", "code", code)
            );
        }

        Instant now = Instant.now();
        StoredWarehouseLayer row = new StoredWarehouseLayer(
            UUID.randomUUID(),
            code,
            name,
            systemLayerCode,
            WarehouseLayerContract.groupOf(systemLayerCode),
            description,
            prefix,
            "ACTIVE",
            1,
            actor,
            now,
            actor,
            now
        );
        repository.insert(row);
        auditService.auditActionStrict(
            "MODELING_WAREHOUSE_LAYER_CREATE",
            AuditStage.SUCCESS,
            code,
            Map.of("systemLayerCode", systemLayerCode, "namingPrefix", prefix)
        );
        return toCustomView(row);
    }

    @Transactional(noRollbackFor = WarehouseLayerException.class)
    public void delete(String actor, String code) {
        requireActor(actor);
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (Sprint64GovernanceContract.resolveLayer(normalized).isPresent()) {
            throw error("WAREHOUSE_LAYER_BUILTIN_PROTECTED", "平台内置分层不可删除: " + normalized, CONFLICT, Map.of("code", normalized));
        }
        StoredWarehouseLayer row = repository
            .findByCode(normalized)
            .filter(candidate -> "ACTIVE".equals(candidate.status()))
            .orElseThrow(() ->
                error("WAREHOUSE_LAYER_NOT_FOUND", "自定义分层不存在或已删除: " + normalized, NOT_FOUND, Map.of("code", normalized))
            );

        long referenceCount = repository.countActiveModelReferences(normalized);
        if (referenceCount > 0) {
            auditService.auditActionStrict(
                "MODELING_WAREHOUSE_LAYER_DELETE",
                AuditStage.FAIL,
                normalized,
                Map.of("referenceCount", referenceCount, "reason", "IN_USE")
            );
            throw error(
                "WAREHOUSE_LAYER_IN_USE",
                "存在 " + referenceCount + " 个活动模型引用该分层，不能删除",
                CONFLICT,
                Map.of("referenceCount", referenceCount)
            );
        }

        int updated = repository.softDelete(normalized, row.version(), actor, Instant.now());
        if (updated != 1) {
            throw error("WAREHOUSE_LAYER_DELETE_RACE", "分层已被并发修改，请刷新后重试", CONFLICT, Map.of("code", normalized));
        }
        auditService.auditActionStrict(
            "MODELING_WAREHOUSE_LAYER_DELETE",
            AuditStage.SUCCESS,
            normalized,
            Map.of("referenceCount", 0L)
        );
    }

    public ResolvedWarehouseLayer resolveSelection(String requestedCode, Layer expectedLayer) {
        String effectiveCode = requestedCode == null || requestedCode.isBlank()
            ? expectedLayer.name()
            : normalizeCode(requestedCode);
        Optional<WarehouseLayerDto> builtin = Sprint64GovernanceContract.resolveLayer(effectiveCode);
        String systemCode = builtin
            .map(WarehouseLayerDto::code)
            .orElseGet(() -> activeCustom(effectiveCode).systemLayerCode());
        if (!systemCode.equals(expectedLayer.name())) {
            throw error(
                "MODEL_SPEC_WAREHOUSE_LAYER_TYPE_MISMATCH",
                "分层 " + effectiveCode + " 的系统类型与模型目标层不匹配",
                UNPROCESSABLE,
                Map.of("warehouseLayerCode", effectiveCode, "expectedSystemLayerCode", expectedLayer.name())
            );
        }
        return new ResolvedWarehouseLayer(effectiveCode, expectedLayer, builtin.isPresent());
    }

    private StoredWarehouseLayer activeCustom(String code) {
        StoredWarehouseLayer row = repository
            .findByCode(code)
            .orElseThrow(() ->
                error("MODEL_SPEC_WAREHOUSE_LAYER_NOT_FOUND", "分层不存在: " + code, UNPROCESSABLE, Map.of("warehouseLayerCode", code))
            );
        if (!"ACTIVE".equals(row.status())) {
            throw error(
                "MODEL_SPEC_WAREHOUSE_LAYER_INACTIVE",
                "分层已删除: " + code,
                UNPROCESSABLE,
                Map.of("warehouseLayerCode", code)
            );
        }
        return row;
    }

    private WarehouseLayerView toCustomView(StoredWarehouseLayer row) {
        WarehouseLayerDto system = Sprint64GovernanceContract
            .resolveLayer(row.systemLayerCode())
            .orElseThrow(() -> new IllegalStateException("custom layer system type out of contract: " + row.systemLayerCode()));
        Set<String> prefixes = new LinkedHashSet<>();
        if (row.namingPrefix() != null && !row.namingPrefix().isBlank()) {
            prefixes.add(row.namingPrefix());
        }
        prefixes.addAll(system.namingPrefixes());
        return new WarehouseLayerView(
            row.code(),
            row.name(),
            system.code(),
            system.kind(),
            trimToNull(row.description()) == null ? system.responsibility() : row.description(),
            List.copyOf(prefixes),
            system.optional(),
            false,
            true,
            null,
            row.layerGroup(),
            WarehouseLayerContract.modelTypesOf(row.systemLayerCode())
        );
    }

    private String resolveSystemLayerCode(String raw) {
        String systemLayerCode = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (Sprint64GovernanceContract.resolveLayer(systemLayerCode).isEmpty()) {
            throw error(
                "WAREHOUSE_LAYER_SYSTEM_TYPE_INVALID",
                "所属系统类型无效: " + raw,
                BAD_REQUEST,
                Map.of("field", "systemLayerCode")
            );
        }
        return systemLayerCode;
    }

    private static String normalizeCode(String value) {
        String code = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z][A-Z0-9_]{1,63}")) {
            throw error("WAREHOUSE_LAYER_CODE_INVALID", "分层编码格式不合法: " + value, BAD_REQUEST, Map.of("field", "code"));
        }
        return code;
    }

    private static String normalizePrefix(String value) {
        String prefix = trimToNull(value);
        if (prefix == null) {
            return null;
        }
        String normalized = prefix.toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z][a-z0-9_]{0,63}")) {
            throw error("WAREHOUSE_LAYER_PREFIX_INVALID", "命名前缀格式不合法: " + value, BAD_REQUEST, Map.of("field", "namingPrefix"));
        }
        return normalized;
    }

    private static void requireActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("warehouse layer actor is required");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static WarehouseLayerException error(String code, String message, WarehouseLayerException.Kind kind, Map<String, Object> details) {
        return new WarehouseLayerException(code, message, kind, details);
    }
}
