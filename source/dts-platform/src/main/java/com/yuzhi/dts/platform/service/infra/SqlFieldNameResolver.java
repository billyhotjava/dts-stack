package com.yuzhi.dts.platform.service.infra;

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
 * Rules:
 * 1) Keep English headers when possible (normalize to lower_snake_case).
 * 2) Translate known Chinese business headers by dictionary.
 * 3) Fallback to token-level translation and then pinyin fragments.
 * 4) Enforce SQL identifier safety and uniqueness.
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
        // 项目进度 Excel 29 字段
        putExact(map, "项目编号", "project_no");
        putExact(map, "分系统/分任务", "subsystem");
        putExact(map, "节点任务及目标", "node_task");
        putExact(map, "节点计划时间", "plan_date");
        putExact(map, "节点计划周数", "plan_week");
        putExact(map, "节点类型", "node_type");
        putExact(map, "负责人", "owner");
        putExact(map, "责任科室", "dept");
        putExact(map, "分管室领导", "dept_leader");
        putExact(map, "完成情况", "completion_status");
        putExact(map, "协同部门", "collab_dept");
        putExact(map, "责任监管部门", "supervisor_dept");
        putExact(map, "延期预计完成时间", "delay_expected_date");
        putExact(map, "未完成原因及当前进展", "incomplete_reason");
        putExact(map, "风险等级", "risk_level");
        putExact(map, "主要风险内容及措施", "risk_content");
        putExact(map, "延期影响分析", "delay_impact");
        putExact(map, "实际完成时间", "actual_date");
        putExact(map, "实际完成周数", "actual_week");
        putExact(map, "所领导", "institute_leader");
        putExact(map, "来源", "source");
        putExact(map, "延期项目原计划时间", "original_plan_date");
        putExact(map, "计划延误时间（已变更）", "delay_days_changed");
        putExact(map, "计划延误时间（未变更）", "delay_days_unchanged");
        putExact(map, "是否提交延期申请", "delay_applied");
        putExact(map, "项目主管", "project_manager");
        putExact(map, "最后更新时间", "last_update_time");
        putExact(map, "填写人", "filled_by");
        putExact(map, "亮点工作", "highlight");
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
        // 项目进度通用术语
        map.put("项目", "project");
        map.put("节点", "node");
        map.put("任务", "task");
        map.put("计划", "plan");
        map.put("负责人", "owner");
        map.put("科室", "dept");
        map.put("领导", "leader");
        map.put("完成", "completion");
        map.put("情况", "status");
        map.put("协同", "collab");
        map.put("监管", "supervisor");
        map.put("延期", "delay");
        map.put("原因", "reason");
        map.put("风险", "risk");
        map.put("等级", "level");
        map.put("措施", "measure");
        map.put("影响", "impact");
        map.put("分析", "analysis");
        map.put("实际", "actual");
        map.put("周数", "week");
        map.put("填写", "filled");
        map.put("亮点", "highlight");
        map.put("工作", "work");
        map.put("主管", "manager");
        map.put("更新", "update");
        map.put("变更", "changed");
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
        map.put("公", "gong");
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

