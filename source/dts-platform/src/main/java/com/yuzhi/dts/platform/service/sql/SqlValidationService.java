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
import org.springframework.stereotype.Service;

@Service
public class SqlValidationService {

    public SqlValidateResponse validate(SqlValidateRequest request, Principal principal) {
        String rawSql = request.sqlText() != null ? request.sqlText().trim() : "";
        List<SqlViolation> violations = new ArrayList<>();

        if (rawSql.isBlank()) {
            violations.add(new SqlViolation("EMPTY_SQL", "SQL 语句不能为空", true));
        }

        String lowered = rawSql.toLowerCase();
        if (lowered.startsWith("delete") || lowered.startsWith("update") || lowered.startsWith("insert") || lowered.startsWith("drop")) {
            violations.add(new SqlViolation("WRITE_BLOCKED", "当前环境仅允许只读查询", true));
        }

        boolean hasLimit = lowered.contains(" limit ") || lowered.endsWith(" limit") || lowered.matches(".*limit\\s+\\d+.*");
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
}
