package com.yuzhi.dts.platform.service.governance;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.sf.jsqlparser.expression.AnalyticExpression;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.CastExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExtractExpression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.JsonAggregateFunction;
import net.sf.jsqlparser.expression.JsonFunction;
import net.sf.jsqlparser.expression.MySQLGroupConcat;
import net.sf.jsqlparser.expression.NextValExpression;
import net.sf.jsqlparser.expression.TimeKeyExpression;
import net.sf.jsqlparser.expression.TranscodingFunction;
import net.sf.jsqlparser.expression.VariableAssignment;
import net.sf.jsqlparser.expression.XMLSerializeExpr;
import net.sf.jsqlparser.parser.CCJSqlParser;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.piped.FromQuery;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.LateralSubSelect;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.ParenthesedFromItem;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.TableFunction;
import net.sf.jsqlparser.statement.select.TableStatement;
import net.sf.jsqlparser.statement.select.Values;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.util.StringUtils;

/**
 * Fail-closed SQL scope validation for quality rules.
 *
 * <p>The lineage extractor intentionally accepts partial SQL and is therefore not a security
 * boundary. Quality execution instead requires one parseable read-only query, rejects dynamic
 * table sources, and verifies every physical table discovered by the AST against the bound data
 * asset.</p>
 */
public final class QualitySqlScopeValidator {

    private static final int PARSE_TIMEOUT_MILLIS = 2_000;
    private static final Set<String> SAFE_FUNCTIONS = Set.of(
        "btrim",
        "count",
        "ltrim",
        "nullif",
        "round",
        "rtrim",
        "sum",
        "trim"
    );
    private static final Set<String> SAFE_CAST_TYPES = Set.of("numeric", "text", "string");

    private QualitySqlScopeValidator() {}

    /**
     * Outcome of one scope check. A denial always names the construct that failed so operators do
     * not have to read this class to understand why their statement was rejected.
     */
    record ScopeCheck(boolean allowed, String reasonCode, String detail) {
        static ScopeCheck ok() {
            return new ScopeCheck(true, null, null);
        }

        static ScopeCheck denied(String reasonCode, String detail) {
            return new ScopeCheck(false, reasonCode, detail);
        }

        String message() {
            if (allowed) {
                return "";
            }
            return switch (reasonCode) {
                case "UNSUPPORTED_SOURCE_TYPE" -> "检测资产的数据源类型不支持质量检测 SQL 解析：" + detail;
                case "INVALID_BOUND_TABLE" -> "绑定资产的库表名不是规范标识符：" + detail;
                case "UNPARSEABLE_SQL" -> "检测 SQL 无法解析为单条只读查询：" + detail;
                case "UNSUPPORTED_FUNCTION" -> "函数 " + detail + " 不在质量检测允许清单内，当前允许：" + allowedFunctions();
                case "UNSUPPORTED_CAST_TYPE" -> "CAST 目标类型 " + detail + " 不在允许清单内，当前允许：" + allowedCastTypes();
                case "UNSUPPORTED_SYNTAX" -> "检测 SQL 使用了不受支持的语法：" + detail;
                case "NO_TABLE_REFERENCE" -> "检测 SQL 未引用任何物理表";
                case "OUT_OF_SCOPE_TABLE" -> "检测 SQL 引用了绑定资产以外的表：" + detail;
                default -> "检测 SQL 未通过绑定资产校验：" + reasonCode;
            };
        }
    }

    static String allowedFunctions() {
        return String.join("、", SAFE_FUNCTIONS.stream().sorted().toList());
    }

    private static String allowedCastTypes() {
        return String.join("、", SAFE_CAST_TYPES.stream().sorted().toList());
    }

    static boolean referencesOnlyBoundTable(String sql, String boundSchema, String boundTable, String sourceType) {
        return checkScope(sql, boundSchema, boundTable, sourceType).allowed();
    }

