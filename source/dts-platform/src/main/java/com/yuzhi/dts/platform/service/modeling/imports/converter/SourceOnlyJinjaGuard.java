package com.yuzhi.dts.platform.service.modeling.imports.converter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Full-consumption whitelist for source-only Jinja expressions; it never evaluates Jinja or SQL. */
final class SourceOnlyJinjaGuard {

    private static final Set<String> MATERIALIZATIONS = Set.of("table", "view", "incremental", "ephemeral", "dts_schema_only");
    private static final int MAX_LITERAL_LENGTH = 256;
    private static final int MAX_TAGS = 64;
    private static final int MAX_METADATA_ENTRIES = 32;

    private SourceOnlyJinjaGuard() {}

    static Risk inspect(String sql) {
        boolean dynamic = false;
        boolean macro = false;
        String materialization = null;
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        int cursor = 0;
        while (cursor < sql.length()) {
            int expression = sql.indexOf("{{", cursor);
            int control = sql.indexOf("{%", cursor);
            int comment = sql.indexOf("{#", cursor);
            int start = earliest(expression, control, comment);
            if (start < 0) {
                break;
            }
            if (start == comment) {
                int end = sql.indexOf("#}", start + 2);
                if (end < 0) {
                    dynamic = true;
                    break;
                }
                cursor = end + 2;
                continue;
            }
            if (start == control) {
                dynamic = true;
                int end = sql.indexOf("%}", start + 2);
                if (end < 0) {
                    break;
                }
                ExpressionResult controlResult = new ExpressionParser(sql.substring(start + 2, end)).parse();
                macro |= controlResult.macro();
                cursor = end + 2;
                continue;
            }
            int end = sql.indexOf("}}", start + 2);
            if (end < 0) {
                dynamic = true;
                break;
            }
            ExpressionResult result = new ExpressionParser(sql.substring(start + 2, end)).parse();
            dynamic |= result.dynamic();
            macro |= result.macro();
            if (result.materialization() != null) {
                if (materialization != null && !materialization.equals(result.materialization())) {
                    dynamic = true;
                } else {
                    materialization = result.materialization();
                }
            }
            tags.addAll(result.tags());
            if (tags.size() > MAX_TAGS) {
                dynamic = true;
            }
            cursor = end + 2;
        }
        return new Risk(dynamic, macro, dynamic || macro ? null : materialization, dynamic || macro ? List.of() : List.copyOf(tags));
    }

    private static int earliest(int first, int second, int third) {
        int result = -1;
        for (int value : new int[] { first, second, third }) {
            if (value >= 0 && (result < 0 || value < result)) {
                result = value;
            }
        }
        return result;
    }

    record Risk(boolean dynamic, boolean macro, String materialization, List<String> tags) {

        Risk {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }

    private record ExpressionResult(
        boolean dynamic,
        boolean macro,
        String materialization,
        List<String> tags
    ) {

        private static ExpressionResult safeResult() {
            return new ExpressionResult(false, false, null, List.of());
        }

        private static ExpressionResult dynamicResult() {
            return new ExpressionResult(true, false, null, List.of());
        }

        private static ExpressionResult macroResult() {
            return new ExpressionResult(false, true, null, List.of());
        }
    }

    private static final class ExpressionParser {

        private final String value;
        private int cursor;

        private ExpressionParser(String value) {
            this.value = value;
        }

        private ExpressionResult parse() {
            try {
                skipWhitespace();
                String function = identifier(true);
                if (function == null || !consume('(')) {
                    return ExpressionResult.dynamicResult();
                }
                String normalized = function.toLowerCase(Locale.ROOT);
                return switch (normalized) {
                    case "ref" -> finishLiteralCall(1, 2);
                    case "source" -> finishLiteralCall(2, 2);
                    case "config" -> finishConfigCall();
                    case "var", "env_var" -> ExpressionResult.dynamicResult();
                    default -> ExpressionResult.macroResult();
                };
            } catch (UnsafeExpressionException exception) {
                return ExpressionResult.dynamicResult();
            }
        }

