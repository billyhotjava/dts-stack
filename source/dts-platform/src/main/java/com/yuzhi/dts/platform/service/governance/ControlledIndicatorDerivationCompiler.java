package com.yuzhi.dts.platform.service.governance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Compiles the deliberately small governance-indicator derivation DSL.
 *
 * <p>The grammar accepts metric tokens, numeric literals, arithmetic operators,
 * parentheses, and a fixed set of scalar functions. It does not accept SQL
 * identifiers, string literals, comments, statements, or arbitrary functions.
 */
@Component
public class ControlledIndicatorDerivationCompiler {

    private static final int MAX_EXPRESSION_LENGTH = 4096;
    private static final int MAX_TOKEN_COUNT = 256;
    private static final int MAX_NESTING_DEPTH = 32;
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern ALIAS_PATTERN = Pattern.compile(
        "(?:\"[A-Za-z_][A-Za-z0-9_]*\"|(?:[A-Za-z_][A-Za-z0-9_]*\\.)?[A-Za-z_][A-Za-z0-9_]*)"
    );
    private static final Pattern METRIC_TOKEN_PATTERN = Pattern.compile(
        "\\{\\{\\s*metric\\s*:\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*}}",
        Pattern.CASE_INSENSITIVE
    );
    private static final Set<String> ALLOWED_FUNCTIONS = Set.of("nullif", "coalesce", "round", "abs");

    public String compile(String expression, List<String> declaredMetricCodes) {
        if (declaredMetricCodes == null) {
            throw new IllegalArgumentException("派生指标依赖不能为空");
        }
        Map<String, String> aliases = new LinkedHashMap<>();
        for (String code : declaredMetricCodes) {
            String normalized = requireCode(code);
            aliases.put(normalized, '"' + code.trim() + '"');
        }
        return compile(expression, aliases);
    }

    public String compile(String expression, Map<String, String> metricAliases) {
        if (!StringUtils.hasText(expression)) {
            throw new IllegalArgumentException("派生表达式不能为空");
        }
        if (expression.length() > MAX_EXPRESSION_LENGTH) {
            throw new IllegalArgumentException("派生表达式长度超过限制");
        }
        Map<String, String> normalizedAliases = normalizeAliases(metricAliases);
        return new Parser(expression.trim(), normalizedAliases).parse();
    }

    public record CompiledFormula(String expression, String zeroDenominator) {}

    public CompiledFormula compileWithDiagnostics(String expression, Map<String, String> metricAliases) {
        // Run the same bounded grammar and collect denominator checks from its parsed operands.
        compile(expression, metricAliases);
        Parser parser = new Parser(expression.trim(), normalizeAliases(metricAliases));
        String sql = parser.parse();
        return new CompiledFormula(sql, parser.denominators.isEmpty() ? "false" : "(" + String.join(" OR ", parser.denominators) + ")");
    }

    public List<String> referencedMetricCodes(String expression) {
        if (!StringUtils.hasText(expression)) {
            return List.of();
        }
        Matcher matcher = METRIC_TOKEN_PATTERN.matcher(expression);
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        while (matcher.find()) {
            codes.add(matcher.group(1));
        }
        return List.copyOf(codes);
    }

    private Map<String, String> normalizeAliases(Map<String, String> aliases) {
        if (aliases == null) {
            throw new IllegalArgumentException("派生指标依赖不能为空");
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            String code = requireCode(entry.getKey());
            String alias = entry.getValue() == null ? "" : entry.getValue().trim();
            if (!ALIAS_PATTERN.matcher(alias).matches()) {
                throw new IllegalArgumentException("非法的生成器字段别名: " + alias);
            }
            normalized.put(code, alias);
        }
        return normalized;
    }

