package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovRule;
import com.yuzhi.dts.platform.domain.governance.GovRuleBinding;
import com.yuzhi.dts.platform.repository.governance.GovRuleBindingRepository;
import com.yuzhi.dts.platform.service.governance.dto.PreCheckResult;
import com.yuzhi.dts.platform.service.governance.dto.RuleCheckResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Bridge service that connects ingestion staging tables with governance quality rules
 * for pre-check validation before data is committed to the target dataset.
 */
@Service
public class IngestionQualityBridge {

    private static final Logger log = LoggerFactory.getLogger(IngestionQualityBridge.class);
    private static final int MAX_SAMPLE_ROWS = 100;
    private static final String STATUS_PUBLISHED = "PUBLISHED";

    private final GovRuleBindingRepository bindingRepository;
    private final PgStatementExecutor pgStatementExecutor;

    public IngestionQualityBridge(
        GovRuleBindingRepository bindingRepository,
        PgStatementExecutor pgStatementExecutor
    ) {
        this.bindingRepository = bindingRepository;
        this.pgStatementExecutor = pgStatementExecutor;
    }

    /**
     * Run quality pre-check on staging table data.
     *
     * Flow:
     * 1. Find all published rule bindings for the given datasetId
     * 2. For each binding:
     *    a. Get the rule's renderedSql
     *    b. Replace the original table name (from binding.datasetAlias) with stagingTableName
     *    c. Execute via PgStatementExecutor
     *    d. Collect failing rows
     * 3. Aggregate results
     *
     * @param stagingTableName the staging table to validate against
     * @param datasetId        the dataset whose rule bindings to apply
     * @param totalRows        total number of rows in the staging table
     * @return aggregated pre-check result
     */
    private static final Pattern VALID_STAGING_TABLE = Pattern.compile("^tmp_ingestion_[0-9a-f]{32}$");

    public PreCheckResult preCheck(String stagingTableName, UUID datasetId, int totalRows) {
        // Validate staging table name to prevent SQL injection
        if (stagingTableName == null || !VALID_STAGING_TABLE.matcher(stagingTableName).matches()) {
            throw new IllegalArgumentException("Invalid staging table name: " + stagingTableName);
        }

        List<GovRuleBinding> bindings = bindingRepository
            .findByDatasetIdAndRuleVersionStatus(datasetId, STATUS_PUBLISHED);

        if (bindings.isEmpty()) {
            log.debug("No published rule bindings found for dataset {}, all rows pass", datasetId);
            return new PreCheckResult(totalRows, totalRows, 0, List.of());
        }

        List<RuleCheckResult> ruleResults = new ArrayList<>();
        Set<Integer> allFailingRowNums = new HashSet<>();

        for (GovRuleBinding binding : bindings) {
            GovRule rule = binding.getRuleVersion().getRule();
            String renderedSql = rule.getExpression();

            if (!StringUtils.hasText(renderedSql)) {
                log.warn("Rule [{}] has no expression, skipping", rule.getName());
                continue;
            }

            String datasetAlias = binding.getDatasetAlias();
            if (!StringUtils.hasText(datasetAlias)) {
                log.warn("Binding for rule [{}] has no datasetAlias, skipping", rule.getName());
                continue;
            }

            // Replace the original table name with the staging table name (case-insensitive)
            String adjustedSql = replaceTableName(renderedSql, datasetAlias, stagingTableName);

            try {
                List<Map<String, Object>> failingRows =
                    pgStatementExecutor.executeQualityCheck(adjustedSql, MAX_SAMPLE_ROWS);

                if (!failingRows.isEmpty()) {
                    List<RuleCheckResult.FailingRow> sampleRows = new ArrayList<>();
                    for (Map<String, Object> row : failingRows) {
                        int rowNum = toInt(row.get("row_num"));
                        String column = toString(row.get("column_name"));
                        String actualValue = toString(row.get("actual_value"));
                        String reason = toString(row.get("reason"));

                        sampleRows.add(new RuleCheckResult.FailingRow(rowNum, column, actualValue, reason));
                        if (rowNum > 0) {
                            allFailingRowNums.add(rowNum);
                        }
                    }

                    ruleResults.add(new RuleCheckResult(
                        rule.getName(),
                        rule.getType(),
                        failingRows.size(),
                        sampleRows
                    ));
                }
            } catch (Exception e) {
                log.warn("Failed to execute quality check for rule [{}]: {}", rule.getName(), e.getMessage());
            }
        }

        int failedRows = allFailingRowNums.size();
        int passedRows = Math.max(0, totalRows - failedRows);

        return new PreCheckResult(totalRows, passedRows, failedRows, ruleResults);
    }

    /**
     * Replace occurrences of the original table name with the staging table name in SQL.
     * Uses word-boundary matching to avoid partial replacements, case-insensitive.
     */
    private String replaceTableName(String sql, String originalTable, String stagingTable) {
        // Use word boundary regex for robust replacement
        String pattern = "(?i)\\b" + Pattern.quote(originalTable) + "\\b";
        return sql.replaceAll(pattern, stagingTable);
    }

    private int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number num) {
            return num.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String toString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
