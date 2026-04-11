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
    private static final Pattern CHINESE_PATTERN = Pattern.compile(
        "[\\u4E00-\\u9FFF\\u3400-\\u4DBF\\uF900-\\uFAFF]"
        + "|[\\uD840-\\uD87F][\\uDC00-\\uDFFF]"  // surrogate pairs for CJK Extension B-F
    );
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
        // Use codepoint iteration to handle CJK Extension B+ (surrogate pairs)
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            int charCount = Character.charCount(cp);
            boolean isChinese = isChineseCodePoint(cp);
            boolean isAsciiAlphaNum = cp < 128 && isAsciiAlphaNum((char) cp);
            if (!isChinese && !isAsciiAlphaNum) {
                flushSegment(segment, segmentChinese, tokens);
                segment.setLength(0);
                segmentChinese = null;
                i += charCount;
                continue;
            }
            if (segmentChinese == null) {
                segmentChinese = isChinese;
                segment.appendCodePoint(cp);
                i += charCount;
                continue;
            }
            if (segmentChinese.booleanValue() == isChinese) {
                segment.appendCodePoint(cp);
                i += charCount;
                continue;
            }
            flushSegment(segment, segmentChinese, tokens);
            segment.setLength(0);
            segment.appendCodePoint(cp);
            segmentChinese = isChinese;
            i += charCount;
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
        return isChineseCodePoint(ch);
    }

    private static boolean isChineseCodePoint(int cp) {
        return (cp >= 0x4E00 && cp <= 0x9FFF)       // CJK Unified Ideographs
            || (cp >= 0x3400 && cp <= 0x4DBF)        // CJK Extension A (rare chars in names)
            || (cp >= 0xF900 && cp <= 0xFAFF)        // CJK Compatibility Ideographs
            || (cp >= 0x20000 && cp <= 0x2A6DF)      // CJK Extension B (surrogate pairs)
            || (cp >= 0x2A700 && cp <= 0x2B73F)      // CJK Extension C
            || (cp >= 0x2B740 && cp <= 0x2B81F)      // CJK Extension D
            || (cp >= 0x2B820 && cp <= 0x2CEAF)      // CJK Extension E
            || (cp >= 0x2CEB0 && cp <= 0x2EBEF)      // CJK Extension F
            || (cp >= 0x2F800 && cp <= 0x2FA1F);     // CJK Compatibility Supplement
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
        map.put("司", "si");
        map.put("状", "zhuang");
        map.put("态", "tai");
        map.put("数", "shu");
        map.put("据", "ju");
        map.put("年", "year");
        map.put("月", "month");
        map.put("天", "day");
        map.put("值", "zhi");
        // 常见业务字段用字
        map.put("薪", "xin");
        map.put("资", "zi");
        map.put("工", "gong");
        map.put("龄", "ling");
        map.put("岗", "gang");
        map.put("位", "wei");
        map.put("级", "ji");
        map.put("别", "bie");
        map.put("性", "xing");
        map.put("族", "zu");
        map.put("民", "min");
        map.put("籍", "ji");
        map.put("贯", "guan");
        map.put("址", "zhi");
        map.put("话", "hua");
        map.put("件", "jian");
        map.put("邮", "you");
        map.put("箱", "xiang");
        map.put("期", "qi");
        map.put("始", "shi");
        map.put("止", "zhi");
        map.put("终", "zhong");
        map.put("总", "zong");
        map.put("额", "e");
        map.put("价", "jia");
        map.put("格", "ge");
        map.put("单", "dan");
        map.put("量", "liang");
        map.put("重", "zhong");
        map.put("高", "gao");
        map.put("长", "chang");
        map.put("宽", "kuan");
        map.put("面", "mian");
        map.put("积", "ji");
        map.put("率", "lv");
        map.put("比", "bi");
        map.put("例", "li");
        map.put("百", "bai");
        map.put("分", "fen");
        map.put("人", "ren");
        map.put("员", "yuan");
        map.put("组", "zu");
        map.put("室", "shi");
        map.put("院", "yuan");
        map.put("所", "suo");
        map.put("厂", "chang");
        map.put("产", "chan");
        map.put("品", "pin");
        map.put("物", "wu");
        map.put("料", "liao");
        map.put("材", "cai");
        map.put("设", "she");
        map.put("备", "bei");
        map.put("器", "qi");
        map.put("机", "ji");
        map.put("车", "che");
        map.put("间", "jian");
        map.put("段", "duan");
        map.put("区", "qu");
        map.put("域", "yu");
        map.put("层", "ceng");
        map.put("线", "xian");
        map.put("路", "lu");
        map.put("号", "hao");
        map.put("栋", "dong");
        map.put("楼", "lou");
        map.put("入", "ru");
        map.put("出", "chu");
        map.put("收", "shou");
        map.put("发", "fa");
        map.put("送", "song");
        map.put("到", "dao");
        map.put("回", "hui");
        map.put("用", "yong");
        map.put("费", "fei");
        map.put("支", "zhi");
        map.put("付", "fu");
        map.put("账", "zhang");
        map.put("款", "kuan");
        map.put("税", "shui");
        map.put("票", "piao");
        map.put("订", "ding");
        map.put("合", "he");
        map.put("同", "tong");
        map.put("签", "qian");
        map.put("审", "shen");
        map.put("批", "pi");
        map.put("核", "he");
        map.put("验", "yan");
        map.put("检", "jian");
        map.put("测", "ce");
        map.put("试", "shi");
        map.put("评", "ping");
        map.put("定", "ding");
        map.put("结", "jie");
        map.put("果", "guo");
        map.put("方", "fang");
        map.put("案", "an");
        map.put("法", "fa");
        map.put("规", "gui");
        map.put("则", "ze");
        map.put("标", "biao");
        map.put("准", "zhun");
        map.put("要", "yao");
        map.put("求", "qiu");
        map.put("条", "tiao");
        map.put("目", "mu");
        map.put("主", "zhu");
        map.put("副", "fu");
        map.put("正", "zheng");
        map.put("负", "fu");
        map.put("是", "shi");
        map.put("否", "fou");
        map.put("有", "you");
        map.put("无", "wu");
        map.put("大", "da");
        map.put("小", "xiao");
        map.put("新", "xin");
        map.put("旧", "jiu");
        map.put("上", "shang");
        map.put("下", "xia");
        map.put("前", "qian");
        map.put("后", "hou");
        map.put("左", "zuo");
        map.put("右", "you");
        map.put("内", "nei");
        map.put("外", "wai");
        map.put("第", "di");
        map.put("次", "ci");
        map.put("版", "ban");
        map.put("本", "ben");
        map.put("全", "quan");
        map.put("半", "ban");
        map.put("初", "chu");
        map.put("末", "mo");
        map.put("中", "zhong");
        map.put("首", "shou");
        map.put("尾", "wei");
        map.put("头", "tou");
        map.put("身", "shen");
        map.put("份", "fen");
        map.put("证", "zheng");
        map.put("照", "zhao");
        map.put("册", "ce");
        map.put("记", "ji");
        map.put("录", "lu");
        map.put("志", "zhi");
        map.put("信", "xin");
        map.put("息", "xi");
        map.put("告", "gao");
        map.put("知", "zhi");
        map.put("通", "tong");
        map.put("报", "bao");
        map.put("表", "biao");
        map.put("图", "tu");
        map.put("文", "wen");
        map.put("字", "zi");
        map.put("码", "ma");
        map.put("密", "mi");
        map.put("网", "wang");
        map.put("链", "lian");
        map.put("接", "jie");
        map.put("系", "xi");
        map.put("关", "guan");
        map.put("联", "lian");
        return Collections.unmodifiableMap(map);
    }
}

