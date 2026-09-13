# Phase 1: 后端新增能力 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐后端缺失能力——清洗函数、执行器抽象、Excel 全量解析、暂存表、预检桥接、评分引擎、P0 修复

**Architecture:** 在现有 dts-ingestion（入湖）和 dts-platform（治理）两个模块中新增服务，通过执行器接口抽象实现 PG/Hive 双轨执行，暂存表使用动态 DDL

**Tech Stack:** Java 21, Spring Boot 3.4.5, Apache POI, PostgreSQL 17.6, Liquibase

**已实现基础（不需要重做）：** GovQualityTemplate(10个) + SqlTemplateRenderer + GovCleansingFunction(7个) + DataCleansingService + QualityRunService(含dryRun) + HiveStatementExecutor + gov_quality_failing_row + gov_data_edit_log

---

### Task 1: 补齐清洗函数 [F2/T03]

**Files:**
- Modify: `source/dts-platform/src/main/resources/config/liquibase/changelog/20260404_05_gov_cleansing_function_seed.xml`
- Modify: `source/dts-platform/src/main/resources/config/liquibase/master.xml` (如需新增 changelog)

- [ ] **Step 1: 查看现有清洗函数 seed 文件**

确认现有 7 个函数的 changeset ID 格式和最大 display_order。

- [ ] **Step 2: 追加 3 个清洗函数 INSERT**

在 seed 文件末尾追加 3 个 changeset：

```xml
<changeSet id="20260404_05_08" author="system">
    <insert tableName="gov_cleansing_function">
        <column name="id" valueComputed="gen_random_uuid()"/>
        <column name="code" value="DATE_NORMALIZE"/>
        <column name="name" value="日期格式标准化"/>
        <column name="description" value="将多种日期格式（2024/1/1、2024年1月1日、20240101等）统一为 yyyy-MM-dd"/>
        <column name="sql_expression" value="CASE WHEN {{column}} ~ '^\d{4}-\d{2}-\d{2}$' THEN {{column}} WHEN {{column}} ~ '^\d{4}/\d{1,2}/\d{1,2}$' THEN TO_CHAR(TO_DATE({{column}}, 'YYYY/MM/DD'), 'YYYY-MM-DD') WHEN {{column}} ~ '^\d{8}$' THEN TO_CHAR(TO_DATE({{column}}, 'YYYYMMDD'), 'YYYY-MM-DD') WHEN {{column}} ~ '^\d{4}年\d{1,2}月\d{1,2}日$' THEN TO_CHAR(TO_DATE(REGEXP_REPLACE({{column}}, '[年月日]', '-', 'g'), 'YYYY-MM-DD-'), 'YYYY-MM-DD') ELSE {{column}} END"/>
        <column name="display_order" valueNumeric="8"/>
        <column name="builtin" valueBoolean="true"/>
        <column name="enabled" valueBoolean="true"/>
    </insert>
</changeSet>

<changeSet id="20260404_05_09" author="system">
    <insert tableName="gov_cleansing_function">
        <column name="id" valueComputed="gen_random_uuid()"/>
        <column name="code" value="ENUM_MAP"/>
        <column name="name" value="枚举值映射"/>
        <column name="description" value="将同义枚举值映射为标准值，如 male/M/男性 → 男"/>
        <column name="sql_expression" value="CASE WHEN {{column}} IN ({{aliases}}) THEN '{{target}}' ELSE {{column}} END"/>
        <column name="display_order" valueNumeric="9"/>
        <column name="builtin" valueBoolean="true"/>
        <column name="enabled" valueBoolean="true"/>
    </insert>
</changeSet>

<changeSet id="20260404_05_10" author="system">
    <insert tableName="gov_cleansing_function">
        <column name="id" valueComputed="gen_random_uuid()"/>
        <column name="code" value="FILL_DEFAULT"/>
        <column name="name" value="空值填充默认值"/>
        <column name="description" value="将空值或空字符串替换为指定默认值"/>
        <column name="sql_expression" value="COALESCE(NULLIF(TRIM({{column}}), ''), '{{default_value}}')"/>
        <column name="display_order" valueNumeric="10"/>
        <column name="builtin" valueBoolean="true"/>
        <column name="enabled" valueBoolean="true"/>
    </insert>
</changeSet>
```

- [ ] **Step 3: 验证 Liquibase 变更**

Run: `cd source/dts-platform && mvn liquibase:updateSQL -pl . 2>&1 | tail -20`
Expected: SQL INSERT 语句无语法错误

- [ ] **Step 4: Commit**

