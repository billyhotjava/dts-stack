package com.yuzhi.dts.ingestion.service.etl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * Resolve spreadsheet headers to SQL-friendly field names.
 */
public final class SqlFieldNameResolver {

    private static final int MAX_IDENTIFIER_LENGTH = 63;
    private static final Pattern CAMEL_CASE_BOUNDARY = Pattern.compile("([a-z0-9])([A-Z])");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern MULTI_UNDERSCORE = Pattern.compile("_+");
    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\u4E00-\\u9FFF]");
    private static final Pattern COMPACT_KEY_STRIP = Pattern.compile("[\\s_\\-()（）\\[\\]{}<>《》\"'`·、，,。:：;；/\\\\]+");

    private static final Set<String> RESERVED_KEYWORDS = Set.of(
        "all", "and", "as", "by", "case", "create", "default", "delete", "desc",
        "distinct", "drop", "else", "end", "exists", "from", "full", "group",
        "having", "in", "index", "insert", "into", "is", "join", "key", "left",
        "like", "limit", "not", "null", "on", "or", "order", "outer", "primary",
        "right", "select", "set", "table", "then", "union", "unique", "update",
        "user", "using", "values", "view", "when", "where"
    );

    private static final Map<String, String> EXACT_TRANSLATIONS = buildExactTranslations();
    private static final Map<String, String> TERM_TRANSLATIONS = buildTermTranslations();
    private static final List<String> TERM_KEYS = buildTermKeys();
    private static final Map<String, String> CHAR_PINYIN = buildCharPinyin();

    private SqlFieldNameResolver() {}

    public static String resolve(String rawHeader, int index, Set<String> used) {
        Set<String> usedNames = used != null ? used : new LinkedHashSet<>();
        String raw = stripOuterQuotes(rawHeader);
        String candidate = EXACT_TRANSLATIONS.get(compactKey(raw));
        if (!StringUtils.hasText(candidate)) {
            candidate = containsChinese(raw) ? translateMixed(raw) : asciiToSnake(raw);
        }
        candidate = sanitizeIdentifier(candidate, index);
        candidate = ensureUnique(candidate, index, usedNames);
        if (used != null) {
            used.add(candidate);
        }
        return candidate;
    }

    private static String translateMixed(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        List<String> tokens = new ArrayList<>();
        StringBuilder segment = new StringBuilder();
        Boolean segmentChinese = null;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            boolean isChinese = isChineseChar(ch);
            boolean isAsciiAlphaNum = isAsciiAlphaNum(ch);
            if (!isChinese && !isAsciiAlphaNum) {
                flushSegment(segment, segmentChinese, tokens);
                segment.setLength(0);
                segmentChinese = null;
                continue;
            }
            if (segmentChinese == null) {
                segmentChinese = isChinese;
                segment.append(ch);
                continue;
            }
            if (segmentChinese.booleanValue() == isChinese) {
                segment.append(ch);
                continue;
            }
            flushSegment(segment, segmentChinese, tokens);
            segment.setLength(0);
            segment.append(ch);
            segmentChinese = isChinese;
        }
        flushSegment(segment, segmentChinese, tokens);
        return String.join("_", tokens);
    }

    private static void flushSegment(StringBuilder segment, Boolean segmentChinese, List<String> tokens) {
        if (segment == null || segment.isEmpty()) {
            return;
        }
        String text = segment.toString();
        if (Boolean.TRUE.equals(segmentChinese)) {
            appendChineseTokens(text, tokens);
            return;
        }
        String ascii = asciiToSnake(text);
        appendToken(tokens, ascii);
    }

    private static void appendChineseTokens(String text, List<String> tokens) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        String exact = EXACT_TRANSLATIONS.get(compactKey(text));
        if (StringUtils.hasText(exact)) {
            appendToken(tokens, exact);
            return;
        }
        int idx = 0;
        while (idx < text.length()) {
            String matchedTerm = matchTermAt(text, idx);
            if (matchedTerm != null) {
                appendToken(tokens, TERM_TRANSLATIONS.get(matchedTerm));
                idx += matchedTerm.length();
                continue;
            }
            String one = text.substring(idx, idx + 1);
            String pinyin = CHAR_PINYIN.get(one);
            if (StringUtils.hasText(pinyin)) {
                appendToken(tokens, pinyin);
            }
            idx++;
        }
    }

    private static String matchTermAt(String source, int offset) {
        if (!StringUtils.hasText(source) || offset < 0 || offset >= source.length()) {
            return null;
        }
        for (String key : TERM_KEYS) {
            if (source.startsWith(key, offset)) {
                return key;
            }
        }
        return null;
    }

    private static void appendToken(List<String> tokens, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        String[] parts = value.split("_");
        for (String part : parts) {
            String normalized = asciiToSnake(part);
            if (StringUtils.hasText(normalized)) {
                tokens.add(normalized);
            }
        }
    }

    private static String sanitizeIdentifier(String value, int index) {
        String normalized = asciiToSnake(value);
        if (!StringUtils.hasText(normalized)) {
            normalized = "field_" + (index + 1);
        }
        if (Character.isDigit(normalized.charAt(0))) {
            normalized = "col_" + normalized;
        }
        if (RESERVED_KEYWORDS.contains(normalized)) {
            normalized = normalized + "_col";
        }
        normalized = truncate(normalized, MAX_IDENTIFIER_LENGTH);
        if (!StringUtils.hasText(normalized)) {
            normalized = "field_" + (index + 1);
        }
        return normalized;
    }

    private static String ensureUnique(String base, int index, Set<String> used) {
        String normalizedBase = StringUtils.hasText(base) ? base : "field_" + (index + 1);
        String candidate = normalizedBase;
        int seq = 2;
        while (used.contains(candidate)) {
            String suffix = "_" + seq++;
            candidate = truncate(normalizedBase, MAX_IDENTIFIER_LENGTH - suffix.length()) + suffix;
        }
        return candidate;
    }

    private static String asciiToSnake(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String text = stripOuterQuotes(raw);
        text = CAMEL_CASE_BOUNDARY.matcher(text).replaceAll("$1_$2");
        text = text.toLowerCase(Locale.ROOT);
        text = NON_ALNUM.matcher(text).replaceAll("_");
        text = MULTI_UNDERSCORE.matcher(text).replaceAll("_");
        text = text.replaceAll("^_+|_+$", "");
        return text;
    }

    private static String stripOuterQuotes(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String text = raw.trim();
        if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'"))) {
            return text.substring(1, text.length() - 1).trim();
        }
        return text;
    }

    private static boolean containsChinese(String text) {
        return StringUtils.hasText(text) && CHINESE_PATTERN.matcher(text).find();
    }

    private static boolean isChineseChar(char ch) {
        return ch >= '\u4E00' && ch <= '\u9FFF';
    }

    private static boolean isAsciiAlphaNum(char ch) {
        return (ch >= 'a' && ch <= 'z')
            || (ch >= 'A' && ch <= 'Z')
            || (ch >= '0' && ch <= '9');
    }

    private static String compactKey(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String lowered = stripOuterQuotes(text).toLowerCase(Locale.ROOT);
        return COMPACT_KEY_STRIP.matcher(lowered).replaceAll("");
    }

    private static String truncate(String text, int maxLength) {
        if (!StringUtils.hasText(text) || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(1, maxLength));
    }

    private static Map<String, String> buildExactTranslations() {
        Map<String, String> map = new LinkedHashMap<>();
        putExact(map, "序号", "seq_no");
        putExact(map, "专利名称（中文）", "patent_title_cn");
        putExact(map, "专利名称(中文)", "patent_title_cn");
        putExact(map, "专利类型", "patent_type");
        putExact(map, "专利号", "patent_no");
        putExact(map, "申请日", "application_date");
        putExact(map, "授权日", "grant_date");
        putExact(map, "首次公开日", "first_publication_date");
        putExact(map, "专利权人", "assignee_name");
        putExact(map, "发明人", "inventor_names");
        putExact(map, "部门", "dept_name");
        putExact(map, "代理公司", "agent_org_name");
        putExact(map, "状态", "state");
        return Collections.unmodifiableMap(map);
    }

    private static void putExact(Map<String, String> map, String raw, String translated) {
        map.put(compactKey(raw), translated);
    }

    private static Map<String, String> buildTermTranslations() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("编号", "no");
        map.put("序号", "seq_no");
        map.put("名称", "name");
        map.put("中文", "cn");
        map.put("英文", "en");
        map.put("专利", "patent");
        map.put("类型", "type");
        map.put("申请", "application");
        map.put("授权", "grant");
        map.put("日期", "date");
        map.put("公开", "publication");
        map.put("首次", "first");
        map.put("权人", "assignee");
        map.put("发明人", "inventor_names");
        map.put("部门", "dept_name");
        map.put("代码", "code");
        map.put("代理", "agent");
        map.put("公司", "org_name");
        map.put("状态", "state");
        map.put("金额", "amount");
        map.put("数量", "qty");
        map.put("时间", "time");
        map.put("日期", "date");
        map.put("地址", "address");
        map.put("电话", "phone");
        return Collections.unmodifiableMap(map);
    }

    private static List<String> buildTermKeys() {
        List<String> keys = new ArrayList<>(TERM_TRANSLATIONS.keySet());
        keys.sort((a, b) -> Integer.compare(b.length(), a.length()));
        return List.copyOf(keys);
    }

    private static Map<String, String> buildCharPinyin() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("名", "ming");
        map.put("称", "cheng");
        map.put("号", "hao");
        map.put("类", "lei");
        map.put("型", "xing");
        map.put("专", "zhuan");
        map.put("利", "li");
        map.put("申", "shen");
        map.put("请", "qing");
        map.put("授", "shou");
        map.put("权", "quan");
        map.put("公", "gong");
        map.put("开", "kai");
        map.put("日", "ri");
        map.put("部", "bu");
        map.put("门", "men");
        map.put("代", "dai");
        map.put("理", "li");
        map.put("司", "si");
        map.put("状", "zhuang");
        map.put("态", "tai");
        map.put("数", "shu");
        map.put("据", "ju");
        map.put("年", "year");
        map.put("月", "month");
        map.put("天", "day");
        map.put("值", "zhi");
        return Collections.unmodifiableMap(map);
    }
}

