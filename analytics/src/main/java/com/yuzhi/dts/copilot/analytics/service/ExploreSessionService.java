package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsExploreSession;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsExploreSessionRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ExploreSessionService {

    public static final String MODEL_EXPLORE_SESSION = "explore_session";

    private final AnalyticsExploreSessionRepository exploreSessionRepository;
    private final PublicLinkService publicLinkService;
    private final ObjectMapper objectMapper;

    public ExploreSessionService(
            AnalyticsExploreSessionRepository exploreSessionRepository,
            PublicLinkService publicLinkService,
            ObjectMapper objectMapper) {
        this.exploreSessionRepository = exploreSessionRepository;
        this.publicLinkService = publicLinkService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(
            Long actorUserId, boolean superuser, boolean includeArchived, String dept, String projectKey, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        List<AnalyticsExploreSession> sessions = superuser
                ? exploreSessionRepository.findAll(PageRequest.of(0, safeLimit)).getContent()
                : exploreSessionRepository.findAllByCreatorIdOrderByUpdatedAtDesc(actorUserId, PageRequest.of(0, safeLimit));
        return sessions.stream()
                .filter(row -> includeArchived || !row.isArchived())
                .filter(row -> dept == null || dept.equalsIgnoreCase(trimToNull(row.getDept())))
                .filter(row -> projectKey == null || projectKey.equalsIgnoreCase(trimToNull(row.getProjectKey())))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(long id, Long actorUserId, boolean superuser) {
        AnalyticsExploreSession row = exploreSessionRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("explore session not found"));
        checkReadable(row, actorUserId, superuser);
        return toResponse(row);
    }

    public Map<String, Object> create(
            JsonNode body, Long actorUserId, String dept, String projectKey) {
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("payload must be object");
        }
        String title = trimToNull(body.path("title").asText(null));
        if (title == null) {
            title = "未命名分析会话";
        }

        AnalyticsExploreSession row = new AnalyticsExploreSession();
        row.setTitle(title);
        row.setQuestionText(trimToNull(body.path("question").asText(null)));
        row.setConclusionText(trimToNull(body.path("conclusion").asText(null)));
        row.setStepsJson(toJson(readSteps(body.path("steps"))));
        row.setTagsJson(toJson(readTags(body.path("tags"))));
        row.setDept(trimToNull(body.path("dept").asText(null)));
        if (row.getDept() == null) {
            row.setDept(trimToNull(dept));
        }
        row.setProjectKey(trimToNull(body.path("projectKey").asText(null)));
        if (row.getProjectKey() == null) {
            row.setProjectKey(trimToNull(projectKey));
        }
        row.setCreatorId(actorUserId);
        row.setArchived(false);
        return toResponse(exploreSessionRepository.save(row));
    }

    public Map<String, Object> update(long id, JsonNode body, Long actorUserId, boolean superuser) {
        AnalyticsExploreSession row = exploreSessionRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("explore session not found"));
        checkWritable(row, actorUserId, superuser);
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("payload must be object");
        }

        if (body.has("title")) {
            String title = trimToNull(body.path("title").asText(null));
            if (title != null) {
                row.setTitle(title);
            }
        }
        if (body.has("question")) {
            row.setQuestionText(trimToNull(body.path("question").asText(null)));
        }
        if (body.has("conclusion")) {
            row.setConclusionText(trimToNull(body.path("conclusion").asText(null)));
        }
        if (body.has("steps")) {
            row.setStepsJson(toJson(readSteps(body.path("steps"))));
        }
        if (body.has("tags")) {
            row.setTagsJson(toJson(readTags(body.path("tags"))));
        }
        if (body.has("projectKey")) {
            row.setProjectKey(trimToNull(body.path("projectKey").asText(null)));
        }
        if (body.has("archived")) {
            row.setArchived(body.path("archived").asBoolean(false));
        }
        return toResponse(exploreSessionRepository.save(row));
    }

    public Map<String, Object> appendStep(long id, JsonNode body, Long actorUserId, boolean superuser) {
        AnalyticsExploreSession row = exploreSessionRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("explore session not found"));
        checkWritable(row, actorUserId, superuser);
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("step payload must be object");
        }

        ArrayNode steps = parseArray(row.getStepsJson());
        ObjectNode step = objectMapper.createObjectNode();
        step.put("at", Instant.now().toString());
        step.put("title", trimToNull(body.path("title").asText(null)) == null ? "未命名步骤" : body.path("title").asText());
        step.put("type", trimToNull(body.path("type").asText(null)) == null ? "action" : body.path("type").asText());
        if (body.has("params")) {
            step.set("params", body.path("params"));
        } else {
            step.set("params", objectMapper.createObjectNode());
        }
        if (body.has("snapshotRef")) {
            step.set("snapshotRef", body.path("snapshotRef"));
        }
        steps.add(step);
        row.setStepsJson(toJson(steps));
        return toResponse(exploreSessionRepository.save(row));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> replayStep(long id, int stepIndex, Long actorUserId, boolean superuser) {
        AnalyticsExploreSession row = exploreSessionRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("explore session not found"));
        checkReadable(row, actorUserId, superuser);
        ArrayNode steps = parseArray(row.getStepsJson());
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("explore session has no steps");
        }
        int safeIndex = Math.max(0, Math.min(stepIndex, steps.size() - 1));
        JsonNode step = steps.get(safeIndex);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sessionId", row.getId());
        out.put("stepIndex", safeIndex);
        out.put("step", step);
        out.put("replayStatus", "ok");
        out.put("message", "step replay payload ready");
        return out;
    }

    public Map<String, Object> archive(long id, Long actorUserId, boolean superuser) {
        AnalyticsExploreSession row = exploreSessionRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("explore session not found"));
        checkWritable(row, actorUserId, superuser);
        row.setArchived(true);
        return toResponse(exploreSessionRepository.save(row));
    }

    public Map<String, Object> cloneSession(long id, Long actorUserId, boolean superuser) {
        AnalyticsExploreSession source = exploreSessionRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("explore session not found"));
        checkReadable(source, actorUserId, superuser);

        AnalyticsExploreSession copy = new AnalyticsExploreSession();
        copy.setTitle(source.getTitle() + " (副本)");
        copy.setQuestionText(source.getQuestionText());
        copy.setStepsJson(source.getStepsJson());
        copy.setConclusionText(source.getConclusionText());
        copy.setTagsJson(source.getTagsJson());
        copy.setProjectKey(source.getProjectKey());
        copy.setDept(source.getDept());
        copy.setCreatorId(actorUserId);
        copy.setArchived(false);
        return toResponse(exploreSessionRepository.save(copy));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getByPublicUuid(String uuid) {
        String safeUuid = trimToNull(uuid);
        if (safeUuid == null) {
            throw new IllegalArgumentException("public uuid is required");
        }
        var link = publicLinkService
                .findByPublicUuid(safeUuid)
                .orElseThrow(() -> new IllegalArgumentException("public session link not found"));
        if (!MODEL_EXPLORE_SESSION.equals(link.getModel()) || link.isDisabled()) {
            throw new IllegalArgumentException("public session link is invalid");
        }
        AnalyticsExploreSession row = exploreSessionRepository
                .findById(link.getModelId())
                .orElseThrow(() -> new IllegalArgumentException("explore session not found"));
        return toResponse(row);
    }

    public String getOrCreatePublicLink(long id, Long actorUserId, boolean superuser, String dept, String classification) {
        AnalyticsExploreSession row = exploreSessionRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("explore session not found"));
        checkWritable(row, actorUserId, superuser);
        return publicLinkService.getOrCreateScoped(MODEL_EXPLORE_SESSION, id, actorUserId, dept, classification);
    }

    public void deletePublicLink(long id, Long actorUserId, boolean superuser) {
        AnalyticsExploreSession row = exploreSessionRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("explore session not found"));
        checkWritable(row, actorUserId, superuser);
        publicLinkService.delete(MODEL_EXPLORE_SESSION, id);
    }

    private void checkReadable(AnalyticsExploreSession row, Long actorUserId, boolean superuser) {
        if (superuser) {
            return;
        }
        if (row.getCreatorId() == null || actorUserId == null || !row.getCreatorId().equals(actorUserId)) {
            throw new IllegalArgumentException("forbidden");
        }
    }

    private void checkWritable(AnalyticsExploreSession row, Long actorUserId, boolean superuser) {
        checkReadable(row, actorUserId, superuser);
    }

    private Map<String, Object> toResponse(AnalyticsExploreSession row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.getId());
        out.put("title", row.getTitle());
        out.put("question", row.getQuestionText());
        out.put("steps", parseArray(row.getStepsJson()));
        out.put("stepCount", parseArray(row.getStepsJson()).size());
        out.put("conclusion", row.getConclusionText());
        out.put("tags", parseArray(row.getTagsJson()));
        out.put("projectKey", row.getProjectKey());
        out.put("dept", row.getDept());
        out.put("creatorId", row.getCreatorId());
        out.put("archived", row.isArchived());
        out.put("createdAt", row.getCreatedAt());
        out.put("updatedAt", row.getUpdatedAt());
        out.put("publicUuid", publicLinkService.publicUuidFor(MODEL_EXPLORE_SESSION, row.getId()).orElse(null));
        return out;
    }

    private ArrayNode readSteps(JsonNode node) {
        if (node != null && node.isArray()) {
            return (ArrayNode) node.deepCopy();
        }
        return objectMapper.createArrayNode();
    }

    private ArrayNode readTags(JsonNode node) {
        if (node != null && node.isArray()) {
            ArrayNode tags = objectMapper.createArrayNode();
            for (JsonNode item : node) {
                String value = trimToNull(item == null ? null : item.asText(null));
                if (value != null) {
                    tags.add(value);
                }
            }
            return tags;
        }
        return objectMapper.createArrayNode();
    }

    private ArrayNode parseArray(String raw) {
        if (raw == null || raw.isBlank()) {
            return objectMapper.createArrayNode();
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node != null && node.isArray()) {
                return (ArrayNode) node;
            }
        } catch (Exception e) {
            // ignore parse error
        }
        return objectMapper.createArrayNode();
    }

    private String toJson(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
