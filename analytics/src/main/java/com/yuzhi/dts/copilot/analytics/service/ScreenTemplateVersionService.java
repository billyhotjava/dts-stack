package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreenTemplate;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreenTemplateVersion;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsScreenTemplateVersionRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ScreenTemplateVersionService {

    private final AnalyticsScreenTemplateVersionRepository templateVersionRepository;
    private final ObjectMapper objectMapper;

    public ScreenTemplateVersionService(
            AnalyticsScreenTemplateVersionRepository templateVersionRepository,
            ObjectMapper objectMapper) {
        this.templateVersionRepository = templateVersionRepository;
        this.objectMapper = objectMapper;
    }

    public int appendVersion(AnalyticsScreenTemplate template, Long actorId, String action, Integer restoredFromVersion) {
        if (template == null || template.getId() == null) {
            return template == null || template.getTemplateVersion() == null ? 1 : template.getTemplateVersion();
        }
        int nextVersion = templateVersionRepository.findFirstByTemplateIdOrderByVersionNoDesc(template.getId())
                .map(v -> (v.getVersionNo() == null ? 0 : v.getVersionNo()) + 1)
                .orElse(1);
        AnalyticsScreenTemplateVersion version = new AnalyticsScreenTemplateVersion();
        version.setTemplateId(template.getId());
        version.setVersionNo(nextVersion);
        version.setAction(trimToDefault(action, "update"));
        version.setSnapshotJson(toSnapshotJson(template));
        version.setActorId(actorId);
        version.setRestoredFromVersion(restoredFromVersion);
        templateVersionRepository.save(version);
        return nextVersion;
    }

    @Transactional(readOnly = true)
    public List<AnalyticsScreenTemplateVersion> listVersions(Long templateId, int limit) {
        if (templateId == null) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<AnalyticsScreenTemplateVersion> versions = templateVersionRepository.findAllByTemplateIdOrderByVersionNoDesc(templateId);
        if (versions.size() <= safeLimit) {
            return versions;
        }
        return new ArrayList<>(versions.subList(0, safeLimit));
    }

    private String toSnapshotJson(AnalyticsScreenTemplate template) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", template.getName());
        node.put("description", template.getDescription());
        node.put("category", template.getCategory());
        node.put("thumbnail", template.getThumbnail());
        node.put("tagsJson", template.getTagsJson());
        node.put("width", template.getWidth() == null ? 1920 : template.getWidth());
        node.put("height", template.getHeight() == null ? 1080 : template.getHeight());
        node.put("backgroundColor", template.getBackgroundColor());
        node.put("backgroundImage", template.getBackgroundImage());
        node.put("theme", template.getTheme());
        node.put("componentsJson", template.getComponentsJson());
        node.put("variablesJson", template.getVariablesJson());
        node.put("sourceScreenId", template.getSourceScreenId());
        node.put("sourceTemplateId", template.getSourceTemplateId());
        node.put("visibilityScope", template.getVisibilityScope());
        node.put("ownerDept", template.getOwnerDept());
        node.put("listed", template.isListed());
        node.put("themePackJson", template.getThemePackJson());
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String trimToDefault(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