    static ScopeCheck checkScope(String sql, String boundSchema, String boundTable, String sourceType) {
        if (!StringUtils.hasText(sql)) {
            return ScopeCheck.denied("UNPARSEABLE_SQL", "语句为空");
        }
        if (!StringUtils.hasText(boundSchema) || !StringUtils.hasText(boundTable)) {
            return ScopeCheck.denied("INVALID_BOUND_TABLE", "绑定资产缺少库名或表名");
        }
        IdentifierDialect identifierDialect = IdentifierDialect.forSource(sourceType);
        if (identifierDialect == null) {
            return ScopeCheck.denied("UNSUPPORTED_SOURCE_TYPE", String.valueOf(sourceType));
        }
        if (!isCanonicalBoundIdentifier(boundTable, identifierDialect)) {
            return ScopeCheck.denied("INVALID_BOUND_TABLE", boundTable);
        }
        if (!isCanonicalBoundIdentifier(boundSchema, identifierDialect)) {
            return ScopeCheck.denied("INVALID_BOUND_TABLE", boundSchema);
        }
        try {
            Statements statements = CCJSqlParserUtil.parseStatements(sql, parser ->
                configureParser(parser, identifierDialect)
            );
            if (statements.size() != 1) {
                return ScopeCheck.denied("UNPARSEABLE_SQL", "只允许一条语句，实际 " + statements.size() + " 条");
            }
            Statement statement = statements.get(0);
            if (!(statement instanceof Select select)) {
                return ScopeCheck.denied("UNPARSEABLE_SQL", "只允许 SELECT 查询");
            }

            SecurityTablesFinder finder = new SecurityTablesFinder(identifierDialect);
            Set<String> references = finder.getTables((Statement) select);
            if (finder.rejected()) {
                return ScopeCheck.denied(finder.reasonCode(), finder.detail());
            }
            if (references.isEmpty()) {
                return ScopeCheck.denied("NO_TABLE_REFERENCE", null);
            }
            String outOfScope = references
                .stream()
                .filter(reference -> !matchesBoundTable(reference, boundSchema, boundTable, identifierDialect))
                .findFirst()
                .orElse(null);
            if (outOfScope != null) {
                return ScopeCheck.denied("OUT_OF_SCOPE_TABLE", outOfScope);
            }
            return ScopeCheck.ok();
        } catch (Exception parseFailure) {
            // Parser errors, timeouts, and unsupported dialect constructs are denied by design.
            return ScopeCheck.denied("UNPARSEABLE_SQL", "解析失败");
        }
    }

    /** Physical read set for modeling SQL. Unsupported syntax is rejected, never partially accepted. */
    public static Set<String> modelingReadTables(String sql) {
        try {
            Statements statements = CCJSqlParserUtil.parseStatements(sql, parser -> configureParser(parser, IdentifierDialect.HIVE));
            if (statements.size() != 1 || !(statements.get(0) instanceof Select)) throw new IllegalArgumentException();
            SecurityTablesFinder finder = new SecurityTablesFinder(IdentifierDialect.HIVE, false);
            Set<String> tables = finder.getTables(statements.get(0));
            if (finder.rejected()) throw new IllegalArgumentException();
            return Set.copyOf(tables);
        } catch (Exception failure) {
            throw new IllegalArgumentException("SQL_READ_SET_UNVERIFIABLE");
        }
    }

    private static void configureParser(CCJSqlParser parser, IdentifierDialect identifierDialect) {
        parser
            .withTimeOut(PARSE_TIMEOUT_MILLIS)
            .withAllowComplexParsing(true)
            .withBackslashEscapeCharacter(identifierDialect == IdentifierDialect.HIVE);
    }

