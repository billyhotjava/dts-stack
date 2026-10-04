package com.yuzhi.dts.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.analytics.domain.AnalyticsScreenTemplate;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenTemplateRepository;
import com.yuzhi.dts.analytics.web.support.PlatformContext;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class MarketplaceService {

    private static final List<ComponentDefinition> BUILTIN_COMPONENTS = List.of(
            new ComponentDefinition("demo-stat-pack:kpi-card-pro", "高级 KPI 卡片", "统计插件包中的 KPI 卡片。", "Demo", "1.0.0", "metric", List.of("kpi", "metric", "plugin")),
            new ComponentDefinition("demo-stat-pack:compact-trend", "紧凑趋势图", "统计插件包中的趋势组件。", "Demo", "1.0.0", "chart", List.of("trend", "chart", "plugin")),
            new ComponentDefinition("demo-stat-pack:table-matrix", "矩阵表格", "统计插件包中的矩阵表格组件。", "Demo", "1.0.0", "other", List.of("table", "matrix", "plugin")));

    private final AnalyticsScreenTemplateRepository screenTemplateRepository;
    private final ObjectMapper objectMapper;
    private final Map<String, InstallRecord> installedComponents = new ConcurrentHashMap<>();

    public MarketplaceService(AnalyticsScreenTemplateRepository screenTemplateRepository, ObjectMapper objectMapper) {
        this.screenTemplateRepository = screenTemplateRepository;
        this.objectMapper = objectMapper;
    }

    public List<ObjectNode> listComponents(String search, String category) {
        return BUILTIN_COMPONENTS.stream()
                .filter(component -> matchesComponent(component, search, category))
                .map(this::toComponentNode)
                .toList();
    }

    public ObjectNode installComponent(String componentId, Long actorId) {
        ComponentDefinition component = BUILTIN_COMPONENTS.stream()
                .filter(item -> item.id().equals(componentId))
                .findFirst()
                .orElse(null);
        if (component == null) {
            return null;
        }
        installedComponents.put(componentId, new InstallRecord(actorId, component.version()));
        return toComponentNode(component);
    }

    public boolean isComponentInstalled(String componentId) {
        if (componentId == null || componentId.isBlank()) {
            return false;
        }
        return installedComponents.containsKey(componentId);
    }

    public List<ObjectNode> listTemplates(AnalyticsUser user, PlatformContext context, String search, String category) {
        return screenTemplateRepository.findAllByArchivedFalseOrderByUpdatedAtDesc().stream()
                .filter(template -> canReadTemplate(template, user, context))
                .filter(template -> template.isListed() || isCreator(template, user))
                .filter(template -> matchesTemplate(template, search, category))
                .map(template -> toTemplateNode(template, user))
                .toList();
    }

    public TemplateInstallResult installTemplate(long templateId, AnalyticsUser user, PlatformContext context) {
        AnalyticsScreenTemplate source = screenTemplateRepository.findByIdAndArchivedFalse(templateId).orElse(null);
        if (source == null) {
            return new TemplateInstallResult(null, false, false);
        }
        if (!canReadTemplate(source, user, context) || (!source.isListed() && !isCreator(source, user) && !user.isSuperuser())) {
            return new TemplateInstallResult(null, false, true);
        }

        AnalyticsScreenTemplate existingClone = screenTemplateRepository.findAllByArchivedFalseOrderByUpdatedAtDesc().stream()
                .filter(template -> !template.isArchived())
                .filter(template -> user.getId().equals(template.getCreatorId()))
                .filter(template -> Long.valueOf(templateId).equals(template.getSourceTemplateId()))
                .findFirst()
                .orElse(null);
        if (existingClone != null) {
            return new TemplateInstallResult(toTemplateNode(existingClone, user), true, false);
        }

        AnalyticsScreenTemplate clone = new AnalyticsScreenTemplate();
        clone.setName(source.getName());
        clone.setDescription(source.getDescription());
        clone.setCategory(source.getCategory());
        clone.setThumbnail(source.getThumbnail());
        clone.setTagsJson(defaultJson(source.getTagsJson(), "[]"));
        clone.setWidth(source.getWidth());
        clone.setHeight(source.getHeight());
        clone.setBackgroundColor(source.getBackgroundColor());
        clone.setBackgroundImage(source.getBackgroundImage());
        clone.setTheme(source.getTheme());
        clone.setComponentsJson(defaultJson(source.getComponentsJson(), "[]"));
        clone.setVariablesJson(defaultJson(source.getVariablesJson(), "[]"));
        clone.setSourceScreenId(source.getSourceScreenId());
        clone.setSourceTemplateId(source.getId());
        clone.setTemplateVersion(1);
        clone.setVisibilityScope("team");
        clone.setOwnerDept(context == null ? null : context.dept());
        clone.setListed(false);
        clone.setThemePackJson(source.getThemePackJson());
        clone.setArchived(false);
        clone.setCreatorId(user.getId());
        clone = screenTemplateRepository.save(clone);
        return new TemplateInstallResult(toTemplateNode(clone, user), true, false);
    }

    private ObjectNode toComponentNode(ComponentDefinition component) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", component.id());
        node.put("name", component.name());
        node.put("description", component.description());
        node.put("author", component.author());
        node.put("version", component.version());
        node.put("category", component.category());
        ArrayNode tags = objectMapper.createArrayNode();
        component.tags().forEach(tags::add);
        node.set("tags", tags);
        node.put("downloads", installedComponents.containsKey(component.id()) ? 1 : 0);
        node.put("installed", installedComponents.containsKey(component.id()));
        return node;
    }

    private ObjectNode toTemplateNode(AnalyticsScreenTemplate template, AnalyticsUser user) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", String.valueOf(template.getId()));
        node.put("name", template.getName());
        node.put("description", template.getDescription());
        node.put("author", template.getCreatorId() == null ? "unknown" : String.valueOf(template.getCreatorId()));
        node.put("version", String.valueOf(template.getTemplateVersion() == null ? 1 : template.getTemplateVersion()));
        node.put("category", template.getCategory());
        node.set("tags", parseTags(template.getTagsJson()));
        node.putPOJO("createdAt", template.getCreatedAt());
        node.putPOJO("updatedAt", template.getUpdatedAt());
        node.put("downloads", installationCountForTemplate(template.getId()));
        node.put("installed", isTemplateInstalledForUser(template, user));
        if (template.getSourceTemplateId() != null) {
            node.putPOJO("sourceTemplateId", template.getSourceTemplateId());
        }
        return node;
    }

    private boolean matchesComponent(ComponentDefinition component, String search, String category) {
        String normalizedCategory = trimToNull(category);
        if (normalizedCategory != null && !normalizedCategory.equalsIgnoreCase(component.category())) {
            return false;
        }
        String normalizedSearch = trimToNull(search);
        if (normalizedSearch == null) {
            return true;
        }
        String haystack = (component.name() + " " + component.description() + " " + component.category() + " " + String.join(" ", component.tags()))
                .toLowerCase(Locale.ROOT);
        return haystack.contains(normalizedSearch.toLowerCase(Locale.ROOT));
    }

    private boolean matchesTemplate(AnalyticsScreenTemplate template, String search, String category) {
        String normalizedCategory = trimToNull(category);
        if (normalizedCategory != null && !normalizedCategory.equalsIgnoreCase(template.getCategory())) {
            return false;
        }
        String normalizedSearch = trimToNull(search);
        if (normalizedSearch == null) {
            return true;
        }
        String haystack = ((template.getName() == null ? "" : template.getName()) + " "
                + (template.getDescription() == null ? "" : template.getDescription()) + " "
                + (template.getCategory() == null ? "" : template.getCategory()) + " "
                + defaultJson(template.getTagsJson(), "[]")).toLowerCase(Locale.ROOT);
        return haystack.contains(normalizedSearch.toLowerCase(Locale.ROOT));
    }

    private boolean canReadTemplate(AnalyticsScreenTemplate template, AnalyticsUser user, PlatformContext context) {
        if (template == null || user == null) {
            return false;
        }
        if (user.isSuperuser()) {
            return true;
        }
        if (isCreator(template, user)) {
            return true;
        }
        String scope = normalizeVisibilityScope(template.getVisibilityScope());
        if ("personal".equals(scope)) {
            return false;
        }
        if ("team".equals(scope)) {
            return sameDept(template.getOwnerDept(), context == null ? null : context.dept());
        }
        return true;
    }

    private boolean isCreator(AnalyticsScreenTemplate template, AnalyticsUser user) {
        return template.getCreatorId() != null && template.getCreatorId().equals(user.getId());
    }

    private boolean isTemplateInstalledForUser(AnalyticsScreenTemplate template, AnalyticsUser user) {
        if (template == null || template.getId() == null || user == null || user.getId() == null) {
            return false;
        }
        if (template.getSourceTemplateId() != null && user.getId().equals(template.getCreatorId())) {
            return true;
        }
        return screenTemplateRepository.findAllByArchivedFalseOrderByUpdatedAtDesc().stream()
                .anyMatch(row -> user.getId().equals(row.getCreatorId()) && template.getId().equals(row.getSourceTemplateId()));
    }

    private int installationCountForTemplate(Long templateId) {
        if (templateId == null) {
            return 0;
        }
        return (int) screenTemplateRepository.findAllByArchivedFalseOrderByUpdatedAtDesc().stream()
                .filter(row -> templateId.equals(row.getSourceTemplateId()))
                .count();
    }

    private ArrayNode parseTags(String tagsJson) {
        if (tagsJson != null && !tagsJson.isBlank()) {
            try {
                if (objectMapper.readTree(tagsJson).isArray()) {
                    return (ArrayNode) objectMapper.readTree(tagsJson);
                }
            } catch (Exception ignored) {
                return objectMapper.createArrayNode();
            }
        }
        return objectMapper.createArrayNode();
    }

    private String defaultJson(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private String normalizeVisibilityScope(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return "team";
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if ("personal".equals(normalized) || "team".equals(normalized) || "global".equals(normalized)) {
            return normalized;
        }
        return "team";
    }

    private boolean sameDept(String expectedDept, String actualDept) {
        String left = trimToNull(expectedDept);
        String right = trimToNull(actualDept);
        if (left == null || right == null) {
            return false;
        }
        return left.equalsIgnoreCase(right);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record ComponentDefinition(
            String id,
            String name,
            String description,
            String author,
            String version,
            String category,
            List<String> tags) {}

    private record InstallRecord(Long actorId, String version) {}

    public record TemplateInstallResult(ObjectNode payload, boolean installed, boolean forbidden) {}
}