        private ExpressionResult finishLiteralCall(int minimum, int maximum) {
            int arguments = 0;
            skipWhitespace();
            while (!peek(')')) {
                literal(MAX_LITERAL_LENGTH);
                arguments++;
                if (arguments > maximum) {
                    throw unsafe();
                }
                skipWhitespace();
                if (peek(')')) {
                    break;
                }
                require(',');
                skipWhitespace();
                if (peek(')')) {
                    throw unsafe();
                }
            }
            require(')');
            requireEnd();
            return arguments >= minimum && arguments <= maximum
                ? ExpressionResult.safeResult()
                : ExpressionResult.dynamicResult();
        }

        private ExpressionResult finishConfigCall() {
            String materialization = null;
            LinkedHashSet<String> tags = new LinkedHashSet<>();
            Set<String> seen = new LinkedHashSet<>();
            skipWhitespace();
            while (!peek(')')) {
                String key = identifier(false);
                if (key == null || !seen.add(key.toLowerCase(Locale.ROOT))) {
                    throw unsafe();
                }
                skipWhitespace();
                require('=');
                skipWhitespace();
                switch (key.toLowerCase(Locale.ROOT)) {
                    case "materialized" -> {
                        materialization = literal(64).toLowerCase(Locale.ROOT);
                        if (!MATERIALIZATIONS.contains(materialization)) {
                            throw unsafe();
                        }
                    }
                    case "tags" -> tags.addAll(tagValue());
                    case "alias" -> literal(128);
                    case "unique_key" -> tagValue();
                    case "dts_columns" -> schemaColumns();
                    case "dts_primary_keys" -> tagValue();
                    case "meta" -> metadataMap();
                    default -> throw unsafe();
                }
                skipWhitespace();
                if (peek(')')) {
                    break;
                }
                require(',');
                skipWhitespace();
                if (peek(')')) {
                    break;
                }
            }
            require(')');
            requireEnd();
            boolean schema = "dts_schema_only".equals(materialization);
            if (schema != seen.contains("dts_columns") || schema != seen.contains("dts_primary_keys")) throw unsafe();
            if (tags.size() > MAX_TAGS) {
                throw unsafe();
            }
            return new ExpressionResult(false, false, materialization, List.copyOf(tags));
        }

        private void schemaColumns() {
            require('[');
            Set<String> names = new LinkedHashSet<>();
            do {
                skipWhitespace();
                require('{');
                Set<String> keys = new LinkedHashSet<>();
                do {
                    skipWhitespace();
                    String key = literal(128);
                    if (!keys.add(key)) throw unsafe();
                    skipWhitespace();
                    require(':');
                    skipWhitespace();
                    switch (key) {
                        case "name" -> {
                            String name = literal(63);
                            if (!name.matches("[A-Za-z_][A-Za-z0-9_]{0,62}") || !names.add(name)) throw unsafe();
                        }
                        case "data_type" -> {
                            String type = literal(128);
                            try {
                                if (!type.equals(com.yuzhi.dts.platform.service.modeling.ModelSchemaOnlySupport.requirePhysicalColumnType(type))) throw unsafe();
                            } catch (IllegalArgumentException invalid) { throw unsafe(); }
                        }
                        case "nullable" -> {
                            String bool = identifier(false);
                            if (!"True".equals(bool) && !"False".equals(bool)) throw unsafe();
                        }
                        default -> throw unsafe();
                    }
                    skipWhitespace();
                    if (peek('}')) break;
                    require(',');
                } while (true);
                require('}');
                if (!keys.equals(Set.of("name", "data_type", "nullable")) || names.size() > 1000) throw unsafe();
                skipWhitespace();
                if (peek(']')) break;
                require(',');
            } while (true);
            require(']');
        }

