package com.yuzhi.dts.platform.service.sql;

import com.yuzhi.dts.platform.service.sql.dto.PlanSnippet;
import com.yuzhi.dts.platform.service.sql.dto.SqlLimitInfo;
import com.yuzhi.dts.platform.service.sql.dto.SqlSummary;
import com.yuzhi.dts.platform.service.sql.dto.SqlValidateRequest;
import com.yuzhi.dts.platform.service.sql.dto.SqlValidateResponse;
import com.yuzhi.dts.platform.service.sql.dto.SqlViolation;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class SqlValidationService {

    /** Match DML/DDL as whole words — blocks leading "DROP", embedded DROP after comments, etc. */
    private static final Pattern WRITE_KEYWORD = Pattern.compile(
        "(?is)\\b(delete|update|insert|drop|truncate|alter|create|grant|revoke|merge|call|execute)\\b"
    );
    private static final Pattern LIMIT_KEYWORD = Pattern.compile("(?is)\\blimit\\b\\s+\\d+");

    public SqlValidateResponse validate(SqlValidateRequest request, Principal principal) {
        String rawSql = request.sqlText() != null ? request.sqlText().trim() : "";
        List<SqlViolation> violations = new ArrayList<>();

        if (rawSql.isBlank()) {
            violations.add(new SqlViolation("EMPTY_SQL", "SQL 语句不能为空", true));
        }

        // Strip comments before keyword matching so `-- drop` or `/* drop */` can't slip through.
        String stripped = stripSqlComments(rawSql);
        if (WRITE_KEYWORD.matcher(stripped).find()) {
            violations.add(new SqlViolation("WRITE_BLOCKED", "当前环境仅允许只读查询", true));
        }

        boolean hasLimit = LIMIT_KEYWORD.matcher(stripped).find();
        String rewritten = rawSql;
        List<String> warnings = new ArrayList<>();
        SqlLimitInfo limitInfo = null;
        if (!rawSql.isBlank() && !hasLimit) {
            rewritten = rawSql + " LIMIT 1000";
            warnings.add("已自动追加 LIMIT 1000");
            limitInfo = new SqlLimitInfo(true, 1000, "DEFAULT_LIMIT_POLICY");
        }

        SqlSummary summary = new SqlSummary(List.of(), hasLimit ? null : 1000, List.of());
        return new SqlValidateResponse(
            violations.stream().noneMatch(SqlViolation::blocking),
            rewritten,
            summary,
            violations,
            warnings,
            (PlanSnippet) null,
            limitInfo
        );
    }

    /**
     * Drop SQL line comments and block comments before keyword scanning.
     * String literals are left untouched — a {@code SELECT 'drop'} will therefore be
     * flagged as a write. We accept that false positive because genuine queries rarely
     * contain such literals and the security win is worth the noise.
     */
    static String stripSqlComments(String sql) {
        if (sql == null || sql.isEmpty()) return "";
        StringBuilder out = new StringBuilder(sql.length());
        int i = 0, n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            // Line comment — skip to EOL
            if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                int eol = sql.indexOf('\n', i + 2);
                if (eol < 0) break;
                i = eol + 1;
                out.append('\n');
                continue;
            }
            // Block comment — skip to closing */
            if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int close = sql.indexOf("*/", i + 2);
                if (close < 0) break;
                i = close + 2;
                out.append(' ');
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }
}