    private String requireCode(String code) {
        String value = code == null ? "" : code.trim();
        if (!CODE_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("非法的指标编码: " + value);
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private static final class Parser {

        private final String source;
        private final Map<String, String> aliases;
        private int position;
        private int tokenCount;
        private int nestingDepth;
        private final List<String> denominators = new ArrayList<>();

        private Parser(String source, Map<String, String> aliases) {
            this.source = source;
            this.aliases = aliases;
        }

        private String parse() {
            String compiled = parseAdditive();
            skipWhitespace();
            if (position != source.length()) {
                throw invalid("存在不受支持的字符或 SQL 片段");
            }
            return compiled;
        }

        private String parseAdditive() {
            String value = parseMultiplicative();
            while (true) {
                skipWhitespace();
                if (consume('+')) {
                    recordToken();
                    value = value + " + " + parseMultiplicative();
                } else if (consume('-')) {
                    recordToken();
                    value = value + " - " + parseMultiplicative();
                } else {
                    return value;
                }
            }
        }

        private String parseMultiplicative() {
            String value = parseUnary();
            while (true) {
                skipWhitespace();
                if (consume('*')) {
                    recordToken();
                    value = value + " * " + parseUnary();
                } else if (consume('/')) {
                    recordToken();
                    String denominator = parseUnary();
                    denominators.add("(" + denominator + ") = 0");
                    value = value + " / NULLIF((" + denominator + "), 0)";
                } else {
                    return value;
                }
            }
        }

        private String parseUnary() {
            skipWhitespace();
            if (consume('+')) {
                recordToken();
                return "+" + parseUnary();
            }
            if (consume('-')) {
                recordToken();
                return "-" + parseUnary();
            }
            return parsePrimary();
        }

        private String parsePrimary() {
            skipWhitespace();
            if (startsWith("{{")) {
                return parseMetricToken();
            }
            if (consume('(')) {
                enterNesting();
                try {
                    String nested = parseAdditive();
                    skipWhitespace();
                    expect(')');
                    return "(" + nested + ")";
                } finally {
                    exitNesting();
                }
            }
            if (position < source.length() && (Character.isDigit(source.charAt(position)) || source.charAt(position) == '.')) {
                return parseNumber();
            }
            if (position < source.length() && Character.isLetter(source.charAt(position))) {
                return parseFunction();
            }
            throw invalid("表达式语法不完整");
        }

        private String parseMetricToken() {
            Matcher matcher = METRIC_TOKEN_PATTERN.matcher(source);
            matcher.region(position, source.length());
            if (!matcher.lookingAt()) {
                throw invalid("指标 token 格式错误");
            }
            position = matcher.end();
            recordToken();
            String code = matcher.group(1);
            String alias = aliases.get(code.toUpperCase(Locale.ROOT));
            if (alias == null) {
                throw invalid("表达式引用了未声明的指标: " + code);
            }
            return alias;
        }

        private String parseNumber() {
            int start = position;
            boolean digitsBeforeDot = false;
            while (position < source.length() && Character.isDigit(source.charAt(position))) {
                digitsBeforeDot = true;
                position++;
            }
            boolean digitsAfterDot = false;
            if (position < source.length() && source.charAt(position) == '.') {
                position++;
                while (position < source.length() && Character.isDigit(source.charAt(position))) {
                    digitsAfterDot = true;
                    position++;
                }
            }
            if (!digitsBeforeDot && !digitsAfterDot) {
                throw invalid("数值格式错误");
            }
            recordToken();
            return source.substring(start, position);
        }

        private String parseFunction() {
            int start = position;
            while (
                position < source.length() &&
                (Character.isLetterOrDigit(source.charAt(position)) || source.charAt(position) == '_')
            ) {
                position++;
            }
            String function = source.substring(start, position).toLowerCase(Locale.ROOT);
            if (!ALLOWED_FUNCTIONS.contains(function)) {
                throw invalid("不允许的函数或 SQL 标识符: " + function);
            }
            recordToken();
            skipWhitespace();
            expect('(');
            enterNesting();
            try {
                List<String> arguments = new ArrayList<>();
                skipWhitespace();
                if (consume(')')) {
                    throw invalid("函数参数不能为空");
                }
                while (true) {
                    arguments.add(parseAdditive());
                    skipWhitespace();
                    if (consume(',')) {
                        recordToken();
                        continue;
                    }
                    expect(')');
                    break;
                }
                validateArity(function, arguments.size());
                if ("nullif".equals(function) && "0".equals(arguments.get(1))) denominators.add("(" + arguments.get(0) + ") = 0");
                return function + "(" + String.join(", ", arguments) + ")";
            } finally {
                exitNesting();
            }
        }

        private void validateArity(String function, int count) {
            boolean valid = switch (function) {
                case "abs" -> count == 1;
                case "nullif" -> count == 2;
                case "round" -> count == 1 || count == 2;
                case "coalesce" -> count >= 2;
                default -> false;
            };
            if (!valid) {
                throw invalid("函数 " + function + " 的参数数量不合法");
            }
        }

        private void skipWhitespace() {
            while (position < source.length() && Character.isWhitespace(source.charAt(position))) {
                position++;
            }
        }

        private boolean startsWith(String value) {
            return source.startsWith(value, position);
        }

        private boolean consume(char expected) {
            if (position < source.length() && source.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!consume(expected)) {
                throw invalid("缺少字符 " + expected);
            }
        }

        private void recordToken() {
            tokenCount++;
            if (tokenCount > MAX_TOKEN_COUNT) {
                throw invalid("表达式 token 数量超过限制");
            }
        }

        private void enterNesting() {
            nestingDepth++;
            if (nestingDepth > MAX_NESTING_DEPTH) {
                throw invalid("表达式嵌套深度超过限制");
            }
        }

        private void exitNesting() {
            nestingDepth = Math.max(0, nestingDepth - 1);
        }

        private IllegalArgumentException invalid(String message) {
            return new IllegalArgumentException(message + "（位置 " + position + "）");
        }
    }
}
