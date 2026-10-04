package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class ScreenAiDraftService {

    private static final int DEFAULT_WIDTH = 1920;
    private static final int DEFAULT_HEIGHT = 1080;

    private final ObjectMapper objectMapper;

    public ScreenAiDraftService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode generateDraft(String prompt, Integer width, Integer height, String locale) {
        String normalizedPrompt = prompt == null ? "" : prompt.trim();
        String lowered = normalizedPrompt.toLowerCase(Locale.ROOT);

        int w = width == null || width <= 0 ? DEFAULT_WIDTH : width;
        int h = height == null || height <= 0 ? DEFAULT_HEIGHT : height;

        String scene = detectScene(lowered);
        String theme = detectTheme(lowered);
        String backgroundColor = "glacier".equals(theme) ? "#f8fafc" : "#0a0f25";
        String title = resolveTitle(normalizedPrompt, scene);

        ArrayNode components = objectMapper.createArrayNode();
        int z = 1;
        components.add(component("ai_title", "title", "主标题", 640, 20, 640, 64, z++,
                node("text", title, "fontSize", 42, "fontWeight", "700",
                        "color", "glacier".equals(theme) ? "#0f172a" : "#22d3ee", "textAlign", "center")));

        components.add(component("ai_datetime", "datetime", "时间", 1600, 24, 280, 36, z++,
                node("format", "YYYY-MM-DD HH:mm:ss", "fontSize", 18,
                        "color", "glacier".equals(theme) ? "#334155" : "#93c5fd")));

        components.add(component("ai_card_1", "number-card", "指标1", 50, 110, 270, 96, z++,
                node("title", cardTitle(scene, 1), "value", 12853, "precision", 0,
                        "titleColor", "#94a3b8", "valueColor", "#34d399",
                        "backgroundColor", "glacier".equals(theme) ? "rgba(15,23,42,0.06)" : "rgba(15,23,42,0.4)")));

        components.add(component("ai_card_2", "number-card", "指标2", 340, 110, 270, 96, z++,
                node("title", cardTitle(scene, 2), "value", 529, "precision", 0,
                        "titleColor", "#94a3b8", "valueColor", "#38bdf8",
                        "backgroundColor", "glacier".equals(theme) ? "rgba(15,23,42,0.06)" : "rgba(15,23,42,0.4)")));

        components.add(component("ai_card_3", "number-card", "指标3", 630, 110, 270, 96, z++,
                node("title", cardTitle(scene, 3), "value", 97.6, "suffix", "%", "precision", 1,
                        "titleColor", "#94a3b8", "valueColor", "#f59e0b",
                        "backgroundColor", "glacier".equals(theme) ? "rgba(15,23,42,0.06)" : "rgba(15,23,42,0.4)")));

        components.add(component("ai_card_4", "number-card", "指标4", 920, 110, 270, 96, z++,
                node("title", cardTitle(scene, 4), "value", 82, "suffix", "%", "precision", 0,
                        "titleColor", "#94a3b8", "valueColor", "#a78bfa",
                        "backgroundColor", "glacier".equals(theme) ? "rgba(15,23,42,0.06)" : "rgba(15,23,42,0.4)")));

        components.add(component("ai_line", "line-chart", "趋势图", 50, 230, 920, 390, z++,
                node("title", chartTitle(scene, "trend"),
                        "xAxisData", arr("1月", "2月", "3月", "4月", "5月", "6月"),
                        "series", series2("本期", arr(120, 180, 260, 300, 360, 420), "同期", arr(100, 150, 220, 270, 310, 390)),
                        "lineSmooth", true,
                        "areaStyle", true)));

        components.add(component("ai_bar", "bar-chart", "结构图", 1000, 230, 870, 390, z++,
                node("title", chartTitle(scene, "struct"),
                        "xAxisData", barX(scene),
                        "series", series1("数量", barY(scene)))));

        components.add(component("ai_table", "table", "明细", 50, 640, 1820, 390, z++,
                node("title", chartTitle(scene, "detail"),
                        "columns", arrObj(
                                node("key", "name", "title", firstColumnTitle(scene)),
                                node("key", "value", "title", "数值"),
                                node("key", "rate", "title", "占比")),
                        "data", arrObj(
                                node("name", sampleRow(scene, 1), "value", 1200, "rate", "35%"),
                                node("name", sampleRow(scene, 2), "value", 980, "rate", "28%"),
                                node("name", sampleRow(scene, 3), "value", 760, "rate", "22%"),
                                node("name", sampleRow(scene, 4), "value", 480, "rate", "15%")))));

        ArrayNode globalVariables = objectMapper.createArrayNode();
        globalVariables.add(node("key", "stat_year", "label", "统计年份", "type", "number", "defaultValue", "2025"));
        globalVariables.add(node("key", "dept_name", "label", "部门", "type", "string", "defaultValue", "全部"));

        ArrayNode warnings = objectMapper.createArrayNode();
        warnings.add("当前为离线规则生成草稿，请在设计器中微调字段映射与样式。");
        warnings.add("如需复杂口径，请绑定语义指标或改用已发布查询卡片。\n");

        ObjectNode result = objectMapper.createObjectNode();
        result.put("name", title);
        result.put("description", "由 AI 规则引擎根据需求自动生成的初版大屏草稿");
        result.put("width", w);
        result.put("height", h);
        result.put("backgroundColor", backgroundColor);
        result.put("theme", theme);
        result.set("components", components);
        result.set("globalVariables", globalVariables);
        result.set("warnings", warnings);

        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("mode", "rule-based-offline");
        meta.put("llm", false);
        meta.put("locale", locale == null || locale.isBlank() ? "zh-CN" : locale);
        result.set("generation", meta);
        return result;
    }

    private String detectScene(String loweredPrompt) {
        if (loweredPrompt.contains("专利") || loweredPrompt.contains("patent")) {
            return "patent";
        }
        if (loweredPrompt.contains("销售") || loweredPrompt.contains("经营") || loweredPrompt.contains("revenue")) {
            return "sales";
        }
        if (loweredPrompt.contains("运维") || loweredPrompt.contains("监控") || loweredPrompt.contains("ops")) {
            return "ops";
        }
        return "general";
    }

    private String detectTheme(String loweredPrompt) {
        if (loweredPrompt.contains("浅色") || loweredPrompt.contains("light") || loweredPrompt.contains("明亮")) {
            return "glacier";
        }
        if (loweredPrompt.contains("金属") || loweredPrompt.contains("titanium")) {
            return "titanium";
        }
        return "legacy-dark";
    }

    private String resolveTitle(String prompt, String scene) {
        if (prompt != null && !prompt.isBlank()) {
            String p = prompt.trim();
            if (p.length() <= 28) {
                return p;
            }
        }
        return switch (scene) {
            case "patent" -> "专利运营分析看板";
            case "sales" -> "经营分析看板";
            case "ops" -> "运维监控看板";
            default -> "数据分析大屏";
        };
    }

    private String cardTitle(String scene, int idx) {
        return switch (scene) {
            case "patent" -> switch (idx) {
                case 1 -> "专利总量";
                case 2 -> "本年新增";
                case 3 -> "授权率";
                default -> "高价值占比";
            };
            case "sales" -> switch (idx) {
                case 1 -> "累计收入";
                case 2 -> "新增客户";
                case 3 -> "目标达成";
                default -> "毛利率";
            };
            case "ops" -> switch (idx) {
                case 1 -> "请求总量";
                case 2 -> "告警数";
                case 3 -> "成功率";
                default -> "资源使用率";
            };
            default -> "核心指标" + idx;
        };
    }

    private String chartTitle(String scene, String kind) {
        if ("trend".equals(kind)) {
            return switch (scene) {
                case "patent" -> "月度申请趋势";
                case "sales" -> "月度经营趋势";
                case "ops" -> "系统负载趋势";
                default -> "趋势分析";
            };
        }
        if ("struct".equals(kind)) {
            return switch (scene) {
                case "patent" -> "类型结构分布";
                case "sales" -> "产品结构分布";
                case "ops" -> "服务结构分布";
                default -> "结构分析";
            };
        }
        return "明细列表";
    }

    private ArrayNode barX(String scene) {
        return switch (scene) {
            case "patent" -> arr("发明", "实用新型", "外观", "PCT", "软著");
            case "sales" -> arr("华北", "华东", "华南", "西南", "海外");
            case "ops" -> arr("网关", "采集", "计算", "存储", "应用");
            default -> arr("A", "B", "C", "D", "E");
        };
    }

    private ArrayNode barY(String scene) {
        return switch (scene) {
            case "patent" -> arr(380, 220, 140, 80, 60);
            case "sales" -> arr(420, 360, 300, 240, 180);
            case "ops" -> arr(95, 88, 76, 63, 52);
            default -> arr(120, 100, 90, 70, 50);
        };
    }

    private String firstColumnTitle(String scene) {
        return switch (scene) {
            case "patent" -> "专利维度";
            case "sales" -> "经营维度";
            case "ops" -> "系统维度";
            default -> "维度";
        };
    }

    private String sampleRow(String scene, int idx) {
        return switch (scene) {
            case "patent" -> switch (idx) {
                case 1 -> "发明专利";
                case 2 -> "实用新型";
                case 3 -> "外观设计";
                default -> "PCT";
            };
            case "sales" -> switch (idx) {
                case 1 -> "重点客户";
                case 2 -> "潜力客户";
                case 3 -> "渠道客户";
                default -> "其他";
            };
            case "ops" -> switch (idx) {
                case 1 -> "核心服务";
                case 2 -> "关键任务";
                case 3 -> "边缘节点";
                default -> "基础设施";
            };
            default -> "类别" + idx;
        };
    }

    private ObjectNode component(
            String id,
            String type,
            String name,
            int x,
            int y,
            int width,
            int height,
            int zIndex,
            ObjectNode config) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", id);
        node.put("type", type);
        node.put("name", name);
        node.put("x", x);
        node.put("y", y);
        node.put("width", width);
        node.put("height", height);
        node.put("zIndex", zIndex);
        node.put("locked", false);
        node.put("visible", true);
        node.set("config", config);
        return node;
    }

    private ObjectNode node(Object... kv) {
        ObjectNode node = objectMapper.createObjectNode();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String key = String.valueOf(kv[i]);
            Object value = kv[i + 1];
            if (value == null) {
                node.putNull(key);
            } else if (value instanceof String s) {
                node.put(key, s);
            } else if (value instanceof Integer n) {
                node.put(key, n);
            } else if (value instanceof Long n) {
                node.put(key, n);
            } else if (value instanceof Double n) {
                node.put(key, n);
            } else if (value instanceof Float n) {
                node.put(key, n);
            } else if (value instanceof Boolean b) {
                node.put(key, b);
            } else if (value instanceof ArrayNode an) {
                node.set(key, an);
            } else if (value instanceof ObjectNode on) {
                node.set(key, on);
            } else {
                node.put(key, String.valueOf(value));
            }
        }
        return node;
    }

    private ArrayNode arr(Object... values) {
        ArrayNode arr = objectMapper.createArrayNode();
        for (Object value : values) {
            if (value == null) {
                arr.addNull();
            } else if (value instanceof String s) {
                arr.add(s);
            } else if (value instanceof Integer n) {
                arr.add(n);
            } else if (value instanceof Long n) {
                arr.add(n);
            } else if (value instanceof Double n) {
                arr.add(n);
            } else if (value instanceof Float n) {
                arr.add(n);
            } else if (value instanceof Boolean b) {
                arr.add(b);
            } else if (value instanceof ObjectNode on) {
                arr.add(on);
            } else {
                arr.add(String.valueOf(value));
            }
        }
        return arr;
    }

    private ArrayNode arrObj(ObjectNode... values) {
        ArrayNode arr = objectMapper.createArrayNode();
        for (ObjectNode value : values) {
            arr.add(value);
        }
        return arr;
    }

    private ArrayNode series1(String name, ArrayNode data) {
        return arr(node("name", name, "data", data));
    }

    private ArrayNode series2(String leftName, ArrayNode leftData, String rightName, ArrayNode rightData) {
        return arr(node("name", leftName, "data", leftData), node("name", rightName, "data", rightData));
    }
}
