package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsReportTemplate;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsReportTemplateRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.report.FixedReportPageAnchorService;
import com.yuzhi.dts.copilot.analytics.service.report.FixedReportExecutionService;
import com.yuzhi.dts.copilot.analytics.service.report.ReportExecutionPlanService;
import com.yuzhi.dts.copilot.analytics.service.report.ReportExecutionPlanService.ReportExecutionPlan;
import com.yuzhi.dts.copilot.analytics.web.rest.errors.ApiError;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import com.yuzhi.dts.copilot.analytics.web.support.PlatformContext;
import com.yuzhi.dts.copilot.analytics.web.support.RequestContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fixed-reports")
public class FixedReportResource {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AnalyticsSessionService sessionService;
    private final AnalyticsReportTemplateRepository templateRepository;
    private final ReportExecutionPlanService planService;
    private final FixedReportPageAnchorService pageAnchorService;
    private final FixedReportExecutionService executionService;

    public FixedReportResource(
            AnalyticsSessionService sessionService,
            AnalyticsReportTemplateRepository templateRepository,
            ReportExecutionPlanService planService,
            FixedReportPageAnchorService pageAnchorService,
            FixedReportExecutionService executionService) {
        this.sessionService = sessionService;
        this.templateRepository = templateRepository;
        this.planService = planService;
        this.pageAnchorService = pageAnchorService;
        this.executionService = executionService;
    }

    @PostMapping(path = "/{templateCode}/run", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> run(
            @PathVariable("templateCode") String templateCode,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return buildApiError(
                    HttpStatus.UNAUTHORIZED,
                    "SEC_UNAUTHORIZED",
                    "Authentication required",
                    false,
                    request);
        }

        if (body != null && !body.isObject()) {
            return buildApiError(
                    HttpStatus.BAD_REQUEST,
                    "REQ_INVALID_ARGUMENT",
                    "Request body must be an object",
                    false,
                    request);
        }

        Optional<AnalyticsReportTemplate> template = resolveTemplate(templateCode);
        if (template.isEmpty()) {
            return buildApiError(
                    HttpStatus.NOT_FOUND,
                    "REPORT_TEMPLATE_NOT_FOUND",
                    "Fixed report template not found",
                    false,
                    request);
        }

        ResponseEntity<?> permissionDenied = enforcePermission(template.get(), user.get(), request);
        if (permissionDenied != null) {
            return permissionDenied;
        }

        JsonNode parametersNode = body == null ? null : body.path("parameters");
        if (parametersNode != null && !parametersNode.isMissingNode() && !parametersNode.isNull() && !parametersNode.isObject()) {
            return buildApiError(
                    HttpStatus.BAD_REQUEST,
                    "REQ_INVALID_ARGUMENT",
                    "parameters must be an object",
                    false,
                    request);
        }

        String rawParameterSchema = trimToNull(template.get().getParameterSchemaJson());
        Set<String> allowedParameterNames = allowedParameterNames(template.get().getParameterSchemaJson());
        if (parametersNode != null && parametersNode.isObject()) {
            if (rawParameterSchema != null && allowedParameterNames.isEmpty() && parametersNode.size() > 0) {
                return buildApiError(
                        HttpStatus.BAD_REQUEST,
                        "REQ_INVALID_ARGUMENT",
                        "parameterSchemaJson does not define any named params",
                        false,
                        request);
            }
            List<String> unsupported = unsupportedParameterNames(parametersNode, allowedParameterNames);
            if (!unsupported.isEmpty()) {
                return buildApiError(
                        HttpStatus.BAD_REQUEST,
                        "REQ_INVALID_ARGUMENT",
                        "Unsupported parameter(s): " + String.join(", ", unsupported),
                        false,
                        request);
            }
        }

        AnalyticsReportTemplate selectedTemplate = template.get();
        ReportExecutionPlan plan = planService.planFor(selectedTemplate);
        boolean placeholderReviewRequired = isPlaceholderReviewRequired(template.get().getSpecJson());
        @SuppressWarnings("unchecked")
        Map<String, Object> parameterMap = parametersNode == null || parametersNode.isMissingNode()
                ? Map.of()
                : OBJECT_MAPPER.convertValue(parametersNode, Map.class);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("templateCode", safeText(selectedTemplate.getTemplateCode(), templateCode));
        response.put("templateName", selectedTemplate.getName());
        response.put("domain", selectedTemplate.getDomain());
        response.put("freshness", selectedTemplate.getRefreshPolicy());
        response.put("sourceType", selectedTemplate.getDataSourceType());
        response.put("targetObject", selectedTemplate.getTargetObject());
        response.put("route", plan.route().name());
        response.put("adapterKey", plan.adapterKey());
        response.put("rationale", plan.rationale());
        response.put("placeholderReviewRequired", placeholderReviewRequired);
        response.put("supported", !placeholderReviewRequired && plan.route() != ReportExecutionPlanService.Route.EXPLORATION);
        response.put(
                "executionStatus",
                placeholderReviewRequired
                        ? "BACKING_REQUIRED"
                        : plan.route() == ReportExecutionPlanService.Route.EXPLORATION
                                ? "PLANNED"
                                : "READY");
        if (!placeholderReviewRequired && plan.route() != ReportExecutionPlanService.Route.EXPLORATION) {
            try {
                executionService.execute(selectedTemplate, parameterMap, plan).ifPresent(result -> {
                    response.put("executionStatus", "COMPLETED");
                    List<Map<String, Object>> columns = new ArrayList<>();
                    result.columns().forEach(column -> {
                        Map<String, Object> columnRow = new LinkedHashMap<>();
                        columnRow.put("key", column.key());
                        columnRow.put("label", column.label());
                        columnRow.put("baseType", column.baseType());
                        columns.add(columnRow);
                    });
                    Map<String, Object> resultPreview = new LinkedHashMap<>();
                    resultPreview.put("databaseId", result.databaseId());
                    resultPreview.put("databaseName", result.databaseName());
                    resultPreview.put("columns", columns);
                    resultPreview.put("rows", result.rows());
                    resultPreview.put("rowCount", result.rowCount());
                    resultPreview.put("truncated", result.truncated());
                    response.put(
                            "resultPreview",
                            resultPreview);
                });
            } catch (IllegalArgumentException ex) {
                return buildApiError(
                        HttpStatus.BAD_REQUEST,
                        "REQ_INVALID_ARGUMENT",
                        ex.getMessage(),
                        false,
                        request);
            } catch (SQLException ex) {
                return buildApiError(
                        HttpStatus.BAD_GATEWAY,
                        "FIXED_REPORT_EXECUTION_FAILED",
                        ex.getMessage(),
                        true,
                        request);
            }
        }
        pageAnchorService.resolve(selectedTemplate.getTemplateCode()).ifPresent(anchor -> {
            response.put("legacyPageTitle", anchor.title());
            response.put("legacyPagePath", anchor.path());
        });
        response.put("parameters", parameterMap);
        return ResponseEntity.ok(response);
    }

