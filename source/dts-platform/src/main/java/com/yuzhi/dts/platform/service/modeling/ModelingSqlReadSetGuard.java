package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.governance.QualitySqlScopeValidator;
import java.util.*;
import java.util.regex.Pattern;

/** Complements dbt dependency reconciliation with the complete physical SQL read set. */
public final class ModelingSqlReadSetGuard {
    private ModelingSqlReadSetGuard() {}
    private static final Pattern STATIC_REFERENCE = Pattern.compile("\\{\\{\\s*(?:ref|source)\\s*\\(\\s*(['\"])[A-Za-z_][A-Za-z0-9_]*\\1(?:\\s*,\\s*(['\"])[A-Za-z_][A-Za-z0-9_]*\\2)?\\s*\\)\\s*}}", Pattern.CASE_INSENSITIVE);
    public static void requireDeclared(String sql, Collection<String> physicalReferences) {
        Set<String> allowed = new HashSet<>();
        if (physicalReferences != null) physicalReferences.forEach(ref -> { if (ref != null) allowed.add(normalize(ref)); });
        var matcher = STATIC_REFERENCE.matcher(stripConfig(Objects.toString(sql, "")));
        StringBuffer resolved = new StringBuffer();
        int ordinal = 0;
        while (matcher.find()) {
            String placeholder = "f9_declared_" + UUID.randomUUID().toString().replace("-", "") + "_" + ordinal++;
            allowed.add(placeholder);
            matcher.appendReplacement(resolved, placeholder);
        }
        matcher.appendTail(resolved);
        // Dynamic Jinja is handled by the existing dbt compiler; it cannot establish an authorization read set.
        if (resolved.indexOf("{{") >= 0 || resolved.indexOf("{#") >= 0 || resolved.indexOf("{%") >= 0) throw denied();
        try {
            if (QualitySqlScopeValidator.modelingReadTables(resolved.toString()).stream().map(ModelingSqlReadSetGuard::normalize).anyMatch(table -> !allowed.contains(table))) throw denied();
        } catch (IllegalArgumentException failure) { throw denied(); }
    }
    private static String stripConfig(String sql) {
        var blocks = Pattern.compile("(?s)\\{\\{\\s*config\\s*\\((.*?)\\)\\s*}}").matcher(sql);
        StringBuffer result = new StringBuffer();
        while (blocks.find()) {
            new ConfigLiterals(blocks.group(1)).validate();
            blocks.appendReplacement(result, "");
        }
        blocks.appendTail(result);
        return result.toString();
    }
    /** Parses inert literals only; macro calls, operators and hook configuration cannot enter SQL. */
    private static final class ConfigLiterals {
        private static final Set<String> KEYS = Set.of("materialized", "alias", "schema", "database", "tags", "meta", "unique_key", "dts_columns", "dts_primary_keys");
        private final String input;
        private int offset;
        ConfigLiterals(String input) { this.input = input; }
        void validate() {
            whitespace();
            while (offset < input.length()) {
                String key = word();
                if (!KEYS.contains(key)) throw denied();
                expect('='); value(0); whitespace();
                if (offset == input.length()) return;
                expect(','); whitespace();
            }
        }
        private void value(int depth) {
            if (depth > 16) throw denied();
            whitespace();
            if (offset == input.length()) throw denied();
            char token = input.charAt(offset);
            if (token == '\'' || token == '"') { string(); return; }
            if (token == '[' || token == '{') {
                offset++; char end = token == '[' ? ']' : '}'; whitespace();
                while (offset < input.length() && input.charAt(offset) != end) {
                    if (token == '{') { string(); expect(':'); }
                    value(depth + 1); whitespace();
                    if (offset < input.length() && input.charAt(offset) == end) break;
                    expect(','); whitespace();
                }
                expect(end); return;
            }
            int start = offset;
            if (token == '-') offset++;
            while (offset < input.length() && (Character.isDigit(input.charAt(offset)) || input.charAt(offset) == '.')) offset++;
            if (offset > start) {
                if (!input.substring(start, offset).matches("-?[0-9]+(?:\\.[0-9]+)?")) throw denied();
                return;
            }
            if (!Set.of("true", "false", "none").contains(word().toLowerCase(Locale.ROOT))) throw denied();
        }
        private void string() {
            whitespace();
            if (offset >= input.length() || (input.charAt(offset) != '\'' && input.charAt(offset) != '"')) throw denied();
            char quote = input.charAt(offset++);
            while (offset < input.length()) {
                char next = input.charAt(offset++);
                if (next == quote) return;
                if (next == '\\') { if (offset == input.length()) throw denied(); offset++; }
                // dbt re-renders Jinja embedded inside configuration strings.
                if (next == '{' && offset < input.length() && "{%#".indexOf(input.charAt(offset)) >= 0) throw denied();
            }
            throw denied();
        }
        private String word() {
            whitespace(); int start = offset;
            while (offset < input.length() && (Character.isLetterOrDigit(input.charAt(offset)) || input.charAt(offset) == '_')) offset++;
            if (offset == start) throw denied();
            return input.substring(start, offset);
        }
        private void expect(char value) { whitespace(); if (offset == input.length() || input.charAt(offset++) != value) throw denied(); }
        private void whitespace() { while (offset < input.length() && Character.isWhitespace(input.charAt(offset))) offset++; }
    }
    public static void requireArtifacts(java.util.List<com.yuzhi.dts.platform.service.etl.DbtScopedProjectService.CandidateArtifactEntry> entries,
        java.util.List<ModelMaterializationSourceAvailabilityGuard.PinnedSourceDefinition> sources) {
        var references = sources.stream().map(source -> source.schemaName() + "." + source.tableName()).toList();
        entries.forEach(entry -> entry.artifacts().stream().filter(artifact -> artifact.path().toLowerCase(Locale.ROOT).endsWith(".sql"))
            .forEach(artifact -> requireDeclared(artifact.content(), references)));
    }
    private static String normalize(String ref) { String value = ref.trim().replace("`", ""); return value.contains("\"") ? value.replace("\"", "") : value.toLowerCase(Locale.ROOT); }
    private static ModelSpecException denied() { return new ModelSpecException("MODELING_SOURCE_READ_SET_UNVERIFIABLE", "查询来源无法完整核验，请使用已声明的数据来源", ModelSpecException.Kind.UNPROCESSABLE); }
}