        private List<String> tagValue() {
            if (!peek('[')) {
                return List.of(literal(128));
            }
            require('[');
            List<String> result = new ArrayList<>();
            skipWhitespace();
            while (!peek(']')) {
                result.add(literal(128));
                if (result.size() > MAX_TAGS) {
                    throw unsafe();
                }
                skipWhitespace();
                if (peek(']')) {
                    break;
                }
                require(',');
                skipWhitespace();
                if (peek(']')) {
                    break;
                }
            }
            require(']');
            return List.copyOf(result);
        }

        private void metadataMap() {
            require('{');
            skipWhitespace();
            Set<String> keys = new LinkedHashSet<>();
            int count = 0;
            while (!peek('}')) {
                String key = literal(128);
                if (!keys.add(key)) throw unsafe();
                skipWhitespace();
                require(':');
                metadataScalar();
                if (++count > MAX_METADATA_ENTRIES) throw unsafe();
                skipWhitespace();
                if (peek('}')) break;
                require(',');
                skipWhitespace();
                if (peek('}')) throw unsafe();
            }
            require('}');
        }

        private void metadataScalar() {
            skipWhitespace();
            if (peek('\'') || peek('"')) {
                literal(MAX_LITERAL_LENGTH);
                return;
            }
            int start = cursor;
            while (cursor < value.length() && value.charAt(cursor) >= '0' && value.charAt(cursor) <= '9') {
                cursor++;
            }
            if (cursor == start || cursor - start > 19) throw unsafe();
        }

        private String literal(int maximumLength) {
            skipWhitespace();
            if (cursor >= value.length() || (value.charAt(cursor) != '\'' && value.charAt(cursor) != '"')) {
                throw unsafe();
            }
            char quote = value.charAt(cursor++);
            StringBuilder result = new StringBuilder();
            boolean closed = false;
            while (cursor < value.length()) {
                char character = value.charAt(cursor++);
                if (character == quote) {
                    closed = true;
                    break;
                }
                if (character == '\\') {
                    if (cursor >= value.length()) {
                        throw unsafe();
                    }
                    character = value.charAt(cursor++);
                }
                if (Character.isISOControl(character) || result.length() >= maximumLength) {
                    throw unsafe();
                }
                result.append(character);
            }
            String literal = result.toString().trim();
            if (
                !closed ||
                literal.isEmpty() ||
                literal.contains("{{") ||
                literal.contains("}}") ||
                literal.contains("{%") ||
                literal.contains("%}")
            ) {
                throw unsafe();
            }
            return literal;
        }

        private String identifier(boolean dotted) {
            skipWhitespace();
            if (cursor >= value.length() || !identifierStart(value.charAt(cursor))) {
                return null;
            }
            int start = cursor++;
            while (cursor < value.length()) {
                char character = value.charAt(cursor);
                if (identifierPart(character) || (dotted && character == '.')) {
                    cursor++;
                } else {
                    break;
                }
            }
            return value.substring(start, cursor);
        }

        private void requireEnd() {
            skipWhitespace();
            if (cursor != value.length()) {
                throw unsafe();
            }
        }

        private boolean consume(char expected) {
            skipWhitespace();
            if (!peek(expected)) {
                return false;
            }
            cursor++;
            return true;
        }

        private void require(char expected) {
            if (!consume(expected)) {
                throw unsafe();
            }
        }

        private boolean peek(char expected) {
            return cursor < value.length() && value.charAt(cursor) == expected;
        }

        private void skipWhitespace() {
            while (cursor < value.length() && Character.isWhitespace(value.charAt(cursor))) {
                cursor++;
            }
        }

        private static boolean identifierStart(char character) {
            return Character.isLetter(character) || character == '_';
        }

        private static boolean identifierPart(char character) {
            return Character.isLetterOrDigit(character) || character == '_';
        }

        private static UnsafeExpressionException unsafe() {
            return new UnsafeExpressionException();
        }
    }

    private static final class UnsafeExpressionException extends RuntimeException {}
}