    private static boolean matchesBoundTable(
        String reference,
        String boundSchema,
        String boundTable,
        IdentifierDialect identifierDialect
    ) {
        try {
            CCJSqlParser parser = CCJSqlParserUtil.newParser(reference);
            configureParser(parser, identifierDialect);
            Table table = parser.Table();
            List<String> nameParts = table.getNameParts();
            if (
                nameParts == null ||
                nameParts.isEmpty() ||
                nameParts.size() > 2 ||
                nameParts.stream().anyMatch(part -> part != null && part.contains("@")) ||
                StringUtils.hasText(table.getCatalogName()) ||
                StringUtils.hasText(table.getDatabaseName())
            ) {
                return false;
            }
            if (nameParts.size() != 2 || !matchesIdentifier(table.getName(), boundTable, identifierDialect)) {
                return false;
            }
            return matchesIdentifier(table.getSchemaName(), boundSchema, identifierDialect);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean matchesIdentifier(String reference, String bound, IdentifierDialect identifierDialect) {
        if (!StringUtils.hasText(reference) || !StringUtils.hasText(bound)) {
            return false;
        }
        String raw = reference.trim();
        if (raw.startsWith("\"") || raw.endsWith("\"")) {
            return raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"") &&
                raw.substring(1, raw.length() - 1).replace("\"\"", "\"").equals(bound);
        }
        if (raw.startsWith("`") || raw.endsWith("`")) {
            return identifierDialect.allowsBackticks() && raw.length() >= 2 && raw.startsWith("`") && raw.endsWith("`") &&
                raw.substring(1, raw.length() - 1).replace("``", "`").equals(bound);
        }
        if (!raw.matches("^[A-Za-z_][A-Za-z0-9_$]*$")) {
            return false;
        }
        return identifierDialect.fold(raw).equals(bound);
    }

    private static boolean isCanonicalBoundIdentifier(String identifier, IdentifierDialect identifierDialect) {
        String trimmed = StringUtils.hasText(identifier) ? identifier.trim() : "";
        return trimmed.matches("^[A-Za-z_][A-Za-z0-9_$]*$") && identifierDialect.fold(trimmed).equals(trimmed);
    }

    private enum IdentifierDialect {
        POSTGRESQL(false),
        HIVE(true);

        private final boolean allowsBackticks;

        IdentifierDialect(boolean allowsBackticks) {
            this.allowsBackticks = allowsBackticks;
        }

        private String fold(String identifier) {
            return identifier.toLowerCase(Locale.ROOT);
        }

        private boolean allowsBackticks() {
            return allowsBackticks;
        }

        private static IdentifierDialect forSource(String sourceType) {
            String normalized = StringUtils.hasText(sourceType) ? sourceType.trim().toUpperCase(Locale.ROOT) : "";
            return switch (normalized) {
                case "POSTGRES", "POSTGRESQL" -> POSTGRESQL;
                case "HIVE", "INCEPTOR" -> HIVE;
                default -> null;
            };
        }
    }

    private static boolean hasUnsafeSelectOptions(Select select) {
        return (
            select.getForMode() != null ||
            select.getForUpdateTable() != null ||
            select.getForClause() != null ||
            select.getWait() != null ||
            select.isSkipLocked()
        );
    }

    private static final class SecurityTablesFinder extends TablesNamesFinder<Void> {

        private final IdentifierDialect identifierDialect;
        private final boolean qualityPolicy;
        private static final Set<String> MODEL_FUNCTIONS = Set.of("avg", "min", "max", "coalesce", "lower", "upper", "concat", "substring", "substr", "length", "abs", "ceil", "floor", "date_trunc", "date_format", "to_date", "year", "month", "day", "greatest", "least", "to_char", "generate_series");
        private String reasonCode;
        private String detail;

        private SecurityTablesFinder(IdentifierDialect identifierDialect) {
            this(identifierDialect, true);
        }

        private SecurityTablesFinder(IdentifierDialect identifierDialect, boolean qualityPolicy) {
            this.identifierDialect = identifierDialect;
            this.qualityPolicy = qualityPolicy;
        }

        boolean rejected() {
            return reasonCode != null;
        }

        String reasonCode() {
            return reasonCode;
        }

        String detail() {
            return detail;
        }

        /** First rejection wins so the reported reason is the one nearest the author's intent. */
        private void reject(String reasonCode, String detail) {
            if (this.reasonCode == null) {
                this.reasonCode = reasonCode;
                this.detail = detail;
            }
        }

        private void rejectSyntax(String detail) {
            reject("UNSUPPORTED_SYNTAX", detail);
        }

        @Override
        public <S> Void visit(PlainSelect select, S context) {
            inspectFromItem(select);
            inspectFromItem(select.getFromItem());
            if (select.getJoins() != null) {
                select.getJoins().forEach(join -> {
                    inspectFromItem(join.getRightItem());
                    if (join.isApply() || join.isWindowJoin() || join.getJoinWindow() != null || join.getJoinHint() != null) {
                        rejectSyntax("APPLY/窗口 JOIN 或 JOIN 提示");
                    }
                });
            }
            if (hasUnsafeSelectOptions(select)) {
                rejectSyntax("FOR UPDATE/锁定子句");
            }
            if (select.getIntoTempTable() != null || (select.getIntoTables() != null && !select.getIntoTables().isEmpty())) {
                rejectSyntax("SELECT INTO");
            }
            Void result = super.visit(select, context);
            inspectSelectTail(select, context);
            inspectPlainSelectExtensions(select, context);
            return result;
        }

        @Override
        public <S> Void visit(ParenthesedSelect select, S context) {
            inspectFromItem(select);
            if (hasUnsafeSelectOptions(select)) {
                rejectSyntax("FOR UPDATE/锁定子句");
            }
            Void result = super.visit(select, context);
            inspectSelectTail(select, context);
            return result;
        }

        @Override
        public <S> Void visit(SetOperationList select, S context) {
            inspectFromItem(select);
            if (hasUnsafeSelectOptions(select)) {
                rejectSyntax("FOR UPDATE/锁定子句");
            }
            Void result = super.visit(select, context);
            inspectSelectTail(select, context);
            return result;
        }

        @Override
        public <S> Void visit(TableStatement select, S context) {
            inspectFromItem(select);
            if (hasUnsafeSelectOptions(select)) {
                rejectSyntax("FOR UPDATE/锁定子句");
            }
            Void result = super.visit(select, context);
            inspectSelectTail(select, context);
            return result;
        }

        @Override
        public <S> Void visit(Values select, S context) {
            inspectFromItem(select);
            if (hasUnsafeSelectOptions(select)) {
                rejectSyntax("FOR UPDATE/锁定子句");
            }
            Void result = super.visit(select, context);
            inspectSelectTail(select, context);
            return result;
        }

        private <S> void inspectPlainSelectExtensions(PlainSelect select, S context) {
            if (select.getDistinct() != null && select.getDistinct().getOnSelectItems() != null) {
                select.getDistinct().getOnSelectItems().forEach(item -> item.accept(this, context));
            }
            if (select.getGroupBy() != null) {
                inspectExpression(select.getGroupBy().getGroupByExpressionList(), context);
                if (select.getGroupBy().getGroupingSets() != null) {
                    select.getGroupBy().getGroupingSets().forEach(expressions -> inspectExpression(expressions, context));
                }
            }
            inspectExpression(select.getQualify(), context);
            if (select.getTop() != null) {
                inspectExpression(select.getTop().getExpression(), context);
            }
            if (
                (select.getLateralViews() != null && !select.getLateralViews().isEmpty()) ||
                (select.getWindowDefinitions() != null && !select.getWindowDefinitions().isEmpty()) ||
                select.getPreferringClause() != null ||
                select.getKsqlWindow() != null
            ) {
                rejectSyntax("LATERAL VIEW/窗口定义/PREFERRING");
            }
        }

        private <S> void inspectSelectTail(Select select, S context) {
            if (select.getOrderByElements() != null) {
                select.getOrderByElements().forEach(order -> inspectExpression(order.getExpression(), context));
            }
            inspectLimit(select.getLimit(), context);
            inspectLimit(select.getLimitBy(), context);
            if (select.getOffset() != null) {
                inspectExpression(select.getOffset().getOffset(), context);
            }
            if (select.getFetch() != null) {
                inspectExpression(select.getFetch().getExpression(), context);
            }
            if (select.getPivot() != null || select.getUnPivot() != null) {
                rejectSyntax("PIVOT/UNPIVOT");
            }
        }

        private <S> void inspectLimit(net.sf.jsqlparser.statement.select.Limit limit, S context) {
            if (limit == null) {
                return;
            }
            inspectExpression(limit.getOffset(), context);
            inspectExpression(limit.getRowCount(), context);
            inspectExpression(limit.getByExpressions(), context);
        }

        private <S> void inspectExpression(Expression expression, S context) {
            if (expression != null) {
                expression.accept(this, context);
            }
        }

        private void inspectFromItem(FromItem fromItem) {
            if (
                fromItem != null &&
                (fromItem.getPivot() != null || fromItem.getUnPivot() != null || fromItem.getSampleClause() != null)
            ) {
                rejectSyntax("PIVOT/UNPIVOT/TABLESAMPLE");
            }
        }

        @Override
        public <S> Void visit(Table table, S context) {
            inspectFromItem(table);
            if (table.getIndexHint() != null || table.getSqlServerHints() != null) {
                rejectSyntax("表级索引提示");
            }
            return super.visit(table, context);
        }

        @Override
        public <S> Void visit(ParenthesedFromItem fromItem, S context) {
            inspectFromItem(fromItem);
            return super.visit(fromItem, context);
        }

        @Override
        public <S> Void visit(LateralSubSelect select, S context) {
            rejectSyntax("LATERAL 子查询");
            return null;
        }

        @Override
        public <S> Void visit(TableFunction tableFunction, S context) {
            if (qualityPolicy || !"generate_series".equalsIgnoreCase(tableFunction.getFunction().getName()) ||
                StringUtils.hasText(tableFunction.getPrefix()) || StringUtils.hasText(tableFunction.getWithClause())) {
                rejectSyntax("表函数"); return null;
            }
            inspectFromItem(tableFunction);
            return visit(tableFunction.getFunction(), context);
        }

        @Override
        public <S> Void visit(FromQuery fromQuery, S context) {
            rejectSyntax("FROM 管道查询");
            return null;
        }

        @Override
        public <S> Void visit(Function function, S context) {
            List<String> nameParts = function.getMultipartName();
            String name = nameParts != null && nameParts.size() == 1 ? nameParts.get(0) : null;
            if (!StringUtils.hasText(name) || !(SAFE_FUNCTIONS.contains(name.toLowerCase(Locale.ROOT)) || (!qualityPolicy && MODEL_FUNCTIONS.contains(name.toLowerCase(Locale.ROOT))))) {
                reject(
                    "UNSUPPORTED_FUNCTION",
                    StringUtils.hasText(name) ? name.toLowerCase(Locale.ROOT) : String.valueOf(function.getName())
                );
            } else if (
                // The name is allowed, but these clauses can smuggle subqueries into an allowed call.
                function.getNamedParameters() != null ||
                function.getAttribute() != null ||
                function.getHavingClause() != null ||
                function.getKeep() != null ||
                function.getLimit() != null ||
                (function.getOrderByElements() != null && !function.getOrderByElements().isEmpty())
            ) {
                rejectSyntax("函数 " + name.toLowerCase(Locale.ROOT) + " 携带不受支持的子句（ORDER BY/LIMIT/HAVING/KEEP/具名参数）");
            }
            return super.visit(function, context);
        }

        @Override
        public <S> Void visit(SelectItem<?> selectItem, S context) {
            Alias alias = selectItem.getAlias();
            String aliasName = alias != null ? alias.getName() : null;
            if (
                (StringUtils.hasText(aliasName) && !isSafeSelectItemAlias(aliasName)) ||
                (alias != null && alias.getAliasColumns() != null && !alias.getAliasColumns().isEmpty())
            ) {
                // PostgreSQL type literals such as schema.custom_type 'value' are parsed by
                // JSqlParser as a qualified Column plus a single-quoted alias. Reject that
                // ambiguous shape so it cannot bypass the CAST target-type allowlist.
                rejectSyntax("不受支持的列别名：" + aliasName);
            }
            inspectExpression(selectItem.getExpression(), context);
            return null;
        }

        private boolean isSafeSelectItemAlias(String aliasName) {
            String raw = aliasName.trim();
            if (raw.matches("^[A-Za-z_][A-Za-z0-9_$]*$")) {
                return true;
            }
            if (isSafeDelimitedAlias(raw, '"')) {
                return true;
            }
            return identifierDialect == IdentifierDialect.HIVE && isSafeDelimitedAlias(raw, '`');
        }

        private boolean isSafeDelimitedAlias(String raw, char delimiter) {
            if (raw.length() < 2 || raw.charAt(0) != delimiter || raw.charAt(raw.length() - 1) != delimiter) {
                return false;
            }
            String doubledDelimiter = String.valueOf(delimiter) + delimiter;
            String decoded = raw.substring(1, raw.length() - 1).replace(doubledDelimiter, String.valueOf(delimiter));
            return decoded.matches("^[A-Za-z_][A-Za-z0-9_$]*$");
        }

        @Override
        public <S> Void visit(CastExpression expression, S context) {
            var dataType = expression.getColDataType();
            String typeName = dataType != null ? dataType.getDataType() : null;
            if (
                !StringUtils.hasText(typeName) ||
                !typeName.matches("^[A-Za-z][A-Za-z0-9_]*$") ||
                !(SAFE_CAST_TYPES.contains(typeName.toLowerCase(Locale.ROOT)) || (!qualityPolicy && Set.of("integer", "int", "bigint", "double", "float", "boolean", "date", "timestamp", "varchar", "decimal").contains(typeName.toLowerCase(Locale.ROOT)))) ||
                StringUtils.hasText(dataType.getCharacterSet()) ||
                (dataType.getArgumentsStringList() != null && !dataType.getArgumentsStringList().isEmpty()) ||
                (dataType.getArrayData() != null && !dataType.getArrayData().isEmpty()) ||
                (expression.getColumnDefinitions() != null && !expression.getColumnDefinitions().isEmpty()) ||
                StringUtils.hasText(expression.getFormat())
            ) {
                reject("UNSUPPORTED_CAST_TYPE", StringUtils.hasText(typeName) ? typeName : "未知");
            }
            return super.visit(expression, context);
        }

        @Override
        public <S> Void visit(NextValExpression expression, S context) {
            rejectSyntax("NEXTVAL 序列表达式");
            return null;
        }

        @Override
        public <S> Void visit(VariableAssignment expression, S context) {
            rejectSyntax("变量赋值");
            return null;
        }

        @Override
        public <S> Void visit(TranscodingFunction expression, S context) {
            rejectSyntax("转码函数");
            return null;
        }

        @Override
        public <S> Void visit(AnalyticExpression expression, S context) {
            if (qualityPolicy || !"row_number".equalsIgnoreCase(expression.getName()) ||
                expression.getExpression() != null || expression.getOffset() != null || expression.getDefaultValue() != null ||
                expression.getKeep() != null || expression.getHavingClause() != null || expression.getLimit() != null ||
                expression.getFilterExpression() != null || expression.getWindowElement() != null ||
                StringUtils.hasText(expression.getWindowName()) || (expression.getFuncOrderBy() != null && !expression.getFuncOrderBy().isEmpty())) {
                rejectSyntax("窗口/分析函数"); return null;
            }
            inspectExpression(expression.getPartitionExpressionList(), context);
            if (expression.getOrderByElements() != null) expression.getOrderByElements().forEach(order -> inspectExpression(order.getExpression(), context));
            return null;
        }

        @Override
        public <S> Void visit(ExtractExpression expression, S context) {
            if (qualityPolicy) { rejectSyntax("EXTRACT 表达式"); return null; }
            inspectExpression(expression.getExpression(), context);
            return null;
        }

        @Override
        public <S> Void visit(MySQLGroupConcat expression, S context) {
            rejectSyntax("GROUP_CONCAT");
            return null;
        }

        @Override
        public <S> Void visit(TimeKeyExpression expression, S context) {
            rejectSyntax("时间关键字表达式");
            return null;
        }

        @Override
        public <S> Void visit(XMLSerializeExpr expression, S context) {
            rejectSyntax("XMLSERIALIZE");
            return null;
        }

        @Override
        public <S> Void visit(JsonAggregateFunction expression, S context) {
            rejectSyntax("JSON 聚合函数");
            return null;
        }

        @Override
        public <S> Void visit(JsonFunction expression, S context) {
            rejectSyntax("JSON 函数");
            return null;
        }
    }
}