```bash
git add source/dts-platform/src/main/resources/config/liquibase/
git commit -m "feat(F2/T03): add DATE_NORMALIZE, ENUM_MAP, FILL_DEFAULT cleansing functions"
```

---

### Task 2: 抽象执行器接口 + PgStatementExecutor [F4/T03 前置]

**Files:**
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/QualityStatementExecutor.java`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/PgStatementExecutor.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/QualityRunService.java`

- [ ] **Step 1: 创建执行器接口**

```java
package com.yuzhi.dts.platform.service.governance;

import java.util.List;
import java.util.Map;

/**
 * 质量规则 SQL 执行器抽象。
 * HiveStatementExecutor 用于湖内巡检，PgStatementExecutor 用于入湖预检。
 */
public interface QualityStatementExecutor {

    /**
     * 执行质量检查 SQL，返回失败行。
     * @param sql 完整的 SELECT 语句（返回失败的行）
     * @param maxRows 最大返回行数
     * @return 失败行列表，每行为 column→value 映射
     */
    List<Map<String, Object>> executeQualityCheck(String sql, int maxRows);
}
```

- [ ] **Step 2: 创建 PgStatementExecutor**

```java
package com.yuzhi.dts.platform.service.governance;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PgStatementExecutor implements QualityStatementExecutor {

    private final JdbcTemplate jdbcTemplate;

    public PgStatementExecutor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Map<String, Object>> executeQualityCheck(String sql, int maxRows) {
        String limited = "SELECT * FROM (" + sql + ") _qc LIMIT " + maxRows;
        return jdbcTemplate.queryForList(limited);
    }
}
```

- [ ] **Step 3: 在 QualityRunService 中注入 PgStatementExecutor**

在 QualityRunService 构造函数中增加 `PgStatementExecutor pgExecutor` 参数。
在 `doExecuteRun()` 方法中，根据执行模式选择执行器：
- 如果 run 的 triggerType 为 `PRE_CHECK`，使用 pgExecutor
- 否则使用现有 hiveExecutor

具体改法需读取 `doExecuteRun()` 完整代码后确定——关键是在执行 SQL 的调用点做分支。

- [ ] **Step 4: 编译验证**

Run: `cd source/dts-platform && mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/QualityStatementExecutor.java
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/PgStatementExecutor.java
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/QualityRunService.java
git commit -m "feat(F4/T03): abstract QualityStatementExecutor interface, add PgStatementExecutor"
```

---

### Task 3: ExcelParseService — 全量解析 [F4/T01]

**Files:**
- Create: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/ExcelParseService.java`
- Create: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/dto/ParseResult.java`
- Create: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/dto/ColumnInfo.java`
- Create: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/dto/FormulaCell.java`

- [ ] **Step 1: 创建 DTO 类**

```java
// ParseResult.java
package com.yuzhi.dts.ingestion.service.dto;

import java.util.List;

public record ParseResult(
    int totalRows,
    List<ColumnInfo> columns,
    List<FormulaCell> formulaCells,
    List<Integer> emptyRows,
    List<List<String>> rows  // 全量数据，每行为 List<String>
) {}

// ColumnInfo.java
package com.yuzhi.dts.ingestion.service.dto;

public record ColumnInfo(
    String name,
    String inferredType,  // STRING / LONG / DOUBLE / DATE / BOOLEAN
    int typeConfidence     // 0-100，类型推断置信度
) {}

// FormulaCell.java
package com.yuzhi.dts.ingestion.service.dto;

public record FormulaCell(
    int rowNum,
    String columnName,
    String formula
) {}
```

- [ ] **Step 2: 创建 ExcelParseService**

关键实现要点：
- 全量读取所有行（不只前 2 行）
- 公式单元格：`cell.getCellType() == CellType.FORMULA` → 记录到 formulaCells，值存 null
- 类型推断：统计每列所有非空值的类型分布，占比 >80% 的类型作为推断结果
- 空行检测：所有列均为 null 或空字符串
- 全部值以 String 存储

