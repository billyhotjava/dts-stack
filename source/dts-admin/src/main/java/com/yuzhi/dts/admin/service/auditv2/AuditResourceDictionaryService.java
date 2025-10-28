package com.yuzhi.dts.admin.service.auditv2;

import com.yuzhi.dts.admin.domain.AuditResourceDictionary;
import com.yuzhi.dts.admin.repository.AuditResourceDictionaryRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AuditResourceDictionaryService {

    private static final Logger log = LoggerFactory.getLogger(AuditResourceDictionaryService.class);

    private final AuditResourceDictionaryRepository repository;
    private final JdbcTemplate jdbcTemplate;

    private volatile Map<String, DictionaryEntry> exactEntries = new ConcurrentHashMap<>();
    private final List<PrefixEntry> prefixEntries = new CopyOnWriteArrayList<>();
    private volatile List<String> moduleCategories = List.of();

    public AuditResourceDictionaryService(
        AuditResourceDictionaryRepository repository,
        JdbcTemplate jdbcTemplate
    ) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
        reload();
    }

    @Scheduled(fixedDelay = 60000L)
    public void reload() {
        if (!dictionaryTableExists()) {
            log.debug("audit_resource_dictionary table not available yet; skip dictionary reload");
            return;
        }
        try {
            List<AuditResourceDictionary> rows = repository.findAllByEnabledTrueOrderByOrderValueAscResourceKeyAsc();
            Map<String, DictionaryEntry> exact = new ConcurrentHashMap<>();
            List<PrefixEntry> prefixes = new ArrayList<>();
            LinkedHashMap<String, Integer> categoryOrders = new LinkedHashMap<>();
            for (AuditResourceDictionary row : rows) {
                DictionaryEntry entry = new DictionaryEntry(
                    normalize(row.getResourceKey()),
                    safeTrim(row.getDisplayName()),
                    safeTrim(row.getCategory()),
                    row.getOrderValue()
                );
                if (entry.key() != null) {
                    exact.put(entry.key(), entry);
                }
                String displayKey = normalize(entry.displayName());
                if (displayKey != null) {
                    exact.putIfAbsent(displayKey, entry);
                }
                for (String alias : splitAliases(row.getAliases())) {
                    boolean isPrefix = alias.endsWith("*");
                    String token = isPrefix ? alias.substring(0, alias.length() - 1) : alias;
                    String normalized = normalize(token);
                    if (!StringUtils.hasText(normalized)) {
                        continue;
                    }
                    if (isPrefix) {
                        prefixes.add(new PrefixEntry(normalized, entry));
                    } else {
                        exact.putIfAbsent(normalized, entry);
                    }
                }
                if (StringUtils.hasText(entry.category())) {
                    int order = entry.order() == null ? Integer.MAX_VALUE : entry.order();
                    categoryOrders.putIfAbsent(entry.category(), order);
                }
            }
            prefixes.sort(Comparator.comparingInt((PrefixEntry p) -> p.prefix().length()).reversed());
            prefixEntries.clear();
            prefixEntries.addAll(prefixes);
            exactEntries = exact;
            List<Map.Entry<String, Integer>> sorted = new ArrayList<>(categoryOrders.entrySet());
            sorted.sort(Map.Entry.comparingByValue());
            List<String> categories = new ArrayList<>(sorted.size());
            for (Map.Entry<String, Integer> entry : sorted) {
                categories.add(entry.getKey());
            }
            moduleCategories = Collections.unmodifiableList(categories);
            log.info("Loaded {} audit resource dictionary entries ({} prefixes)", exact.size(), prefixes.size());
        } catch (DataAccessException ex) {
            log.debug("Failed to reload audit resource dictionary ({}); will retry", ex.getMessage());
        } catch (Exception ex) {
            log.warn("Unexpected error reloading audit resource dictionary", ex);
        }
    }

    public Optional<DictionaryEntry> findEntry(String key) {
        String normalized = normalize(key);
        if (normalized == null) {
            return Optional.empty();
        }
        DictionaryEntry entry = exactEntries.get(normalized);
        if (entry != null) {
            return Optional.of(entry);
        }
        for (PrefixEntry prefixEntry : prefixEntries) {
            if (normalized.startsWith(prefixEntry.prefix())) {
                return Optional.of(prefixEntry.entry());
            }
        }
        return Optional.empty();
    }

    public Optional<String> resolveLabel(String key) {
        return findEntry(key).map(DictionaryEntry::displayName).filter(StringUtils::hasText);
    }

    public Optional<String> resolveCategory(String key) {
        return findEntry(key).map(DictionaryEntry::category).filter(StringUtils::hasText);
    }

    public List<String> listModuleCategories() {
        return moduleCategories;
    }

    private boolean dictionaryTableExists() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema() AND LOWER(table_name) = ?",
                Integer.class,
                "audit_resource_dictionary"
            );
            return count != null && count > 0;
        } catch (DataAccessException ex) {
            return false;
        }
    }

    private List<String> splitAliases(String aliases) {
        if (!StringUtils.hasText(aliases)) {
            return Collections.emptyList();
        }
        String[] tokens = aliases.split("[,\\n\\r]");
        List<String> out = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            String normalized = safeTrim(token);
            if (normalized != null && !normalized.isBlank()) {
                out.add(normalized);
            }
        }
        return out;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String safeTrim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record DictionaryEntry(String key, String displayName, String category, Integer order) {}

    private record PrefixEntry(String prefix, DictionaryEntry entry) {}
}