    private Optional<AnalyticsReportTemplate> resolveTemplate(String templateCode) {
        String normalizedTemplateCode = trimToNull(templateCode);
        if (normalizedTemplateCode == null) {
            return Optional.empty();
        }
        return templateRepository.findLatestRunnableTemplateByTemplateCode(
                normalizedTemplateCode.toLowerCase(Locale.ROOT));
    }

    private ResponseEntity<?> enforcePermission(
            AnalyticsReportTemplate template, AnalyticsUser user, HttpServletRequest request) {
        JsonNode policy = parseJson(template.getPermissionPolicyJson());
        if (policy == null || policy.isNull()) {
            return null;
        }

        if (policy.path("superuserOnly").asBoolean(false) && !user.isSuperuser()) {
            return buildApiError(
                    HttpStatus.FORBIDDEN,
                    "SEC_FORBIDDEN",
                    "Fixed report permission denied",
                    false,
                    request);
        }

        JsonNode allowedUsernames = policy.get("allowedUsernames");
        if (allowedUsernames != null && allowedUsernames.isArray()) {
            boolean allowed = false;
            for (JsonNode node : allowedUsernames) {
                String candidate = trimToNull(node.asText(null));
                if (candidate != null && candidate.equalsIgnoreCase(user.getUsername())) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                return buildApiError(
                        HttpStatus.FORBIDDEN,
                        "SEC_FORBIDDEN",
                        "Fixed report permission denied",
                        false,
                        request);
            }
        }

        JsonNode allowedUserIds = policy.get("allowedUserIds");
        if (allowedUserIds != null && allowedUserIds.isArray()) {
            boolean allowed = false;
            for (JsonNode node : allowedUserIds) {
                if (user.getId() != null && node.canConvertToLong() && user.getId().equals(node.longValue())) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                return buildApiError(
                        HttpStatus.FORBIDDEN,
                        "SEC_FORBIDDEN",
                        "Fixed report permission denied",
                        false,
                        request);
            }
        }

        JsonNode requiredRoles = policy.get("roles");
        if (requiredRoles != null && requiredRoles.isArray() && requiredRoles.size() > 0 && !user.isSuperuser()) {
            Set<String> userRoles = resolveRequestRoles(request);
            boolean allowed = false;
            for (JsonNode node : requiredRoles) {
                String requiredRole = trimToNull(node.asText(null));
                if (requiredRole != null && userRoles.contains(requiredRole.toLowerCase(Locale.ROOT))) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                return buildApiError(
                        HttpStatus.FORBIDDEN,
                        "SEC_FORBIDDEN",
                        "Fixed report permission denied",
                        false,
                        request);
            }
        }

        return null;
    }