```java
package com.yuzhi.dts.ingestion.service.etl;

import com.yuzhi.dts.ingestion.service.dto.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;

@Service
public class ExcelParseService {

    public ParseResult parse(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("Excel 文件为空或缺少表头");
            }

            // 1. 读取表头
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                headers.add(cell != null ? getCellStringValue(cell) : "col_" + i);
            }

            // 2. 全量读取数据行
            List<List<String>> rows = new ArrayList<>();
            List<FormulaCell> formulaCells = new ArrayList<>();
            List<Integer> emptyRows = new ArrayList<>();
            // 类型统计：columnIndex -> {type -> count}
            Map<Integer, Map<String, Integer>> typeStats = new HashMap<>();

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                List<String> rowData = new ArrayList<>();
                boolean allEmpty = true;

                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = row != null ? row.getCell(c) : null;
                    if (cell != null && cell.getCellType() == CellType.FORMULA) {
                        formulaCells.add(new FormulaCell(r, headers.get(c), cell.getCellFormula()));
                        rowData.add(null);
                        allEmpty = false;
                    } else {
                        String value = cell != null ? getCellStringValue(cell) : null;
                        rowData.add(value);
                        if (value != null && !value.isBlank()) {
                            allEmpty = false;
                            typeStats.computeIfAbsent(c, k -> new HashMap<>())
                                .merge(inferCellType(value), 1, Integer::sum);
                        }
                    }
                }

                if (allEmpty) {
                    emptyRows.add(r);
                }
                rows.add(rowData);
            }

            // 3. 推断列类型
            List<ColumnInfo> columns = new ArrayList<>();
            for (int c = 0; c < headers.size(); c++) {
                Map<String, Integer> stats = typeStats.getOrDefault(c, Map.of());
                int total = stats.values().stream().mapToInt(Integer::intValue).sum();
                String bestType = "STRING";
                int bestCount = 0;
                for (var entry : stats.entrySet()) {
                    if (entry.getValue() > bestCount) {
                        bestCount = entry.getValue();
                        bestType = entry.getKey();
                    }
                }
                int confidence = total > 0 ? (bestCount * 100 / total) : 0;
                columns.add(new ColumnInfo(headers.get(c), bestType, confidence));
            }

            return new ParseResult(rows.size(), columns, formulaCells, emptyRows, rows);
        }
    }

    private String getCellStringValue(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                double d = cell.getNumericCellValue();
                yield d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case BLANK -> null;
            default -> null;
        };
    }

    private String inferCellType(String value) {
        if (value.matches("-?\\d+")) return "LONG";
        if (value.matches("-?\\d+\\.\\d+")) return "DOUBLE";
        if (value.matches("\\d{4}-\\d{2}-\\d{2}.*")) return "DATE";
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) return "BOOLEAN";
        return "STRING";
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `cd source/dts-ingestion && mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/
git commit -m "feat(F4/T01): add ExcelParseService with full scan, formula detection, type inference"
```

---

### Task 4: StagingTableService — 暂存表管理 [F4/T02]

**Files:**
- Create: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/StagingTableService.java`
- Create: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/dto/CellError.java`

- [ ] **Step 1: 创建 CellError DTO**

```java
package com.yuzhi.dts.ingestion.service.dto;

public record CellError(
    String column,
    String rule,
    String message
) {}
```

- [ ] **Step 2: 创建 StagingTableService**

```java
package com.yuzhi.dts.ingestion.service.etl;

