package com.yuzhi.dts.copilot.analytics.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class FinanceCaliberGuardrail {

    private static final Pattern COMMENT_PATTERN = Pattern.compile("--[^\\n]*|/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern SOURCE_TYPE_EIGHT_PATTERN = Pattern.compile(
            "\\bsource_type\\s*=\\s*8\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SALE_ACCOUNT_AMOUNT_AGGREGATE_PATTERN = Pattern.compile(
            "\\b(?:sum|avg)\\s*\\(\\s*(?:\\w+\\.)?(?:receivable_amount|biz_amount)\\s*\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BAD_DEBT_EXCLUSION_PATTERN = Pattern.compile(
            "\\bbiz_type\\s*(?:<>|!=)\\s*6\\b"
                    + "|\\bbiz_type\\s+not\\s+in\\s*\\([^)]*\\b6\\b[^)]*\\)"
                    + "|\\bbiz_type\\s*=\\s*(?:7|8)\\b"
                    + "|\\bbiz_type\\s+in\\s*\\(\\s*(?:7\\s*,\\s*8|8\\s*,\\s*7|7|8)\\s*\\)",
            Pattern.CASE_INSENSITIVE);

    List<Violation> validate(String sql) {
        if (sql == null || sql.isBlank()) {
            return List.of();
        }
        String normalizedSql = normalizeSql(sql);
        List<Violation> violations = new ArrayList<>();
        if ((containsAll(normalizedSql, "a_month_accounting", "a_sale_account") && normalizedSql.contains("sum("))
                || saleAccountIncomeMayIncludeBadDebt(normalizedSql)) {
            violations.add(new Violation(
                    "CAL-SETTLEMENT-CHAIN",
                    "a_sale_account 收入聚合必须排除 t_flower_biz_info.biz_type=6 坏账，坏账不能作为收入。"));
        }
        if (containsAll(normalizedSql, "a_month_accounting", "a_green_accounting", "a_sale_account")
                && SOURCE_TYPE_EIGHT_PATTERN.matcher(normalizedSql).find()
                && normalizedSql.contains("sum(")) {
            violations.add(new Violation(
                    "CAL-SALE-IN-RENT",
                    "a_green_accounting.source_type=8 已是业务单销售收入，不得再与 a_sale_account 重复求和。"));
        }
        return List.copyOf(violations);
    }

    void assertAllowed(String sql) {
        List<Violation> violations = validate(sql);
        if (violations.isEmpty()) {
            return;
        }
        String message = violations.stream()
                .map(violation -> violation.ruleId() + ": " + violation.reason())
                .reduce((left, right) -> left + "; " + right)
                .orElse("unknown violation");
        throw new IllegalArgumentException("Financial caliber guardrail blocked SQL: " + message);
    }

    private static boolean saleAccountIncomeMayIncludeBadDebt(String sql) {
        return sql.contains("a_sale_account")
                && SALE_ACCOUNT_AMOUNT_AGGREGATE_PATTERN.matcher(sql).find()
                && !BAD_DEBT_EXCLUSION_PATTERN.matcher(sql).find();
    }

    private static boolean containsAll(String value, String... tokens) {
        for (String token : tokens) {
            if (!value.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeSql(String sql) {
        String withoutComments = COMMENT_PATTERN.matcher(sql).replaceAll(" ");
        return WHITESPACE_PATTERN.matcher(withoutComments)
                .replaceAll(" ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    record Violation(String ruleId, String reason) {}
}
