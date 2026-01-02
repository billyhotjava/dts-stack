package com.yuzhi.dts.platform.service.security;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogMaskingRule;
import com.yuzhi.dts.platform.repository.catalog.CatalogMaskingRuleRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CatalogMaskingService {

    public record Context(boolean bypass, Map<String, String> columnStrategies, List<String> maskedColumns) {}

    private static final List<Map<String, Object>> TEMPLATES = List.of(
        Map.of("code", "NONE", "title", "不脱敏", "description", "不对字段做任何处理"),
        Map.of("code", "PARTIAL", "title", "部分隐藏", "description", "适用于姓名/证件号/手机号/邮箱等：保留少量字符，其余用 * 替换"),
        Map.of("code", "HASH", "title", "哈希", "description", "对字段值做 SHA-256 摘要，适用于不可逆去标识"),
        Map.of("code", "TOKENIZE", "title", "标记化", "description", "把字段值转换为稳定 token，适用于跨表关联但不暴露原值"),
        Map.of("code", "REDACT", "title", "固定替换", "description", "用固定占位符替换原值"),
        Map.of("code", "NULL", "title", "置空", "description", "将字段值置为 null")
    );

    private final CatalogMaskingRuleRepository maskingRuleRepository;

    public CatalogMaskingService(CatalogMaskingRuleRepository maskingRuleRepository) {
        this.maskingRuleRepository = maskingRuleRepository;
    }

    public List<Map<String, Object>> listTemplates() {
        return TEMPLATES;
    }

    public Context resolveContext(CatalogDataset dataset, List<String> headers) {
        boolean bypass = SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES);
        Map<String, String> columnStrategies = resolveColumnStrategies(dataset, headers);
        List<String> maskedColumns = bypass ? List.of() : new ArrayList<>(columnStrategies.keySet());
        return new Context(bypass, columnStrategies, maskedColumns);
    }

    public Map<String, Object> toMaskingMetadata(Context ctx) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (ctx == null) {
            meta.put("bypass", Boolean.FALSE);
            meta.put("maskedColumns", List.of());
            meta.put("ruleCount", 0);
            return meta;
        }
        meta.put("bypass", ctx.bypass());
        meta.put("maskedColumns", ctx.maskedColumns());
        meta.put("ruleCount", ctx.columnStrategies() != null ? ctx.columnStrategies().size() : 0);
        return meta;
    }

    public void applyMasking(Context ctx, List<String> headers, List<Map<String, Object>> rows) {
        if (ctx == null || ctx.bypass() || rows == null || rows.isEmpty()) {
            return;
        }
        Map<String, String> strategies = ctx.columnStrategies();
        if (strategies == null || strategies.isEmpty()) {
            return;
        }
        Map<String, String> actualHeaders = headers == null
            ? Map.of()
            : headers.stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.toMap(
                    h -> h.trim().toLowerCase(Locale.ROOT),
                    h -> h,
                    (a, b) -> a,
                    LinkedHashMap::new
                ));
        for (Map<String, Object> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, String> entry : strategies.entrySet()) {
                String column = entry.getKey();
                String strategy = entry.getValue();
                if (!StringUtils.hasText(column)) {
                    continue;
                }
                String actual = actualHeaders.getOrDefault(column.trim().toLowerCase(Locale.ROOT), column);
                if (!row.containsKey(actual)) {
                    continue;
                }
                Object original = row.get(actual);
                row.put(actual, MaskingFunctions.apply(original, strategy));
            }
        }
    }

    public Map<String, Object> preview(String strategy, Object value) {
        String normalized = normalizeStrategy(strategy);
        Object output = MaskingFunctions.apply(value, normalized);
        return Map.of(
            "strategy", normalized,
            "input", value != null ? String.valueOf(value) : null,
            "output", output != null ? String.valueOf(output) : null
        );
    }

    private Map<String, String> resolveColumnStrategies(CatalogDataset dataset, List<String> headers) {
        Map<String, String> strategies = new LinkedHashMap<>();
        if (dataset != null) {
            for (CatalogMaskingRule rule : maskingRuleRepository.findByDataset(dataset)) {
                String col = rule != null ? rule.getColumn() : null;
                if (!StringUtils.hasText(col)) {
                    continue;
                }
                String fn = rule.getFunction();
                strategies.put(col.trim(), normalizeStrategy(fn));
            }
        }
        if (!strategies.isEmpty()) {
            return strategies;
        }
        // No explicit rules: fallback to heuristic masking based on header naming.
        if (headers == null || headers.isEmpty()) {
            return strategies;
        }
        Set<String> masked = new LinkedHashSet<>();
        for (String h : headers) {
            if (!StringUtils.hasText(h)) {
                continue;
            }
            String lower = h.trim().toLowerCase(Locale.ROOT);
            if (lower.contains("name") || lower.contains("id") || lower.contains("phone") || lower.contains("mobile") || lower.contains("email")) {
                masked.add(h.trim());
            }
        }
        for (String col : masked) {
            strategies.put(col, "PARTIAL");
        }
        return strategies;
    }

    private String normalizeStrategy(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "NONE";
        }
        String trimmed = raw.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "MASK_EMAIL", "MASK_PHONE", "MASK" -> "PARTIAL";
            case "REDACT", "FIXED" -> "REDACT";
            default -> upper;
        };
    }
}

