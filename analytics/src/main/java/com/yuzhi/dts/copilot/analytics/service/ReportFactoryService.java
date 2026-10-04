package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsExploreSession;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsReportRun;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsReportTemplate;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreen;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreenVersion;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsExploreSessionRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsReportRunRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsReportTemplateRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsScreenRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsScreenVersionRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReportFactoryService {

    private final AnalyticsReportTemplateRepository reportTemplateRepository;
    private final AnalyticsReportRunRepository reportRunRepository;
    private final AnalyticsExploreSessionRepository exploreSessionRepository;
    private final AnalyticsScreenRepository screenRepository;
    private final AnalyticsScreenVersionRepository screenVersionRepository;
    private final ObjectMapper objectMapper;

    public ReportFactoryService(
            AnalyticsReportTemplateRepository reportTemplateRepository,
            AnalyticsReportRunRepository reportRunRepository,
            AnalyticsExploreSessionRepository exploreSessionRepository,
            AnalyticsScreenRepository screenRepository,
            AnalyticsScreenVersionRepository screenVersionRepository,
            ObjectMapper objectMapper) {
        this.reportTemplateRepository = reportTemplateRepository;
        this.reportRunRepository = reportRunRepository;
        this.exploreSessionRepository = exploreSessionRepository;
        this.screenRepository = screenRepository;
        this.screenVersionRepository = screenVersionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTemplates(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return reportTemplateRepository.findAllByArchivedFalseOrderByUpdatedAtDesc(PageRequest.of(0, safeLimit)).stream()
                .map(this::toTemplateResponse)
                .toList();
    }

    public Map<String, Object> saveTemplate(Long templateId, JsonNode body, Long actorUserId) {
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("payload must be object");
        }
        AnalyticsReportTemplate row = templateId == null
                ? new AnalyticsReportTemplate()
                : reportTemplateRepository
                        .findById(templateId)
                        .orElseThrow(() -> new IllegalArgumentException("report template not found"));

        String name = trimToNull(body.path("name").asText(null));
        if (name == null) {
            throw new IllegalArgumentException("name is required");
        }
        row.setName(name);
        row.setDescription(trimToNull(body.path("description").asText(null)));
        if (body.has("spec")) {
            if (!body.path("spec").isObject()) {
                throw new IllegalArgumentException("spec must be object");
            }
            row.setSpecJson(toJson(body.path("spec")));
        } else if (row.getSpecJson() == null) {
            row.setSpecJson(toJson(defaultTemplateSpec()));
        }
        if (body.has("published")) {
            row.setPublished(body.path("published").asBoolean(false));
        }
        if (templateId != null) {
            row.setVersionNo(Math.max(1, row.getVersionNo() + 1));
        } else {
            row.setVersionNo(1);
            row.setCreatorId(actorUserId);
            row.setArchived(false);
        }
        return toTemplateResponse(reportTemplateRepository.save(row));
    }

    public void archiveTemplate(long templateId) {
        AnalyticsReportTemplate row = reportTemplateRepository
                .findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("report template not found"));
        row.setArchived(true);
        reportTemplateRepository.save(row);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRuns(Long actorUserId, boolean superuser, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        List<AnalyticsReportRun> rows = superuser
                ? reportRunRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, safeLimit))
                : reportRunRepository.findAllByCreatorIdOrderByCreatedAtDesc(actorUserId, PageRequest.of(0, safeLimit));
        return rows.stream().map(this::toRunResponse).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getRun(long runId, Long actorUserId, boolean superuser) {
        AnalyticsReportRun run = reportRunRepository
                .findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("report run not found"));
        if (!superuser && (run.getCreatorId() == null || !run.getCreatorId().equals(actorUserId))) {
            throw new IllegalArgumentException("forbidden");
        }
        return toRunResponse(run);
    }

    public Map<String, Object> generate(JsonNode body, Long actorUserId) {
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("payload must be object");
        }
        Long templateId = body.path("templateId").canConvertToLong() ? body.path("templateId").asLong() : null;
        String sourceType = trimToNull(body.path("sourceType").asText(null));
        long sourceId = body.path("sourceId").asLong(0);
        if (sourceType == null || sourceId <= 0) {
            throw new IllegalArgumentException("sourceType/sourceId are required");
        }
        sourceType = sourceType.toLowerCase(Locale.ROOT);
        if (!"session".equals(sourceType) && !"screen".equals(sourceType)) {
            throw new IllegalArgumentException("sourceType must be session or screen");
        }
        String outputFormat = trimToNull(body.path("outputFormat").asText(null));
        if (outputFormat == null) {
            outputFormat = "html";
        }
        outputFormat = outputFormat.toLowerCase(Locale.ROOT);
        if (!"html".equals(outputFormat) && !"markdown".equals(outputFormat)) {
            throw new IllegalArgumentException("outputFormat must be html/markdown");
        }

        AnalyticsReportTemplate template = null;
        if (templateId != null && templateId > 0) {
            template = reportTemplateRepository.findById(templateId).orElse(null);
        }
        JsonNode templateSpec = template == null ? defaultTemplateSpec() : parseJson(template.getSpecJson());
        if (templateSpec == null || !templateSpec.isObject()) {
            templateSpec = defaultTemplateSpec();
        }

        ReportContent content = "session".equals(sourceType)
                ? buildFromSession(sourceId, templateSpec)
                : buildFromScreen(sourceId, templateSpec);

        AnalyticsReportRun run = new AnalyticsReportRun();
        run.setTemplateId(template == null ? null : template.getId());
        run.setSourceType(sourceType);
        run.setSourceId(sourceId);
        run.setStatus("completed");
        run.setOutputFormat(outputFormat);
        run.setSummaryJson(toJson(content.summary()));
        run.setContentMarkdown(content.markdown());
        run.setContentHtml(content.html());
        run.setDistributionJson(toJson(normalizeDistribution(body.path("distribution"))));
        run.setCreatorId(actorUserId);
        run = reportRunRepository.save(run);
        return toRunResponse(run);
    }

    public byte[] exportRun(long runId, String format, Long actorUserId, boolean superuser) {
        AnalyticsReportRun run = reportRunRepository
                .findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("report run not found"));
        if (!superuser && (run.getCreatorId() == null || !run.getCreatorId().equals(actorUserId))) {
            throw new IllegalArgumentException("forbidden");
        }
        String safeFormat = trimToNull(format);
        if (safeFormat == null) {
            safeFormat = "html";
        }
        safeFormat = safeFormat.toLowerCase(Locale.ROOT);
        if ("markdown".equals(safeFormat)) {
            return safeText(run.getContentMarkdown()).getBytes(StandardCharsets.UTF_8);
        }
        return safeText(run.getContentHtml()).getBytes(StandardCharsets.UTF_8);
    }

    private ReportContent buildFromSession(long sessionId, JsonNode templateSpec) {
        AnalyticsExploreSession session = exploreSessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("explore session not found"));

        JsonNode steps = parseJson(session.getStepsJson());
        ArrayNode stepArray = steps != null && steps.isArray() ? (ArrayNode) steps : objectMapper.createArrayNode();
        String title = trimToNull(session.getTitle()) == null ? "分析会话报告" : session.getTitle();
        String question = safeText(session.getQuestionText());
        String conclusion = safeText(session.getConclusionText());

        StringBuilder md = new StringBuilder();
        md.append("# ").append(title).append("\n\n");
        md.append("## 问题背景\n").append(question.isBlank() ? "（未填写）" : question).append("\n\n");
        md.append("## 探索路径\n");
        int index = 1;
        for (JsonNode step : stepArray) {
            String stepTitle = trimToNull(step.path("title").asText(null));
            if (stepTitle == null) {
                stepTitle = "步骤 " + index;
            }
            md.append(index).append(". ").append(stepTitle).append("\n");
            index++;
        }
        if (stepArray.isEmpty()) {
            md.append("1. （暂无步骤）\n");
        }
        md.append("\n## 结论与风险\n");
        md.append(conclusion.isBlank() ? "（未填写）" : conclusion).append("\n\n");
        md.append("## 下一步建议\n");
        md.append("- 复核关键指标口径与过滤条件。\n");
        md.append("- 将关键图表加入大屏或告警面板。\n");
        md.append("- 对异常波动补充根因分析。\n");

        String html = markdownToHtml(md.toString());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("title", title);
        summary.put("sourceType", "session");
        summary.put("sourceId", sessionId);
        summary.put("stepCount", stepArray.size());
        summary.put("templateSections", sectionTitles(templateSpec));
        summary.put("generatedAt", Instant.now());
        return new ReportContent(md.toString(), html, summary);
    }

    private ReportContent buildFromScreen(long screenId, JsonNode templateSpec) {
        AnalyticsScreen screen = screenRepository
                .findById(screenId)
                .orElseThrow(() -> new IllegalArgumentException("screen not found"));
        AnalyticsScreenVersion version = screenVersionRepository
                .findFirstByScreenIdAndCurrentPublishedTrue(screenId)
                .orElse(screenVersionRepository
                        .findFirstByScreenIdOrderByVersionNoDesc(screenId)
                        .orElseThrow(() -> new IllegalArgumentException("screen version not found")));

        JsonNode components = parseJson(version.getComponentsJson());
        ArrayNode componentArray = components != null && components.isArray() ? (ArrayNode) components : objectMapper.createArrayNode();
        String title = trimToNull(version.getName()) == null ? "大屏报告" : version.getName();

        StringBuilder md = new StringBuilder();
        md.append("# ").append(title).append("\n\n");
        md.append("## 大屏概览\n");
        md.append("- 分辨率：").append(version.getWidth()).append(" x ").append(version.getHeight()).append("\n");
        md.append("- 主题：").append(safeText(version.getTheme())).append("\n");
        md.append("- 组件数：").append(componentArray.size()).append("\n\n");
        md.append("## 关键组件\n");
        int index = 1;
        for (JsonNode component : componentArray) {
            if (index > 10) {
                break;
            }
            String type = safeText(component.path("type").asText(""));
            String name = safeText(component.path("name").asText(""));
            md.append(index).append(". ").append(name.isBlank() ? type : name).append(" (").append(type).append(")\n");
            index++;
        }
        if (componentArray.isEmpty()) {
            md.append("1. （暂无组件）\n");
        }
        md.append("\n## 风险提示\n");
        md.append("- 检查关键卡片的数据源可用性。\n");
        md.append("- 确认筛选变量默认值符合发布口径。\n");
        md.append("- 建议增加巡检与告警联动。\n");

        String html = markdownToHtml(md.toString());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("title", title);
        summary.put("sourceType", "screen");
        summary.put("sourceId", screenId);
        summary.put("screenId", screen.getId());
        summary.put("screenVersion", version.getVersionNo());
        summary.put("componentCount", componentArray.size());
        summary.put("templateSections", sectionTitles(templateSpec));
        summary.put("generatedAt", Instant.now());
        return new ReportContent(md.toString(), html, summary);
    }

    private Map<String, Object> toTemplateResponse(AnalyticsReportTemplate row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.getId());
        out.put("name", row.getName());
        out.put("description", row.getDescription());
        out.put("spec", parseJson(row.getSpecJson()));
        out.put("versionNo", row.getVersionNo());
        out.put("published", row.isPublished());
        out.put("archived", row.isArchived());
        out.put("creatorId", row.getCreatorId());
        out.put("createdAt", row.getCreatedAt());
        out.put("updatedAt", row.getUpdatedAt());
        return out;
    }

    private Map<String, Object> toRunResponse(AnalyticsReportRun run) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", run.getId());
        out.put("templateId", run.getTemplateId());
        out.put("sourceType", run.getSourceType());
        out.put("sourceId", run.getSourceId());
        out.put("status", run.getStatus());
        out.put("outputFormat", run.getOutputFormat());
        out.put("summary", parseJson(run.getSummaryJson()));
        out.put("distribution", parseJson(run.getDistributionJson()));
        out.put("creatorId", run.getCreatorId());
        out.put("createdAt", run.getCreatedAt());
        out.put("updatedAt", run.getUpdatedAt());
        return out;
    }

    private JsonNode normalizeDistribution(JsonNode distribution) {
        if (distribution != null && distribution.isObject()) {
            return distribution;
        }
        return objectMapper.createObjectNode();
    }

    private JsonNode defaultTemplateSpec() {
        var node = objectMapper.createObjectNode();
        var sections = node.putArray("sections");
        sections.add("问题背景");
        sections.add("关键洞察");
        sections.add("风险与建议");
        node.put("layout", "default");
        return node;
    }

    private List<String> sectionTitles(JsonNode templateSpec) {
        if (templateSpec == null || !templateSpec.isObject() || !templateSpec.path("sections").isArray()) {
            return List.of();
        }
        List<String> out = new java.util.ArrayList<>();
        for (JsonNode item : templateSpec.path("sections")) {
            String value = trimToNull(item == null ? null : item.asText(null));
            if (value != null) {
                out.add(value);
            }
        }
        return out;
    }

    private JsonNode parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            return node == null ? objectMapper.createObjectNode() : node;
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private String markdownToHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "<html><body></body></html>";
        }
        String[] lines = markdown.split("\\r?\\n");
        StringBuilder html = new StringBuilder();
        html.append("<html><body>");
        // null = no open list; "ol" = inside <ol>; "ul" = inside <ul>
        String currentListTag = null;
        for (String line : lines) {
            String text = line == null ? "" : line.trim();
            if (text.isBlank()) {
                if (currentListTag != null) {
                    html.append("</").append(currentListTag).append(">");
                    currentListTag = null;
                }
                continue;
            }
            if (text.startsWith("# ")) {
                if (currentListTag != null) {
                    html.append("</").append(currentListTag).append(">");
                    currentListTag = null;
                }
                html.append("<h1>").append(escapeHtml(text.substring(2))).append("</h1>");
            } else if (text.startsWith("## ")) {
                if (currentListTag != null) {
                    html.append("</").append(currentListTag).append(">");
                    currentListTag = null;
                }
                html.append("<h2>").append(escapeHtml(text.substring(3))).append("</h2>");
            } else if (text.matches("^\\d+\\.\\s+.*$")) {
                if (!"ol".equals(currentListTag)) {
                    if (currentListTag != null) {
                        html.append("</").append(currentListTag).append(">");
                    }
                    html.append("<ol>");
                    currentListTag = "ol";
                }
                String item = text.replaceFirst("^\\d+\\.\\s+", "");
                html.append("<li>").append(escapeHtml(item)).append("</li>");
            } else if (text.startsWith("- ")) {
                if (!"ul".equals(currentListTag)) {
                    if (currentListTag != null) {
                        html.append("</").append(currentListTag).append(">");
                    }
                    html.append("<ul>");
                    currentListTag = "ul";
                }
                html.append("<li>").append(escapeHtml(text.substring(2))).append("</li>");
            } else {
                if (currentListTag != null) {
                    html.append("</").append(currentListTag).append(">");
                    currentListTag = null;
                }
                html.append("<p>").append(escapeHtml(text)).append("</p>");
            }
        }
        if (currentListTag != null) {
            html.append("</").append(currentListTag).append(">");
        }
        html.append("</body></html>");
        return html.toString();
    }

    private String escapeHtml(String text) {
        String value = text == null ? "" : text;
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private record ReportContent(String markdown, String html, Map<String, Object> summary) {}
}
