package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsAnalysisDraft;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsCard;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsAnalysisDraftRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsCardRepository;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AnalysisDraftService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_SAVED_QUERY = "SAVED_QUERY";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    private final AnalyticsAnalysisDraftRepository draftRepository;
    private final AnalyticsCardRepository cardRepository;
    private final EntityIdGenerator entityIdGenerator;
    private final QueryExecutionFacade queryExecutionFacade;
    private final ObjectMapper objectMapper;

    public AnalysisDraftService(
            AnalyticsAnalysisDraftRepository draftRepository,
            AnalyticsCardRepository cardRepository,
            EntityIdGenerator entityIdGenerator,
            QueryExecutionFacade queryExecutionFacade,
            ObjectMapper objectMapper) {
        this.draftRepository = draftRepository;
        this.cardRepository = cardRepository;
        this.entityIdGenerator = entityIdGenerator;
        this.queryExecutionFacade = queryExecutionFacade;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AnalyticsAnalysisDraft> list(Long userId) {
        return draftRepository.findAllByCreatorIdAndStatusNotOrderByUpdatedAtDesc(userId, STATUS_ARCHIVED);
    }

    @Transactional(readOnly = true)
    public Optional<AnalyticsAnalysisDraft> get(Long userId, long draftId) {
        return draftRepository.findByIdAndCreatorId(draftId, userId);
    }

    public AnalyticsAnalysisDraft create(Long userId, JsonNode body) {
        String question = textOrNull(body, "question");
        String sqlText = textOrNull(body, "sql_text");
        Long databaseId = longOrNull(body, "database_id");
        if (question == null) {
            throw new IllegalArgumentException("question is required");
        }
        if (sqlText == null) {
            throw new IllegalArgumentException("sql_text is required");
        }
        if (databaseId == null || databaseId <= 0) {
            throw new IllegalArgumentException("database_id is required");
        }

        AnalyticsAnalysisDraft draft = new AnalyticsAnalysisDraft();
        draft.setEntityId(entityIdGenerator.newEntityId());
        draft.setTitle(Optional.ofNullable(textOrNull(body, "title")).orElse(defaultTitle(question)));
        draft.setSourceType(Optional.ofNullable(textOrNull(body, "source_type")).orElse("copilot"));
        draft.setSessionId(textOrNull(body, "session_id"));
        draft.setMessageId(textOrNull(body, "message_id"));
        draft.setQuestion(question);
        draft.setDatabaseId(databaseId);
        draft.setSqlText(sqlText);
        draft.setExplanationText(textOrNull(body, "explanation_text"));
        draft.setSuggestedDisplay(Optional.ofNullable(textOrNull(body, "suggested_display")).orElse("table"));
        draft.setResponseKind(textOrNull(body, "response_kind"));
        draft.setDataSurface(textOrNull(body, "data_surface"));
        draft.setQualityLevel(textOrNull(body, "quality_level"));
        draft.setQualityNotes(textOrArrayOrNull(body, "quality_notes"));
        draft.setReportCode(textOrNull(body, "report_code"));
        draft.setStatus(Optional.ofNullable(textOrNull(body, "status")).orElse(STATUS_DRAFT));
        draft.setCreatorId(userId);
        return draftRepository.save(draft);
    }

    public AnalyticsAnalysisDraft archive(Long userId, long draftId) {
        AnalyticsAnalysisDraft draft = requireOwnedDraft(userId, draftId);
        draft.setStatus(STATUS_ARCHIVED);
        return draftRepository.save(draft);
    }

    public void delete(Long userId, long draftId) {
        AnalyticsAnalysisDraft draft = requireOwnedDraft(userId, draftId);
        draftRepository.delete(draft);
    }

    @Transactional(readOnly = true)
    public DatasetQueryService.DatasetResult run(Long userId, long draftId, JsonNode body) throws SQLException {
        AnalyticsAnalysisDraft draft = requireOwnedDraft(userId, draftId);
        QueryExecutionFacade.PreparedQuery prepared = queryExecutionFacade.prepare(
                buildDatasetQuery(draft),
                body,
                null,
                DatasetQueryService.DatasetConstraints.defaults());
        return queryExecutionFacade.executeWithCompliance(prepared);
    }

    public AnalyticsCard saveCard(Long userId, long draftId) {
        AnalyticsAnalysisDraft draft = requireOwnedDraft(userId, draftId);
        if (draft.getLinkedCardId() != null) {
            Optional<AnalyticsCard> existing = cardRepository.findById(draft.getLinkedCardId());
            if (existing.isPresent()) {
                draft.setStatus(STATUS_SAVED_QUERY);
                draftRepository.save(draft);
                return existing.get();
            }
        }

        AnalyticsCard card = new AnalyticsCard();
        card.setEntityId(entityIdGenerator.newEntityId());
        card.setName(Optional.ofNullable(trimToNull(draft.getTitle())).orElse(defaultTitle(draft.getQuestion())));
        card.setDescription(draft.getExplanationText());
        card.setArchived(false);
        card.setCollectionId(null);
        card.setDatabaseId(draft.getDatabaseId());
        card.setDatasetQueryJson(buildDatasetQuery(draft).toString());
        card.setDisplay(Optional.ofNullable(trimToNull(draft.getSuggestedDisplay())).orElse("table"));
        card.setVisualizationSettingsJson("{}");
        card.setCreatorId(userId);
        AnalyticsCard saved = cardRepository.save(card);

        draft.setLinkedCardId(saved.getId());
        draft.setStatus(STATUS_SAVED_QUERY);
        draftRepository.save(draft);
        return saved;
    }

    private AnalyticsAnalysisDraft requireOwnedDraft(Long userId, long draftId) {
        return get(userId, draftId).orElseThrow(() -> new IllegalArgumentException("analysis draft not found"));
    }

    private ObjectNode buildDatasetQuery(AnalyticsAnalysisDraft draft) {
        ObjectNode datasetQuery = objectMapper.createObjectNode();
        datasetQuery.put("type", "native");
        datasetQuery.put("database", draft.getDatabaseId());
        ObjectNode nativeQuery = datasetQuery.putObject("native");
        nativeQuery.put("query", draft.getSqlText());
        return datasetQuery;
    }

    private static String textOrNull(JsonNode body, String fieldName) {
        if (body == null || !body.has(fieldName) || body.get(fieldName).isNull()) {
            return null;
        }
        return trimToNull(body.path(fieldName).asText(null));
    }

    private static Long longOrNull(JsonNode body, String fieldName) {
        if (body == null || !body.has(fieldName) || body.get(fieldName).isNull()) {
            return null;
        }
        JsonNode node = body.get(fieldName);
        return node.canConvertToLong() ? node.asLong() : null;
    }

    private static String textOrArrayOrNull(JsonNode body, String fieldName) {
        if (body == null || !body.has(fieldName) || body.get(fieldName).isNull()) {
            return null;
        }
        JsonNode node = body.get(fieldName);
        if (!node.isArray()) {
            return trimToNull(node.asText(null));
        }
        List<String> values = new java.util.ArrayList<>();
        node.forEach(item -> {
            String text = trimToNull(item.asText(null));
            if (text != null) {
                values.add(text);
            }
        });
        return values.isEmpty() ? null : String.join("；", values);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String defaultTitle(String question) {
        String normalized = Optional.ofNullable(trimToNull(question)).orElse("未命名草稿");
        if (normalized.length() <= 80) {
            return normalized;
        }
        return normalized.substring(0, 80);
    }
}
