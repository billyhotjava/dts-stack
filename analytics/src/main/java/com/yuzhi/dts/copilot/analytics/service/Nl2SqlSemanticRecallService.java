package com.yuzhi.dts.copilot.analytics.service;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsField;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsNl2SqlEvalCase;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsSynonym;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsTable;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsFieldRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsNl2SqlEvalCaseRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsSynonymRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsTableRepository;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class Nl2SqlSemanticRecallService {

    private static final Logger log = LoggerFactory.getLogger(Nl2SqlSemanticRecallService.class);

    private static final Map<String, String> BUILTIN_SYNONYMS = Map.ofEntries(
            Map.entry("产量", "output_qty"),
            Map.entry("产能", "capacity_qty"),
            Map.entry("能耗", "energy_consumption"),
            Map.entry("电耗", "energy_consumption"),
            Map.entry("收入", "revenue_amount"),
            Map.entry("销量", "sales_qty"),
            Map.entry("良率", "yield_rate"),
            Map.entry("合格率", "quality_pass_rate"),
            Map.entry("告警", "alarm_count"),
            Map.entry("停机", "downtime_minutes"));

    /** Merged synonym map: DB entries override built-in ones with the same term. */
    private final Map<String, String> mergedSynonyms = new ConcurrentHashMap<>();

    private final AnalyticsTableRepository tableRepository;
    private final AnalyticsFieldRepository fieldRepository;
    private final AnalyticsNl2SqlEvalCaseRepository evalCaseRepository;
    private final AnalyticsSynonymRepository synonymRepository;

    public Nl2SqlSemanticRecallService(
            AnalyticsTableRepository tableRepository,
            AnalyticsFieldRepository fieldRepository,
            AnalyticsNl2SqlEvalCaseRepository evalCaseRepository,
            AnalyticsSynonymRepository synonymRepository) {
        this.tableRepository = tableRepository;
        this.fieldRepository = fieldRepository;
        this.evalCaseRepository = evalCaseRepository;
        this.synonymRepository = synonymRepository;
    }

    @PostConstruct
    void loadSynonyms() {
        refreshSynonymCache();
    }

    /** Reload synonym cache from DB, merging with built-in synonyms. */
    public void refreshSynonymCache() {
        Map<String, String> fresh = new LinkedHashMap<>(BUILTIN_SYNONYMS);
        try {
            List<AnalyticsSynonym> dbSynonyms = synonymRepository.findAll();
            for (AnalyticsSynonym s : dbSynonyms) {
                fresh.put(s.getTerm(), s.getColumnName());
            }
            log.info("Synonym cache refreshed: {} built-in + {} from DB = {} total",
                    BUILTIN_SYNONYMS.size(), dbSynonyms.size(), fresh.size());
        } catch (Exception e) {
            log.warn("Failed to load synonyms from DB, using built-in only", e);
        }
        mergedSynonyms.clear();
        mergedSynonyms.putAll(fresh);
    }

    public SemanticRecallResult recall(
            String prompt,
            String domain,
            Long databaseId,
            List<String> dimensions,
            List<String> metrics,
            int schemaTopK,
            int fewShotTopK) {
        String normalizedPrompt = prompt == null ? "" : prompt.trim();
        Set<String> tokens = tokenize(normalizedPrompt);
        List<SchemaCandidate> schemaCandidates = recallSchema(tokens, databaseId, schemaTopK);
        List<SynonymHit> synonymHits = recallSynonyms(normalizedPrompt);
        List<FewShotExample> fewShotExamples = recallFewShot(tokens, domain, fewShotTopK);

        List<String> promptHints = new ArrayList<>();
        for (SynonymHit hit : synonymHits) {
            promptHints.add("业务词[" + hit.keyword() + "] -> 字段[" + hit.field() + "]");
        }
        for (SchemaCandidate candidate : schemaCandidates) {
            promptHints.add("候选表[" + candidate.schemaName() + "." + candidate.tableName() + "] 匹配分 " + candidate.score());
        }
        for (FewShotExample example : fewShotExamples) {
            promptHints.add("参考样本#" + example.caseId() + " " + example.title());
        }

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("promptTokenCount", tokens.size());
        trace.put("schemaCandidateCount", schemaCandidates.size());
        trace.put("synonymHitCount", synonymHits.size());
        trace.put("fewShotCount", fewShotExamples.size());
        trace.put("schemaTopK", schemaTopK);
        trace.put("fewShotTopK", fewShotTopK);
        trace.put("source", List.of("analytics_table", "analytics_field", "analytics_nl2sql_eval_case"));
        trace.put("domain", domain);
        trace.put("dimensions", dimensions == null ? List.of() : dimensions);
        trace.put("metrics", metrics == null ? List.of() : metrics);

        return new SemanticRecallResult(schemaCandidates, synonymHits, fewShotExamples, promptHints, trace);
    }

    private List<SchemaCandidate> recallSchema(Set<String> tokens, Long databaseId, int topK) {
        int safeTopK = Math.max(1, Math.min(topK, 20));
        List<AnalyticsTable> tables = databaseId != null && databaseId > 0
                ? tableRepository.findAllByDatabaseIdOrderBySchemaNameAscNameAsc(databaseId)
                : tableRepository.findAll();
        if (tables.isEmpty()) {
            return List.of();
        }
        Map<Long, List<AnalyticsField>> fieldsByTable = new LinkedHashMap<>();
        for (AnalyticsTable table : tables) {
            fieldsByTable.put(table.getId(), fieldRepository.findAllByTableIdOrderByPositionAscIdAsc(table.getId()));
        }

        List<SchemaCandidate> candidates = new ArrayList<>();
        for (AnalyticsTable table : tables) {
            if (!table.isActive()) {
                continue;
            }
            List<AnalyticsField> fields = fieldsByTable.getOrDefault(table.getId(), List.of());
            int score = scoreTable(table, fields, tokens);
            if (score <= 0) {
                continue;
            }
            List<String> matchedFields = fields.stream()
                    .filter(field -> matchToken(field.getName(), tokens) || matchToken(field.getDisplayName(), tokens))
                    .limit(6)
                    .map(field -> field.getDisplayName() == null || field.getDisplayName().isBlank()
                            ? field.getName()
                            : field.getDisplayName() + "(" + field.getName() + ")")
                    .toList();
            candidates.add(new SchemaCandidate(
                    table.getId(),
                    table.getDatabaseId(),
                    table.getSchemaName(),
                    table.getName(),
                    table.getDisplayName(),
                    score,
                    matchedFields));
        }
        return candidates.stream()
                .sorted((left, right) -> Integer.compare(right.score(), left.score()))
                .limit(safeTopK)
                .toList();
    }

    private int scoreTable(AnalyticsTable table, List<AnalyticsField> fields, Set<String> tokens) {
        if (tokens.isEmpty()) {
            return 0;
        }
        int score = 0;
        if (matchToken(table.getName(), tokens)) {
            score += 30;
        }
        if (matchToken(table.getDisplayName(), tokens)) {
            score += 40;
        }
        if (matchToken(table.getDescription(), tokens)) {
            score += 20;
        }
        int fieldMatches = 0;
        for (AnalyticsField field : fields) {
            if (matchToken(field.getName(), tokens) || matchToken(field.getDisplayName(), tokens)) {
                fieldMatches++;
            }
        }
        score += fieldMatches * 6;
        return score;
    }

    private List<SynonymHit> recallSynonyms(String prompt) {
        String normalized = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        List<SynonymHit> hits = new ArrayList<>();
        for (Map.Entry<String, String> entry : mergedSynonyms.entrySet()) {
            if (normalized.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                String source = BUILTIN_SYNONYMS.containsKey(entry.getKey()) ? "builtin" : "db";
                hits.add(new SynonymHit(entry.getKey(), entry.getValue(), source));
            }
        }
        return hits;
    }

    private List<FewShotExample> recallFewShot(Set<String> tokens, String domain, int topK) {
        int safeTopK = Math.max(1, Math.min(topK, 10));
        List<AnalyticsNl2SqlEvalCase> rows =
                evalCaseRepository.findAllByEnabledTrueOrderByIdAsc(PageRequest.of(0, 300));
        List<FewShotExample> scored = new ArrayList<>();
        for (AnalyticsNl2SqlEvalCase row : rows) {
            String prompt = row.getPromptText();
            if (prompt == null || prompt.isBlank()) {
                continue;
            }
            int score = overlapScore(tokens, tokenize(prompt));
            if (score <= 0) {
                continue;
            }
            if (domain != null && row.getDomain() != null && !domain.equalsIgnoreCase(row.getDomain())) {
                score -= 1;
            }
            scored.add(new FewShotExample(row.getId(), row.getName(), prompt, row.getDomain(), score));
        }
        return scored.stream()
                .sorted((left, right) -> Integer.compare(right.score(), left.score()))
                .limit(safeTopK)
                .toList();
    }

    private int overlapScore(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        int hits = 0;
        for (String token : left) {
            if (right.contains(token)) {
                hits++;
            }
        }
        return hits;
    }

    private boolean matchToken(String text, Set<String> tokens) {
        if (text == null || text.isBlank() || tokens.isEmpty()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (token.length() >= 2 && normalized.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        String[] parts = text.toLowerCase(Locale.ROOT).split("[^\\p{IsAlphabetic}\\p{IsDigit}_]+");
        Set<String> out = new LinkedHashSet<>();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (part.length() == 1) {
                continue;
            }
            out.add(part);
        }
        return out;
    }

    public record SchemaCandidate(
            Long tableId,
            Long databaseId,
            String schemaName,
            String tableName,
            String tableDisplayName,
            int score,
            List<String> matchedFields) {}

    public record SynonymHit(String keyword, String field, String source) {}

    public record FewShotExample(Long caseId, String title, String prompt, String domain, int score) {}

    public record SemanticRecallResult(
            List<SchemaCandidate> schemaCandidates,
            List<SynonymHit> synonymHits,
            List<FewShotExample> fewShotExamples,
            List<String> promptHints,
            Map<String, Object> trace) {}
}
