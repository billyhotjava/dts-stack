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
final class QualitySqlScopeValidator {

    private static final int PARSE_TIMEOUT_MILLIS = 2_000;
    private static final Set<String> SAFE_FUNCTIONS = Set.of("count", "round", "nullif");
    private static final Set<String> SAFE_CAST_TYPES = Set.of("numeric", "text");

    private QualitySqlScopeValidator() {}

    static boolean referencesOnlyBoundTable(String sql, String boundSchema, String boundTable, String sourceType) {
        if (!StringUtils.hasText(sql) || !StringUtils.hasText(boundSchema) || !StringUtils.hasText(boundTable)) {
            return false;
        }
        IdentifierDialect identifierDialect = IdentifierDialect.forSource(sourceType);
        if (identifierDialect == null || !isCanonicalBoundIdentifier(boundTable, identifierDialect)) {
            return false;
        }
        if (!isCanonicalBoundIdentifier(boundSchema, identifierDialect)) {
            return false;
        }
        try {
            Statements statements = CCJSqlParserUtil.parseStatements(sql, parser ->
                configureParser(parser, identifierDialect)
            );
            if (statements.size() != 1) {
                return false;
            }
            Statement statement = statements.get(0);
            if (!(statement instanceof Select select)) {
                return false;
            }

            SecurityTablesFinder finder = new SecurityTablesFinder(identifierDialect);
            Set<String> references = finder.getTables((Statement) select);
            if (finder.unsafe() || references.isEmpty()) {
                return false;
            }
            return references
                .stream()
                .allMatch(reference -> matchesBoundTable(reference, boundSchema, boundTable, identifierDialect));
        } catch (Exception ignored) {
            // Parser errors, timeouts, and unsupported dialect constructs are denied by design.
            return false;
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
        private boolean unsafe;

        private SecurityTablesFinder(IdentifierDialect identifierDialect) {
            this.identifierDialect = identifierDialect;
        }

        boolean unsafe() {
            return unsafe;
        }

        @Override
        public <S> Void visit(PlainSelect select, S context) {
            inspectFromItem(select);
            inspectFromItem(select.getFromItem());
            if (select.getJoins() != null) {
                select.getJoins().forEach(join -> {
                    inspectFromItem(join.getRightItem());
                    if (join.isApply() || join.isWindowJoin() || join.getJoinWindow() != null || join.getJoinHint() != null) {
                        unsafe = true;
                    }
                });
            }
            if (
                hasUnsafeSelectOptions(select) ||
                select.getIntoTempTable() != null ||
                (select.getIntoTables() != null && !select.getIntoTables().isEmpty())
            ) {
                unsafe = true;
            }
            Void result = super.visit(select, context);
            inspectSelectTail(select, context);
            inspectPlainSelectExtensions(select, context);
            return result;
        }

        @Override
        public <S> Void visit(ParenthesedSelect select, S context) {
            inspectFromItem(select);
            unsafe |= hasUnsafeSelectOptions(select);
            Void result = super.visit(select, context);
            inspectSelectTail(select, context);
            return result;
        }

        @Override
        public <S> Void visit(SetOperationList select, S context) {
            inspectFromItem(select);
            unsafe |= hasUnsafeSelectOptions(select);
            Void result = super.visit(select, context);
            inspectSelectTail(select, context);
            return result;
        }

        @Override
        public <S> Void visit(TableStatement select, S context) {
            inspectFromItem(select);
            unsafe |= hasUnsafeSelectOptions(select);
            Void result = super.visit(select, context);
            inspectSelectTail(select, context);
            return result;
        }

        @Override
        public <S> Void visit(Values select, S context) {
            inspectFromItem(select);
            unsafe |= hasUnsafeSelectOptions(select);
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
                unsafe = true;
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
                unsafe = true;
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
                unsafe = true;
            }
        }

        @Override
        public <S> Void visit(Table table, S context) {
            inspectFromItem(table);
            if (table.getIndexHint() != null || table.getSqlServerHints() != null) {
                unsafe = true;
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
            unsafe = true;
            return null;
        }

        @Override
        public <S> Void visit(TableFunction tableFunction, S context) {
            unsafe = true;
            return null;
        }

        @Override
        public <S> Void visit(FromQuery fromQuery, S context) {
            unsafe = true;
            return null;
        }

        @Override
        public <S> Void visit(Function function, S context) {
            List<String> nameParts = function.getMultipartName();
            String name = nameParts != null && nameParts.size() == 1 ? nameParts.get(0) : null;
            if (
                !StringUtils.hasText(name) ||
                !SAFE_FUNCTIONS.contains(name.toLowerCase(Locale.ROOT)) ||
                function.getNamedParameters() != null ||
                function.getAttribute() != null ||
                function.getHavingClause() != null ||
                function.getKeep() != null ||
                function.getLimit() != null ||
                (function.getOrderByElements() != null && !function.getOrderByElements().isEmpty())
            ) {
                unsafe = true;
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
                unsafe = true;
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
                !SAFE_CAST_TYPES.contains(typeName.toLowerCase(Locale.ROOT)) ||
                StringUtils.hasText(dataType.getCharacterSet()) ||
                (dataType.getArgumentsStringList() != null && !dataType.getArgumentsStringList().isEmpty()) ||
                (dataType.getArrayData() != null && !dataType.getArrayData().isEmpty()) ||
                (expression.getColumnDefinitions() != null && !expression.getColumnDefinitions().isEmpty()) ||
                StringUtils.hasText(expression.getFormat())
            ) {
                unsafe = true;
            }
            return super.visit(expression, context);
        }

        @Override
        public <S> Void visit(NextValExpression expression, S context) {
            unsafe = true;
            return null;
        }

        @Override
        public <S> Void visit(VariableAssignment expression, S context) {
            unsafe = true;
            return null;
        }

        @Override
        public <S> Void visit(TranscodingFunction expression, S context) {
            unsafe = true;
            return null;
        }

        @Override
        public <S> Void visit(AnalyticExpression expression, S context) {
            unsafe = true;
            return null;
        }

        @Override
        public <S> Void visit(ExtractExpression expression, S context) {
            unsafe = true;
            return null;
        }

        @Override
        public <S> Void visit(MySQLGroupConcat expression, S context) {
            unsafe = true;
            return null;
        }

        @Override
        public <S> Void visit(TimeKeyExpression expression, S context) {
            unsafe = true;
            return null;
        }

        @Override
        public <S> Void visit(XMLSerializeExpr expression, S context) {
            unsafe = true;
            return null;
        }

        @Override
        public <S> Void visit(JsonAggregateFunction expression, S context) {
            unsafe = true;
            return null;
        }

        @Override
        public <S> Void visit(JsonFunction expression, S context) {
            unsafe = true;
            return null;
        }
    }
}