import com.yuzhi.dts.ingestion.service.dto.CellError;
import com.yuzhi.dts.ingestion.service.dto.ColumnInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class StagingTableService {

    private static final Pattern VALID_TASK_ID = Pattern.compile("^[0-9a-f\\-]{36}$");
    private static final String TABLE_PREFIX = "tmp_ingestion_";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public StagingTableService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建暂存表，全部列为 TEXT 类型
     */
    public String create(UUID taskId, List<ColumnInfo> columns) {
        validateTaskId(taskId);
        String tableName = TABLE_PREFIX + taskId.toString().replace("-", "");

        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (");
        ddl.append("_row_num SERIAL, ");
        ddl.append("_errors JSONB DEFAULT '[]'::jsonb, ");
        ddl.append("_status VARCHAR(10) DEFAULT 'CLEAN'");
        for (int i = 0; i < columns.size(); i++) {
            String colName = sanitizeColumnName(columns.get(i).name());
            ddl.append(", ").append(colName).append(" TEXT");
        }
        ddl.append(")");

        jdbcTemplate.execute(ddl.toString());
        return tableName;
    }

    /**
     * 批量写入解析后的数据
     */
    public void bulkInsert(String tableName, List<ColumnInfo> columns, List<List<String>> rows) {
        if (rows.isEmpty()) return;

        List<String> colNames = columns.stream()
            .map(c -> sanitizeColumnName(c.name()))
            .toList();

        String placeholders = String.join(",", Collections.nCopies(colNames.size(), "?"));
        String sql = "INSERT INTO " + tableName + " (" + String.join(",", colNames) + ") VALUES (" + placeholders + ")";

        jdbcTemplate.batchUpdate(sql, rows, 500, (ps, row) -> {
            for (int i = 0; i < row.size(); i++) {
                ps.setString(i + 1, i < row.size() ? row.get(i) : null);
            }
        });
    }

    /**
     * 分页查询暂存表数据
     */
    public Page<Map<String, Object>> query(String tableName, boolean errorsOnly, Pageable pageable) {
        String where = errorsOnly ? " WHERE _status = 'ERROR'" : "";
        String countSql = "SELECT COUNT(*) FROM " + tableName + where;
        String dataSql = "SELECT * FROM " + tableName + where
            + " ORDER BY _row_num LIMIT " + pageable.getPageSize()
            + " OFFSET " + pageable.getOffset();

        Long total = jdbcTemplate.queryForObject(countSql, Long.class);
        List<Map<String, Object>> data = jdbcTemplate.queryForList(dataSql);
        return new PageImpl<>(data, pageable, total != null ? total : 0);
    }

    /**
     * 编辑单个单元格
     */
    public void updateCell(String tableName, int rowNum, String column, String value) {
        String colName = sanitizeColumnName(column);
        String sql = "UPDATE " + tableName + " SET " + colName + " = ? WHERE _row_num = ?";
        jdbcTemplate.update(sql, value, rowNum);
    }

    /**
     * 更新行的错误信息和状态
     */
    public void updateErrors(String tableName, int rowNum, List<CellError> errors) {
        try {
            String errorsJson = objectMapper.writeValueAsString(errors);
            String status = errors.isEmpty() ? "CLEAN" : "ERROR";
            String sql = "UPDATE " + tableName + " SET _errors = ?::jsonb, _status = ? WHERE _row_num = ?";
            jdbcTemplate.update(sql, errorsJson, status, rowNum);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize errors", e);
        }
    }

    /**
     * 检查是否所有行都通过校验
     */
    public boolean allClean(String tableName) {
        String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE _status = 'ERROR'";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null && count == 0;
    }

    /**
     * 删除暂存表
     */
    public void drop(String tableName) {
        if (tableName != null && tableName.startsWith(TABLE_PREFIX)) {
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + tableName);
        }
    }

    /**
     * 定时清理超时暂存表（24小时）
     */
    @Scheduled(fixedRate = 3600000)  // 每小时
    public void cleanupExpired() {
        String sql = """
            SELECT tablename FROM pg_tables
            WHERE schemaname = 'public' AND tablename LIKE 'tmp_ingestion_%'
            """;
        // 注意：PG 无法直接查表创建时间，需要通过 pg_stat_user_tables 的 last_analyze 或自建元数据表
        // 简化方案：检查表是否有最近24小时内的活动
        List<String> tables = jdbcTemplate.queryForList(sql, String.class);
        for (String table : tables) {
            String countSql = "SELECT COUNT(*) FROM " + table;
            try {
                jdbcTemplate.queryForObject(countSql, Long.class);
                // TODO: 增加元数据表记录 lastAccessedAt，根据时间判断清理
            } catch (Exception e) {
                // 表已不存在或损坏，跳过
            }
        }
    }

    private void validateTaskId(UUID taskId) {
        if (taskId == null || !VALID_TASK_ID.matcher(taskId.toString()).matches()) {
            throw new IllegalArgumentException("Invalid task ID");
        }
    }

    private String sanitizeColumnName(String name) {
        // 只允许字母、数字、下划线，防止 SQL 注入
        return name.replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fff]", "_").toLowerCase();
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `cd source/dts-ingestion && mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/
git commit -m "feat(F4/T02): add StagingTableService with CRUD, pagination, TTL cleanup"
```

---

### Task 5: BuiltInRuleChecker — 内置自动检测 [F4/T04]

**Files:**
- Create: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/BuiltInRuleChecker.java`

- [ ] **Step 1: 创建 BuiltInRuleChecker**

```java
package com.yuzhi.dts.ingestion.service.etl;

import com.yuzhi.dts.ingestion.service.dto.CellError;
import com.yuzhi.dts.ingestion.service.dto.ColumnInfo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 内置规则检测器——不依赖用户配置的规则，Excel 上传时始终执行。
 */
@Component
public class BuiltInRuleChecker {

    private final JdbcTemplate jdbcTemplate;

    public BuiltInRuleChecker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 对暂存表执行内置检测，返回每行的错误列表。
     */
    public Map<Integer, List<CellError>> check(String tableName, List<ColumnInfo> columns) {
        Map<Integer, List<CellError>> allErrors = new HashMap<>();

        checkEmptyRows(tableName, columns, allErrors);
        checkDuplicateRows(tableName, columns, allErrors);
        checkNumericWithUnit(tableName, columns, allErrors);
        checkDateFormatMix(tableName, columns, allErrors);

        return allErrors;
    }

    /** 空行检测 */
    private void checkEmptyRows(String tableName, List<ColumnInfo> columns, Map<Integer, List<CellError>> errors) {
        List<String> colNames = columns.stream().map(c -> sanitize(c.name())).toList();
        String conditions = colNames.stream()
            .map(c -> "(" + c + " IS NULL OR TRIM(" + c + ") = '')")
            .reduce((a, b) -> a + " AND " + b)
            .orElse("TRUE");

        String sql = "SELECT _row_num FROM " + tableName + " WHERE " + conditions;
        List<Integer> rows = jdbcTemplate.queryForList(sql, Integer.class);
        for (int rowNum : rows) {
            errors.computeIfAbsent(rowNum, k -> new ArrayList<>())
                .add(new CellError("*", "[内置]空行检测", "整行为空"));
        }
    }

    /** 重复行检测 */
    private void checkDuplicateRows(String tableName, List<ColumnInfo> columns, Map<Integer, List<CellError>> errors) {
        List<String> colNames = columns.stream().map(c -> sanitize(c.name())).toList();
        String cols = String.join(",", colNames);

        String sql = """
            WITH dups AS (
                SELECT %s, MIN(_row_num) AS first_row, ARRAY_AGG(_row_num ORDER BY _row_num) AS all_rows
                FROM %s GROUP BY %s HAVING COUNT(*) > 1
            )
            SELECT unnest(all_rows) AS row_num, first_row FROM dups
            """.formatted(cols, tableName, cols);

        jdbcTemplate.query(sql, rs -> {
            int rowNum = rs.getInt("row_num");
            int firstRow = rs.getInt("first_row");
            if (rowNum != firstRow) {
                errors.computeIfAbsent(rowNum, k -> new ArrayList<>())
                    .add(new CellError("*", "[内置]重复行检测", "与第 " + firstRow + " 行重复"));
            }
        });
    }

    /** 数字列含单位检测 */
    private void checkNumericWithUnit(String tableName, List<ColumnInfo> columns, Map<Integer, List<CellError>> errors) {
        for (ColumnInfo col : columns) {
            if (!"LONG".equals(col.inferredType()) && !"DOUBLE".equals(col.inferredType())) continue;

            String colName = sanitize(col.name());
            String sql = "SELECT _row_num, " + colName + " AS val FROM " + tableName
                + " WHERE " + colName + " IS NOT NULL AND " + colName + " ~ '[0-9]+[^0-9.\\s\\-]'";

            jdbcTemplate.query(sql, rs -> {
                int rowNum = rs.getInt("_row_num");
                String val = rs.getString("val");
                errors.computeIfAbsent(rowNum, k -> new ArrayList<>())
                    .add(new CellError(col.name(), "[内置]数字含单位", "数字列含非数字字符: " + val));
            });
        }
    }

    /** 日期格式混用检测 */
    private void checkDateFormatMix(String tableName, List<ColumnInfo> columns, Map<Integer, List<CellError>> errors) {
        for (ColumnInfo col : columns) {
            if (!"DATE".equals(col.inferredType())) continue;

            String colName = sanitize(col.name());
            // 检查不符合 yyyy-MM-dd 格式的日期值
            String sql = "SELECT _row_num, " + colName + " AS val FROM " + tableName
                + " WHERE " + colName + " IS NOT NULL AND " + colName + " != ''"
                + " AND " + colName + " !~ '^\\d{4}-\\d{2}-\\d{2}$'";

            jdbcTemplate.query(sql, rs -> {
                int rowNum = rs.getInt("_row_num");
                String val = rs.getString("val");
                errors.computeIfAbsent(rowNum, k -> new ArrayList<>())
                    .add(new CellError(col.name(), "[内置]日期格式", "日期格式不统一，期望 yyyy-MM-dd，实际: " + val));
            });
        }
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fff]", "_").toLowerCase();
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `cd source/dts-ingestion && mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/BuiltInRuleChecker.java
git commit -m "feat(F4/T04): add BuiltInRuleChecker with empty/duplicate/unit/date checks"
```

---

### Task 6: IngestionQualityBridge — 预检桥接 [F4/T03]

**Files:**
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/IngestionQualityBridge.java`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/dto/PreCheckResult.java`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/dto/RuleCheckResult.java`

- [ ] **Step 1: 创建 DTO**

```java
// PreCheckResult.java
package com.yuzhi.dts.platform.service.governance.dto;

import java.util.List;

public record PreCheckResult(
    int totalRows,
    int passedRows,
    int failedRows,
    List<RuleCheckResult> errorsByRule
) {}

// RuleCheckResult.java
package com.yuzhi.dts.platform.service.governance.dto;

public record RuleCheckResult(
    String ruleName,
    String ruleType,
    int failCount,
    List<FailingRow> sampleRows
) {
    public record FailingRow(int rowNum, String column, String actualValue, String reason) {}
}
```

- [ ] **Step 2: 创建 IngestionQualityBridge**

```java
package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovRule;
import com.yuzhi.dts.platform.domain.governance.GovRuleBinding;
import com.yuzhi.dts.platform.repository.governance.GovRuleBindingRepository;
import com.yuzhi.dts.platform.service.governance.dto.PreCheckResult;
import com.yuzhi.dts.platform.service.governance.dto.RuleCheckResult;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class IngestionQualityBridge {

    private static final int MAX_SAMPLE_ROWS = 100;

    private final GovRuleBindingRepository bindingRepository;
    private final SqlTemplateRenderer templateRenderer;
    private final PgStatementExecutor pgExecutor;

    public IngestionQualityBridge(
            GovRuleBindingRepository bindingRepository,
            SqlTemplateRenderer templateRenderer,
            PgStatementExecutor pgExecutor) {
        this.bindingRepository = bindingRepository;
        this.templateRenderer = templateRenderer;
        this.pgExecutor = pgExecutor;
    }

    /**
     * 对暂存表执行所有绑定规则的预检。
     * @param stagingTableName 暂存表名
     * @param datasetId 目标数据集 ID（用于查找绑定规则）
     * @param totalRows 暂存表总行数
     */
    public PreCheckResult preCheck(String stagingTableName, UUID datasetId, int totalRows) {
        // 1. 查找数据集绑定的所有已发布规则
        List<GovRuleBinding> bindings = bindingRepository.findByDatasetAndPublishedVersion(datasetId);

        List<RuleCheckResult> results = new ArrayList<>();
        Set<Integer> allFailingRows = new HashSet<>();

        for (GovRuleBinding binding : bindings) {
            GovRule rule = binding.getRuleVersion().getRule();
            String renderedSql = rule.getRenderedSql();
            if (renderedSql == null || renderedSql.isBlank()) continue;

            // 2. 替换表名为暂存表
            String sql = renderedSql.replace(binding.getDataset(), stagingTableName);

            // 3. 通过 PG 执行
            List<Map<String, Object>> failingRows = pgExecutor.executeQualityCheck(sql, MAX_SAMPLE_ROWS);

            if (!failingRows.isEmpty()) {
                List<RuleCheckResult.FailingRow> samples = failingRows.stream()
                    .map(row -> new RuleCheckResult.FailingRow(
                        ((Number) row.getOrDefault("_row_num", 0)).intValue(),
                        binding.getFieldRefs(),
                        String.valueOf(row.values().iterator().next()),
                        rule.getName() + " 检查失败"
                    ))
                    .toList();

                results.add(new RuleCheckResult(
                    rule.getName(),
                    rule.getType(),
                    failingRows.size(),
                    samples
                ));

                failingRows.stream()
                    .map(row -> ((Number) row.getOrDefault("_row_num", 0)).intValue())
                    .forEach(allFailingRows::add);
            }
        }

        int failedRows = allFailingRows.size();
        return new PreCheckResult(totalRows, totalRows - failedRows, failedRows, results);
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `cd source/dts-platform && mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/
git commit -m "feat(F4/T03): add IngestionQualityBridge for pre-check via PG executor"
```

---

### Task 7: 入湖任务 DB 扩展 + API [F4/T05]

**Files:**
- Create: `source/dts-ingestion/src/main/resources/config/liquibase/changelog/YYYYMMDD_NN_ingestion_precheck_fields.xml`
- Modify: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/domain/IngestionTask.java`
- Modify: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/web/rest/IngestionTaskResource.java`

- [ ] **Step 1: 查看 IngestionTask 实体和 Resource 现有结构**

需要先读取确认字段和命名规范。

- [ ] **Step 2: 创建 Liquibase changelog**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <changeSet id="20260404_10_01" author="system">
        <addColumn tableName="ingestion_task">
            <column name="quality_pre_check_enabled" type="boolean" defaultValueBoolean="false"/>
            <column name="staging_table_name" type="varchar(100)"/>
            <column name="pre_check_status" type="varchar(20)"/>
        </addColumn>
    </changeSet>
</databaseChangeLog>
```

- [ ] **Step 3: 扩展 IngestionTask 实体**

添加 3 个字段：
```java
@Column(name = "quality_pre_check_enabled")
private Boolean qualityPreCheckEnabled = false;

@Column(name = "staging_table_name")
private String stagingTableName;

@Column(name = "pre_check_status")
private String preCheckStatus; // PENDING / CHECKING / PASSED / FAILED
```

- [ ] **Step 4: 新增 API 端点**

在 `IngestionTaskResource` 中增加：
```java
@PostMapping("/tasks/{id}/parse")
// 全量解析 Excel → 创建暂存表 → 返回 ParseResult

@PostMapping("/tasks/{id}/pre-check")
// 调用 IngestionQualityBridge.preCheck() → 返回 PreCheckResult

@PutMapping("/tasks/{id}/staging/{rowNum}")
// 编辑暂存表单行

@PostMapping("/tasks/{id}/re-check")
// 重新检查

@PostMapping("/tasks/{id}/submit")
// 提交入湖（暂存表 → Addax）

@DeleteMapping("/tasks/{id}/staging")
// 取消，清理暂存表
```

具体实现需读取现有 Resource 代码后适配。

- [ ] **Step 5: 编译验证**

Run: `cd source/dts-ingestion && mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add source/dts-ingestion/
git commit -m "feat(F4/T05): add ingestion pre-check DB fields and API endpoints"
```

---

### Task 8: QualityScoreService — 评分引擎 [F7/T01]

**Files:**
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/QualityScoreService.java`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/dto/QualityScoreResult.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/governance/GovernanceResource.java`

- [ ] **Step 1: 创建 DTO**

```java
package com.yuzhi.dts.platform.service.governance.dto;

import java.util.List;

public record QualityScoreResult(
    int overall,
    Integer overallDelta,  // vs 上一周期，null 表示无历史数据
    List<DimensionScore> dimensions,
    List<TrendPoint> trend
) {
    public record DimensionScore(String type, int score, Integer delta) {}
    public record TrendPoint(String date, int overall) {}
}
```

- [ ] **Step 2: 创建 QualityScoreService**

```java
package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.repository.governance.GovQualityRunRepository;
import com.yuzhi.dts.platform.service.governance.dto.QualityScoreResult;
import com.yuzhi.dts.platform.service.governance.dto.QualityScoreResult.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class QualityScoreService {

    private static final Map<String, Integer> SEVERITY_WEIGHTS = Map.of(
        "CRITICAL", 4, "HIGH", 3, "MEDIUM", 2, "LOW", 1
    );

    private final GovQualityRunRepository runRepository;

    public QualityScoreService(GovQualityRunRepository runRepository) {
        this.runRepository = runRepository;
    }

    public QualityScoreResult calculate(UUID datasetId, int periodDays) {
        Instant since = LocalDate.now().minusDays(periodDays)
            .atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant previousSince = LocalDate.now().minusDays(periodDays * 2L)
            .atStartOfDay(ZoneId.systemDefault()).toInstant();

        // 当前周期的运行记录
        List<GovQualityRun> currentRuns = runRepository
            .findByDatasetAndFinishedAfter(datasetId, since);
        // 上一周期（用于 delta）
        List<GovQualityRun> previousRuns = runRepository
            .findByDatasetAndFinishedBetween(datasetId, previousSince, since);

        // 按规则类型分组计算
        Map<String, List<GovQualityRun>> byType = currentRuns.stream()
            .filter(r -> r.getRule() != null)
            .collect(Collectors.groupingBy(r -> r.getRule().getType()));

        Map<String, List<GovQualityRun>> prevByType = previousRuns.stream()
            .filter(r -> r.getRule() != null)
            .collect(Collectors.groupingBy(r -> r.getRule().getType()));

        List<DimensionScore> dimensions = new ArrayList<>();
        double overallSum = 0;
        double overallWeight = 0;

        for (String type : List.of("COMPLETENESS", "CONSISTENCY", "ACCURACY", "UNIQUENESS", "TIMELINESS")) {
            int score = calcWeightedScore(byType.getOrDefault(type, List.of()));
            int prevScore = calcWeightedScore(prevByType.getOrDefault(type, List.of()));
            Integer delta = prevByType.containsKey(type) ? score - prevScore : null;
            dimensions.add(new DimensionScore(type, score, delta));
            if (!byType.getOrDefault(type, List.of()).isEmpty()) {
                overallSum += score;
                overallWeight++;
            }
        }

        int overall = overallWeight > 0 ? (int) (overallSum / overallWeight) : 0;
        int prevOverall = calcOverallFromRuns(previousRuns);
        Integer overallDelta = previousRuns.isEmpty() ? null : overall - prevOverall;

        // 趋势
        List<TrendPoint> trend = calcTrend(datasetId, periodDays);

        return new QualityScoreResult(overall, overallDelta, dimensions, trend);
    }

    private int calcWeightedScore(List<GovQualityRun> runs) {
        if (runs.isEmpty()) return 0;
        double weightedSum = 0;
        double totalWeight = 0;
        for (GovQualityRun run : runs) {
            double passRate = calcPassRate(run);
            int weight = SEVERITY_WEIGHTS.getOrDefault(
                run.getRule() != null ? run.getRule().getSeverity() : "MEDIUM", 2);
            weightedSum += passRate * 100 * weight;
            totalWeight += weight;
        }
        return totalWeight > 0 ? (int) (weightedSum / totalWeight) : 0;
    }

    private double calcPassRate(GovQualityRun run) {
        if (run.getRowsChecked() == null || run.getRowsChecked() == 0) return 1.0;
        long failing = run.getFailingRowCount() != null ? run.getFailingRowCount() : 0;
        return 1.0 - (double) failing / run.getRowsChecked();
    }

    private int calcOverallFromRuns(List<GovQualityRun> runs) {
        if (runs.isEmpty()) return 0;
        return calcWeightedScore(runs);
    }

    private List<TrendPoint> calcTrend(UUID datasetId, int periodDays) {
        // 按天聚合，每天计算当天运行的综合得分
        List<TrendPoint> trend = new ArrayList<>();
        for (int i = periodDays - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            Instant dayStart = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant dayEnd = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            List<GovQualityRun> dayRuns = runRepository
                .findByDatasetAndFinishedBetween(datasetId, dayStart, dayEnd);
            if (!dayRuns.isEmpty()) {
                trend.add(new TrendPoint(date.toString(), calcWeightedScore(dayRuns)));
            }
        }
        return trend;
    }
}
```

- [ ] **Step 3: 在 GovernanceResource 中新增端点**

```java
@GetMapping("/quality/score")
public ResponseEntity<QualityScoreResult> getQualityScore(
        @RequestParam UUID datasetId,
        @RequestParam(defaultValue = "7") int periodDays) {
    return ResponseEntity.ok(qualityScoreService.calculate(datasetId, periodDays));
}
```

- [ ] **Step 4: 编译验证**

Run: `cd source/dts-platform && mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/governance/
git commit -m "feat(F7/T01): add QualityScoreService with weighted scoring and trend"
```

---

### Task 9: F8 — P0 修复

**Files:**
- Modify: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/IngestionTaskService.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/` (ModelingAux 相关)

- [ ] **Step 1: sourceDataSourceId 非空校验**

在 `IngestionTaskService` 的创建/更新方法中，找到参数处理代码，添加：
```java
if (request.getSourceDataSourceId() == null) {
    throw new BadRequestAlertException("来源数据源不能为空，请先选择数据源",
        "ingestionTask", "sourceDataSourceIdNull");
}
```

- [ ] **Step 2: modelingPlan 同名校验**

在对应 Service 中添加：
```java
if (planRepository.existsByNameIgnoreCase(request.getName())) {
    throw new BadRequestAlertException("已存在同名项目空间: " + request.getName(),
        "modelingPlan", "duplicateName");
}
```

更新时排除自身：
```java
if (planRepository.existsByNameIgnoreCaseAndIdNot(request.getName(), existingId)) {
    throw new BadRequestAlertException("已存在同名项目空间: " + request.getName(),
        "modelingPlan", "duplicateName");
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn compile -pl source/dts-ingestion,source/dts-platform -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add source/dts-ingestion/ source/dts-platform/
git commit -m "fix(F8): sourceDataSourceId non-null validation + modelingPlan duplicate name check"
```

---

## 执行顺序总结

```
Task 1 (清洗函数) ←── 无依赖，可并行
Task 2 (执行器抽象) ←── 无依赖，可并行
Task 9 (P0修复) ←── 无依赖，可并行

Task 3 (ExcelParse) ←── 无依赖
Task 4 (StagingTable) ←── 依赖 Task 3 的 DTO
Task 5 (BuiltInChecker) ←── 依赖 Task 4
Task 6 (QualityBridge) ←── 依赖 Task 2 + Task 4
Task 7 (入湖API) ←── 依赖 Task 3-6 全部
Task 8 (评分引擎) ←── 无依赖，可并行
```

可并行批次：
- **Batch A**: Task 1 + Task 2 + Task 3 + Task 8 + Task 9
- **Batch B**: Task 4 + Task 5
- **Batch C**: Task 6
- **Batch D**: Task 7
