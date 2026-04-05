package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovCleansingFunction;
import com.yuzhi.dts.platform.repository.governance.GovCleansingFunctionRepository;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DataCleansingService {

    private static final Logger log = LoggerFactory.getLogger(DataCleansingService.class);
    // 白名单：只清洗 ods_ 前缀的表
    private static final String TABLE_PREFIX = "ods_";
    // 标识符白名单正则（与 SqlTemplateRenderer 一致）
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_.]*$");
    // 系统列排除列表
    private static final Set<String> EXCLUDED_COLUMNS = Set.of(
        "id", "source_system", "import_time", "_quality_blocked", "_quality_checked_at"
    );
    // 文本类型关键词（小写匹配）
    private static final Set<String> TEXT_TYPE_KEYWORDS = Set.of("varchar", "text", "character varying");

    private final GovCleansingFunctionRepository functionRepository;
    private final DataSource dataSource;

    public DataCleansingService(GovCleansingFunctionRepository functionRepository, DataSource dataSource) {
        this.functionRepository = functionRepository;
        this.dataSource = dataSource;
    }

    public CleansingResult cleanse(String tableName) {
        // 1. 校验表名（只允许 ods_ 前缀，标识符白名单）
        validateTableName(tableName);

        List<CleansingResult.FunctionResult> details = new ArrayList<>();
        int totalRowsAffected = 0;
        int totalColumnsProcessed = 0;

        // 3. 获取所有 enabled 的清洗函数，按 display_order 排序
        List<GovCleansingFunction> functions = functionRepository.findByEnabledTrueOrderByDisplayOrderAsc();
        if (functions.isEmpty()) {
            log.info("No enabled cleansing functions found, skipping cleansing for table [{}]", tableName);
            return new CleansingResult(tableName, 0, 0, 0, details);
        }

        try (Connection conn = dataSource.getConnection()) {
            // 2. 获取表的列元数据（只处理 varchar/text/character varying 类型的列）
            List<String> textColumns = getTextColumns(conn, tableName);
            if (textColumns.isEmpty()) {
                log.info("No text columns found in table [{}], skipping cleansing", tableName);
                return new CleansingResult(tableName, 0, 0, 0, details);
            }

            totalColumnsProcessed = textColumns.size();

            // 4. 对每个函数、每个文本列，生成并执行 UPDATE SQL
            for (GovCleansingFunction function : functions) {
                for (String column : textColumns) {
                    try {
                        int rows = applyFunction(conn, tableName, column, function);
                        totalRowsAffected += rows;
                        details.add(new CleansingResult.FunctionResult(
                            function.getCode(), function.getName(), column, rows));
                        if (rows > 0) {
                            log.info("Cleansing function [{}] on [{}].{}: {} rows affected",
                                function.getCode(), tableName, column, rows);
                        }
                    } catch (Exception e) {
                        log.warn("Cleansing function [{}] failed on [{}].{}: {}",
                            function.getCode(), tableName, column, e.getMessage());
                        details.add(new CleansingResult.FunctionResult(
                            function.getCode(), function.getName(), column, 0));
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to connect to database for cleansing table [" + tableName + "]", e);
        }

        // 6. 返回 CleansingResult
        log.info("Cleansing completed for table [{}]: {} functions, {} columns, {} total rows affected",
            tableName, functions.size(), totalColumnsProcessed, totalRowsAffected);
        return new CleansingResult(tableName, functions.size(), totalColumnsProcessed, totalRowsAffected, details);
    }

    private void validateTableName(String tableName) {
        if (tableName == null || !tableName.startsWith(TABLE_PREFIX)) {
            throw new IllegalArgumentException("Table name must start with '" + TABLE_PREFIX + "': " + tableName);
        }
        if (!SAFE_IDENTIFIER.matcher(tableName).matches()) {
            throw new IllegalArgumentException(
                "Table name contains illegal characters: " + tableName + " (only letters, digits, underscores and dots allowed)");
        }
    }

    /**
     * 获取表的文本类型列名。
     * 用 DatabaseMetaData.getColumns() 获取，过滤 TYPE_NAME 包含 varchar/text/character varying 的列，
     * 排除系统列。
     */
    private List<String> getTextColumns(Connection conn, String tableName) throws SQLException {
        List<String> columns = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getColumns(null, null, tableName, null)) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                String typeName = rs.getString("TYPE_NAME").toLowerCase(Locale.ROOT);

                // 排除系统列
                if (EXCLUDED_COLUMNS.contains(columnName)) {
                    continue;
                }

                // 只保留文本类型列
                boolean isTextType = TEXT_TYPE_KEYWORDS.stream().anyMatch(typeName::contains);
                if (isTextType) {
                    columns.add(columnName);
                }
            }
        }
        return columns;
    }

    /**
     * 对单个列执行单个清洗函数。
     * 将 function.getSqlExpression() 中的 {{column}} 替换为实际列名，
     * 生成 UPDATE SQL 并执行，返回影响行数。
     */
    private int applyFunction(Connection conn, String tableName, String columnName,
                              GovCleansingFunction function) throws SQLException {
        // 列名安全校验
        if (!SAFE_IDENTIFIER.matcher(columnName).matches()) {
            throw new IllegalArgumentException("Column name contains illegal characters: " + columnName);
        }

        // 1. 将 {{column}} 替换为实际列名
        String renderedExpression = function.getSqlExpression().replace("{{column}}", columnName);

        // 2. 生成 UPDATE SQL
        String sql = "UPDATE " + tableName
            + " SET " + columnName + " = " + renderedExpression
            + " WHERE " + columnName + " IS NOT NULL"
            + " AND " + columnName + " != " + renderedExpression;

        // 3. 执行并返回影响行数
        try (Statement stmt = conn.createStatement()) {
            return stmt.executeUpdate(sql);
        }
    }
}