    private static List<String> unsupportedParameterNames(JsonNode parametersNode, Set<String> allowedParameterNames) {
        List<String> unsupported = new ArrayList<>();
        parametersNode.fieldNames().forEachRemaining(name -> {
            if (!allowedParameterNames.contains(name)) {
                unsupported.add(name);
            }
        });
        return unsupported;
    }

    private static Set<String> allowedParameterNames(String parameterSchemaJson) {
        JsonNode schema = parseJson(parameterSchemaJson);
        if (schema == null || schema.isNull()) {
            return Set.of();
        }
        JsonNode params = schema.get("params");
        if (params == null || !params.isArray()) {
            return Set.of();
        }

        Set<String> names = new LinkedHashSet<>();
        for (JsonNode param : params) {
            String name = trimToNull(param.path("name").asText(null));
            if (name == null && param.isTextual()) {
                name = trimToNull(param.asText());
            }
            if (name != null) {
                names.add(name);
            }
        }
        return names;
    }

    private static JsonNode parseJson(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(text);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isPlaceholderReviewRequired(String specJson) {
        JsonNode spec = parseJson(specJson);
        return spec != null && spec.path("placeholderReviewRequired").asBoolean(false);
    }

    private static Set<String> resolveRequestRoles(HttpServletRequest request) {
        PlatformContext context = PlatformContext.from(request);
        String rawRoles = trimToNull(context.roles());
        if (rawRoles == null) {
            return Set.of();
        }

        List<String> candidates = new ArrayList<>();
        if (rawRoles.startsWith("[") && rawRoles.endsWith("]")) {
            JsonNode parsed = parseJson(rawRoles);
            if (parsed != null && parsed.isArray()) {
                parsed.forEach(node -> {
                    String value = trimToNull(node.asText(null));
                    if (value != null) {
                        candidates.add(value);
                    }
                });
            }
        }

        if (candidates.isEmpty()) {
            for (String token : rawRoles.split("[,，;；|]")) {
                String value = trimToNull(token);
                if (value != null) {
                    candidates.add(value);
                }
            }
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String candidate : candidates) {
            normalized.add(candidate.toLowerCase(Locale.ROOT));
        }
        return normalized;
    }

    private ResponseEntity<ApiError> buildApiError(
            HttpStatus status,
            String code,
            String message,
            boolean retryable,
            HttpServletRequest request) {
        String resolvedMessage = (message == null || message.isBlank()) ? status.getReasonPhrase() : message;
        String requestId = RequestContextUtils.resolveRequestId();
        if (requestId == null || requestId.isBlank()) {
            requestId = MDC.get("requestId");
        }
        ApiError payload = new ApiError(
                OffsetDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                retryable,
                resolvedMessage,
                request.getRequestURI(),
                requestId);
        return ResponseEntity.status(status)
                .body(payload);
    }

    private static String safeText(String value, String fallback) {
        String trimmed = trimToNull(value);
        if (trimmed != null) {
            return trimmed;
        }
        return fallback;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
