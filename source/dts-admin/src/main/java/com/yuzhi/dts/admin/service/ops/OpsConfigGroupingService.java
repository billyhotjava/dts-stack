package com.yuzhi.dts.admin.service.ops;

import com.yuzhi.dts.admin.domain.SystemConfig;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

@Service
public class OpsConfigGroupingService {

    private static final Logger log = LoggerFactory.getLogger(OpsConfigGroupingService.class);

    private static final Map<SystemConfig.Category, GroupMeta> CATEGORY_DEFAULT_GROUPS = Map.of(
        SystemConfig.Category.FEATURE_TOGGLE,
        new GroupMeta("feature-other", "功能开关-其他", 999),
        SystemConfig.Category.SECURITY,
        new GroupMeta("security-other", "安全配置-其他", 999),
        SystemConfig.Category.DATABASE,
        new GroupMeta("database-other", "数据库配置-其他", 999),
        SystemConfig.Category.INTEGRATION,
        new GroupMeta("integration-other", "集成配置-其他", 999),
        SystemConfig.Category.SYSTEM,
        new GroupMeta("system-other", "系统配置-其他", 999)
    );

    private volatile List<GroupRule> rules = List.of();

    @PostConstruct
    public void loadRules() {
        ClassPathResource resource = new ClassPathResource("config/ops-config-groups.yml");
        if (!resource.exists()) {
            log.warn("[ops-config] grouping rules not found: {}", resource.getPath());
            rules = List.of();
            return;
        }
        try (InputStream in = resource.getInputStream()) {
            Object data = new Yaml().load(in);
            if (!(data instanceof Map<?, ?> root)) {
                rules = List.of();
                return;
            }
            Object rawRules = root.get("rules");
            if (!(rawRules instanceof List<?> list) || list.isEmpty()) {
                rules = List.of();
                return;
            }
            List<GroupRule> loaded = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                GroupRule parsed = parseRule(map);
                if (parsed != null) {
                    loaded.add(parsed);
                }
            }
            loaded.sort(Comparator.comparingInt(GroupRule::groupOrder));
            rules = Collections.unmodifiableList(loaded);
            log.info("[ops-config] loaded grouping rules: {}", rules.size());
        } catch (Exception ex) {
            log.warn("[ops-config] failed to load grouping rules: {}", ex.getMessage());
            rules = List.of();
        }
    }

    public GroupMeta resolve(SystemConfig config) {
        if (config == null) {
            return new GroupMeta("unknown", "未分组", 9999);
        }
        SystemConfig.Category category = config.getCategory() != null ? config.getCategory() : SystemConfig.Category.SYSTEM;
        String key = config.getKey();
        for (GroupRule rule : rules) {
            if (rule.matches(category, key)) {
                return new GroupMeta(rule.groupKey(), rule.groupLabel(), rule.groupOrder());
            }
        }
        return CATEGORY_DEFAULT_GROUPS.getOrDefault(category, new GroupMeta("other", "其他", 9999));
    }

    private GroupRule parseRule(Map<?, ?> map) {
        String groupKey = asText(map.get("groupKey"));
        String groupLabel = asText(map.get("groupLabel"));
        String categoryName = asText(map.get("category"));
        Integer groupOrder = asInteger(map.get("groupOrder"), 999);
        if (!StringUtils.hasText(groupKey) || !StringUtils.hasText(groupLabel) || !StringUtils.hasText(categoryName)) {
            return null;
        }
        SystemConfig.Category category;
        try {
            category = SystemConfig.Category.valueOf(categoryName.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            log.warn("[ops-config] invalid category for group {}: {}", groupKey, categoryName);
            return null;
        }
        List<String> keys = asStringList(map.get("keys"));
        List<String> prefixes = asStringList(map.get("prefixes"));
        List<Pattern> regexes = asRegexList(map.get("regexes"));
        return new GroupRule(groupKey.trim(), groupLabel.trim(), category, groupOrder, keys, prefixes, regexes);
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer asInteger(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private List<String> asStringList(Object value) {
        if (!(value instanceof List<?> list) || CollectionUtils.isEmpty(list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            String text = item == null ? null : String.valueOf(item).trim();
            if (StringUtils.hasText(text)) {
                out.add(text);
            }
        }
        return out;
    }

    private List<Pattern> asRegexList(Object value) {
        List<String> raw = asStringList(value);
        if (raw.isEmpty()) {
            return List.of();
        }
        List<Pattern> out = new ArrayList<>();
        for (String expr : raw) {
            try {
                out.add(Pattern.compile(expr));
            } catch (Exception ex) {
                log.warn("[ops-config] invalid regex in grouping rule: {}", expr);
            }
        }
        return out;
    }

    public record GroupMeta(String groupKey, String groupLabel, int groupOrder) {}

    private record GroupRule(
        String groupKey,
        String groupLabel,
        SystemConfig.Category category,
        int groupOrder,
        List<String> keys,
        List<String> prefixes,
        List<Pattern> regexes
    ) {
        boolean matches(SystemConfig.Category category, String key) {
            if (category != this.category || !StringUtils.hasText(key)) {
                return false;
            }
            if (keys.stream().filter(Objects::nonNull).anyMatch(key::equals)) {
                return true;
            }
            if (prefixes.stream().filter(StringUtils::hasText).anyMatch(key::startsWith)) {
                return true;
            }
            return regexes.stream().anyMatch(pattern -> pattern.matcher(key).matches());
        }
    }
}
