package com.yuzhi.dts.platform.service.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.ClassificationUtils;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.infra.DefaultDestinationSyncService;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/** Central object, department, active-state, and clearance decisions for ingestion proxies. */
@Service
public class IngestionAccessDecisionService {

    private static final String ACTIVE = "ACTIVE";
    private static final int SCOPE_SCAN_PAGE_SIZE = 500;
    private static final int MAX_SCOPE_SCAN_ITEMS = 50_000;
    private static final Set<String> API_TEST_ROOT_FIELDS = Set.of("dataSourceId", "resource");
    private static final Set<String> API_TEST_RESOURCE_FIELDS = Set.of(
        "resourceId",
        "displayName",
        "path",
        "resourcePath",
        "recordPath",
        "method",
        "query",
        "pagination",
        "cursor",
        "targetTable",
        "landing",
        "schemaSnapshot"
    );
    private static final Set<String> API_TEST_FORBIDDEN_KEYS = Set.of(
        "baseurl",
        "url",
        "uri",
        "host",
        "hostname",
        "endpoint",
        "headers",
        "defaultheaders",
        "authorization",
        "auth",
        "secrets",
        "requestpolicy",
        "allowhttp",
        "allowedhosts"
    );
    private static final Pattern CONTROL_CHARACTER = Pattern.compile("[\\x00-\\x1f\\x7f]");
    private static final Pattern TEXT_KEY_VALUE = Pattern.compile(
        "(?i)([\"']?([a-z][a-z0-9_.-]{0,63})[\"']?\\s*[:=]\\s*)" +
        "(\"(?:\\\\.|[^\"\\\\])*(?:\"|$)|'(?:\\\\.|[^'\\\\])*(?:'|$)|(?:basic|bearer)\\s+[^\\s,;&\\[\\{\\]}]+|[^\\s,;&\\[\\{\\]}]+)"
    );
    private static final Pattern INTERNAL_ABSOLUTE_PATH_TEXT = Pattern.compile(
        "(?i)(?<![a-z0-9])(?:file:)?/(?:opt|srv|tmp|decrypted|var/lib|home|root)(?:/[^\\s,;\\]}\"']*)?"
    );
    private static final String REDACTED_VALUE = "[REDACTED]";
    private static final String REDACTED_PATH = "[REDACTED_PATH]";

    private final InfraDataSourceRepository dataSourceRepository;
    private final IngestionServiceClient ingestionClient;
    private final ClassificationUtils classificationUtils;
    private final ObjectMapper objectMapper;

    public IngestionAccessDecisionService(
        InfraDataSourceRepository dataSourceRepository,
        IngestionServiceClient ingestionClient,
        ClassificationUtils classificationUtils,
        ObjectMapper objectMapper
    ) {
        this.dataSourceRepository = dataSourceRepository;
        this.ingestionClient = ingestionClient;
        this.classificationUtils = classificationUtils;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> requireTaskAccess(Long taskId, boolean requireExplicitClearance) {
        if (taskId == null || taskId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务标识无效");
        }
        ApiResponse<Map<String, Object>> response = ingestionClient.getTask(taskId);
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "接入服务暂不可用");
        }
        if (response.getStatus() < 200 || response.getStatus() >= 300 || response.getData() == null) {
            HttpStatus status = HttpStatus.resolve(response.getStatus());
            throw new ResponseStatusException(status == null ? HttpStatus.BAD_GATEWAY : status, "无法读取接入任务");
        }
        Map<String, Object> canonicalTask = canonicalizeTaskPayloadIdentifiers(response.getData());
        response.setData(canonicalTask);
        requireTaskPayloadAccess(canonicalTask, requireExplicitClearance);
        return canonicalTask;
    }

    public Map<String, Object> requireTaskAuthorizationAccess(Long taskId, boolean requireExplicitClearance) {
        if (taskId == null || taskId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务标识无效");
        }
        ApiResponse<Map<String, Object>> response = ingestionClient.getTaskAccessMetadata(taskId);
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "接入服务暂不可用");
        }
        if (response.getStatus() < 200 || response.getStatus() >= 300 || response.getData() == null) {
            HttpStatus status = HttpStatus.resolve(response.getStatus());
            throw new ResponseStatusException(status == null ? HttpStatus.BAD_GATEWAY : status, "无法读取接入任务授权元数据");
        }
        Map<String, Object> canonicalTask = canonicalizeTaskPayloadIdentifiers(response.getData());
        requireTaskPayloadAccess(canonicalTask, requireExplicitClearance);
        return canonicalTask;
    }

    public void requireTaskPayloadAccess(Map<String, Object> task, boolean requireExplicitClearance) {
        requirePayloadAccess(task, requireExplicitClearance, HttpStatus.FORBIDDEN);
    }

    public void requireCreateOrUpdateAccess(Map<String, Object> payload, boolean requireExplicitClearance) {
        requirePayloadAccess(payload, requireExplicitClearance, HttpStatus.BAD_REQUEST);
    }

    /**
     * Resolve every accepted source/target data-source alias to one UUID. Conflicting or malformed
     * non-empty aliases fail closed before authorization, sealing, or downstream forwarding.
     */
    public Map<String, Object> canonicalizeTaskPayloadIdentifiers(Map<String, Object> payload) {
        Map<String, Object> canonical = deepMutableMap(payload);
        UUID sourceId = resolveCanonicalSourceId(canonical);
        UUID targetId = resolveCanonicalTargetId(canonical);
        if (sourceId != null) {
            writeCanonicalSourceId(canonical, sourceId.toString());
        }
        if (targetId != null) {
            writeCanonicalTargetId(canonical, targetId.toString());
        }
        return canonical;
    }

    public void requireDiscoveryAccess(Map<String, Object> payload) {
        UUID sourceId = sourceDataSourceId(payload);
        if (sourceId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择受管数据源连接");
        }
        requireDataSourceAccess(sourceId, true);
    }

    public InfraDataSource requireDataSourceAccess(UUID dataSourceId, boolean requireExplicitClearance) {
        if (dataSourceId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数据源标识无效");
        }
        InfraDataSource dataSource = dataSourceRepository
            .findById(dataSourceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据源不存在"));
        assertActiveAndVisible(dataSource);
        if (requireExplicitClearance) {
            assertExplicitClearance(dataSource);
        }
        return dataSource;
    }

    public ApiResponse<Map<String, Object>> listVisibleTasks(Map<String, Object> query) {
        Map<String, Object> safeQuery = query == null ? Map.of() : new LinkedHashMap<>(query);
        int requestedPage = queryInteger(safeQuery.get("page"), 0, 0, Integer.MAX_VALUE, "page");
        int requestedSize = queryInteger(safeQuery.get("size"), 20, 1, 500, "size");
        Map<String, Object> scanQuery = new LinkedHashMap<>(safeQuery);
        scanQuery.remove("page");
        scanQuery.remove("size");
        scanQuery.put("size", SCOPE_SCAN_PAGE_SIZE);

        List<Map<String, Object>> tasks = new ArrayList<>();
        Map<String, Object> pageTemplate = null;
        ApiResponse<Map<String, Object>> firstResponse = null;
        int scanPage = 0;
        long totalElements = Long.MAX_VALUE;
        while (tasks.size() < totalElements) {
            scanQuery.put("page", scanPage);
            ApiResponse<Map<String, Object>> response = ingestionClient.listTasks(scanQuery);
            if (response == null || response.getStatus() < 200 || response.getStatus() >= 300 || response.getData() == null) {
                return response;
            }
            if (firstResponse == null) {
                firstResponse = response;
                pageTemplate = new LinkedHashMap<>(response.getData());
            }
            List<Map<String, Object>> content = pageContent(response.getData())
                .stream()
                .map(this::canonicalizeTaskPayloadIdentifiers)
                .toList();
            tasks.addAll(content);
            totalElements = pageTotalElements(response.getData(), tasks.size(), content.size());
            if (tasks.size() > MAX_SCOPE_SCAN_ITEMS || totalElements > MAX_SCOPE_SCAN_ITEMS) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "接入任务范围过大，无法安全完成部门分页");
            }
            if (content.isEmpty() || content.size() < SCOPE_SCAN_PAGE_SIZE) {
                break;
            }
            scanPage++;
        }

        TaskVisibilityScope visibilityScope = visibleActiveDataSourceIds();
        List<Map<String, Object>> visible = tasks
            .stream()
            .filter(task -> taskVisible(task, visibilityScope))
            .filter(this::taskClearanceVisible)
            .toList();
        long requestedOffset = (long) requestedPage * requestedSize;
        int from = requestedOffset >= visible.size() ? visible.size() : (int) requestedOffset;
        int to = Math.min(visible.size(), from + requestedSize);
        List<Map<String, Object>> selected = List.copyOf(visible.subList(from, to));
        Map<String, Object> page = pageTemplate == null ? new LinkedHashMap<>() : pageTemplate;
        boolean usesItems = page.containsKey("items") && !page.containsKey("content");
        page.put(usesItems ? "items" : "content", selected);
        page.put("number", requestedPage);
        page.put("size", requestedSize);
        page.put("numberOfElements", selected.size());
        page.put("totalElements", visible.size());
        int totalPages = visible.isEmpty() ? 0 : (int) Math.ceil((double) visible.size() / requestedSize);
        page.put("totalPages", totalPages);
        page.put("first", requestedPage == 0);
        page.put("last", requestedPage >= Math.max(0, totalPages - 1));
        page.put("empty", selected.isEmpty());
        return new ApiResponse<>(firstResponse.getStatus(), firstResponse.getMessage(), firstResponse.getCode(), page);
    }

    public Map<String, Object> normalizeApiConnectionTest(Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        for (String key : safe.keySet()) {
            if (!API_TEST_ROOT_FIELDS.contains(key)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "API_TEST_FIELD_FORBIDDEN: " + key);
            }
        }
        UUID dataSourceId = parseUuid(safe.get("dataSourceId"));
        InfraDataSource dataSource = requireDataSourceAccess(dataSourceId, true);
        if (!isApiDataSource(dataSource)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择 API 类型的数据源");
        }
        Map<String, Object> resource = mapValue(safe.get("resource"));
        if (safe.containsKey("resource") && !(safe.get("resource") instanceof Map<?, ?>)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resource 必须是对象");
        }
        for (String key : resource.keySet()) {
            if (!API_TEST_RESOURCE_FIELDS.contains(key)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "API_TEST_RESOURCE_FIELD_FORBIDDEN: " + key);
            }
        }
        rejectApiNetworkOverrides(resource);
        String path = text(resource.get("path"));
        String resourcePath = text(resource.get("resourcePath"));
        if (StringUtils.hasText(path) && StringUtils.hasText(resourcePath) && !path.equals(resourcePath)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "API_TEST_RESOURCE_PATH_CONFLICT");
        }
        String resolvedPath = StringUtils.hasText(path) ? path : resourcePath;
        if (StringUtils.hasText(resolvedPath)) {
            validateRelativePath(resolvedPath);
            resource.put("path", resolvedPath);
        }
        resource.remove("resourcePath");
        String method = text(resource.get("method"));
        if (StringUtils.hasText(method) && !"GET".equalsIgnoreCase(method)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "API_TEST_METHOD_FORBIDDEN: 连通测试仅允许 GET");
        }
        if (!resource.isEmpty()) {
            resource.put("method", "GET");
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("dataSourceId", dataSourceId.toString());
        if (!resource.isEmpty()) {
            normalized.put("resource", Map.copyOf(resource));
        }
        return Map.copyOf(normalized);
    }

    public Object removeInternalFilePaths(Object value) {
        return sanitizeValue(value, false, null);
    }

    public Object retainExplicitSecrets(Object mergedValue, Object explicitValue) {
        if (mergedValue instanceof Map<?, ?> mergedMap) {
            Map<String, Object> retained = new LinkedHashMap<>();
            mergedMap.forEach((key, item) -> {
                String name = String.valueOf(key);
                Map.Entry<?, ?> explicitEntry = findNormalizedEntry(explicitValue, name);
                if (isSensitiveResponseField(name)) {
                    if (explicitEntry != null && hasExplicitSecretValue(explicitEntry.getValue())) {
                        retained.put(name, copyExplicitValue(explicitEntry.getValue()));
                    }
                    return;
                }
                retained.put(
                    name,
                    retainExplicitSecrets(item, explicitEntry == null ? null : explicitEntry.getValue())
                );
            });
            return retained;
        }
        if (mergedValue instanceof Iterable<?> mergedItems) {
            List<?> explicitItems = explicitValue instanceof List<?> list ? list : List.of();
            List<Object> retained = new ArrayList<>();
            int index = 0;
            for (Object item : mergedItems) {
                Object explicitItem = index < explicitItems.size() ? explicitItems.get(index) : null;
                retained.add(retainExplicitSecrets(item, explicitItem));
                index++;
            }
            return retained;
        }
        return mergedValue;
    }

    private Object sanitizeValue(Object value, boolean apiResourceContext, String sourceFieldName) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                String name = String.valueOf(key);
                if (!isSensitiveResponseField(name) && !isInternalFilePathField(name, item, apiResourceContext)) {
                    sanitized.put(name, sanitizeValue(item, isApiResourceField(name), name));
                }
            });
            return sanitized;
        }
        if (value instanceof Iterable<?> values) {
            List<Object> sanitized = new ArrayList<>();
            values.forEach(item -> sanitized.add(sanitizeValue(item, apiResourceContext, sourceFieldName)));
            return sanitized;
        }
        if (value != null && value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            List<Object> sanitized = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                sanitized.add(sanitizeValue(java.lang.reflect.Array.get(value, index), apiResourceContext, sourceFieldName));
            }
            return sanitized;
        }
        if (value instanceof String text) {
            return sanitizeString(text, apiResourceContext, sourceFieldName);
        }
        return value;
    }

    private String sanitizeString(String value, boolean apiResourceContext, String sourceFieldName) {
        String trimmed = value.trim();
        boolean jsonShaped = trimmed.startsWith("{") || trimmed.startsWith("[");
        if (jsonShaped) {
            try {
                Object parsed = objectMapper.readValue(trimmed, Object.class);
                if (parsed instanceof Map<?, ?> || parsed instanceof Iterable<?>) {
                    Object sanitized = sanitizeValue(parsed, apiResourceContext, sourceFieldName);
                    if (sanitized.equals(parsed)) {
                        return value;
                    }
                    return objectMapper.writeValueAsString(sanitized);
                }
            } catch (JsonProcessingException ignored) {
                // Malformed audit JSON must still pass through pattern-based fail-closed redaction below.
            }
        }
        return sanitizePlainText(value, jsonShaped || isAuditTextField(sourceFieldName));
    }

    private String sanitizePlainText(String value, boolean redactStandaloneInternalPaths) {
        Matcher matcher = TEXT_KEY_VALUE.matcher(value);
        StringBuilder sanitized = new StringBuilder();
        boolean changed = false;
        while (matcher.find()) {
            String key = matcher.group(2);
            String rawValue = matcher.group(3);
            boolean sensitive = isSensitiveResponseField(key);
            boolean internalPath = !"path".equals(normalizeFieldName(key)) &&
                isInternalFilePathField(key, unquoteTextValue(rawValue), false);
            if (!sensitive && !internalPath) {
                continue;
            }
            String replacement = matcher.group(1) + quotedRedaction(rawValue, internalPath ? REDACTED_PATH : REDACTED_VALUE);
            matcher.appendReplacement(sanitized, Matcher.quoteReplacement(replacement));
            changed = true;
        }
        if (changed) {
            matcher.appendTail(sanitized);
        }
        String result = changed ? sanitized.toString() : value;
        if (redactStandaloneInternalPaths) {
            result = INTERNAL_ABSOLUTE_PATH_TEXT.matcher(result).replaceAll(REDACTED_PATH);
        }
        return result;
    }

    private String unquoteTextValue(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '\"' && last == '\"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private String quotedRedaction(String rawValue, String redaction) {
        if (rawValue.startsWith("\"")) {
            return "\"" + redaction + "\"";
        }
        if (rawValue.startsWith("'")) {
            return "'" + redaction + "'";
        }
        return redaction;
    }

    private boolean isAuditTextField(String fieldName) {
        String normalized = normalizeFieldName(fieldName);
        return normalized.endsWith("json") || "errormessage".equals(normalized);
    }

    @SuppressWarnings("unchecked")
    public <T> ApiResponse<T> sanitizeResponse(ApiResponse<T> response) {
        if (response != null && response.getData() != null) {
            response.setData((T) removeInternalFilePaths(response.getData()));
        }
        return response;
    }

    public void requireRollbackAccess(RollbackCommand command, boolean requireExplicitClearance) {
        if (command == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "回退请求不能为空");
        }
        if ("task".equals(command.scope())) {
            requireTaskAccess(command.taskId(), requireExplicitClearance);
            return;
        }
        requireDataSourceAccess(command.dataSourceId(), requireExplicitClearance);
    }

    public void requireRollbackAuditAccess(Long taskId, UUID dataSourceId) {
        boolean scoped = false;
        if (dataSourceId != null) {
            requireDataSourceAccess(dataSourceId, true);
            scoped = true;
        }
        if (taskId != null) {
            requireTaskAccess(taskId, false);
            scoped = true;
        }
        if (scoped) {
            return;
        }
        requireInstituteScope("查看全局回退审计");
    }

    public void requireAggregateScope(Map<String, ?> params, String operation) {
        if (params != null && params.containsKey("sourceDataSourceId")) {
            Object rawSourceId = params.get("sourceDataSourceId");
            if (!StringUtils.hasText(text(rawSourceId))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sourceDataSourceId 不能为空");
            }
            UUID sourceId = parseUuid(rawSourceId);
            if (sourceId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sourceDataSourceId 必须是 UUID");
            }
            requireDataSourceAccess(sourceId, true);
            return;
        }
        requireInstituteScope(operation);
    }

    public void requireInstituteScope(String operation) {
        if (!isInstituteMaintainer()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, operation + "仅允许院级管理员执行");
        }
    }

    private void requirePayloadAccess(Map<String, Object> payload, boolean requireExplicitClearance, HttpStatus missingStatus) {
        boolean fileTask = isFileTask(payload);
        UUID sourceId = sourceDataSourceId(payload);
        UUID targetId = targetDataSourceId(payload);
        if (!fileTask && sourceId == null) {
            throw new ResponseStatusException(missingStatus, "接入任务缺少受管源数据源");
        }
        if (targetId == null) {
            throw new ResponseStatusException(missingStatus, "接入任务缺少受管目标数据源");
        }
        if (fileTask) {
            assertFileTaskClearance(payload);
            if (sourceId != null) {
                requireDataSourceAccess(sourceId, true);
            }
        } else if (sourceId != null) {
            requireDataSourceAccess(sourceId, true);
        }
        requireDataSourceAccess(targetId, false);
    }

    private void assertFileTaskClearance(Map<String, Object> task) {
        List<Map<String, Object>> seals = classificationSeals(task);
        List<String> candidateLevels = new ArrayList<>();
        for (Map<String, Object> seal : seals) {
            String effective = strictOptionalDataLevel(seal.get("effectiveLevel"));
            String fileFloor = strictOptionalDataLevel(seal.get("fileFloor"));
            if (
                StringUtils.hasText(effective) &&
                StringUtils.hasText(fileFloor) &&
                !SecurityLevelCatalog.isDataAtLeast(effective, fileFloor)
            ) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "文件密级封存不一致");
            }
            if (StringUtils.hasText(effective)) {
                candidateLevels.add(effective);
            }
            if (StringUtils.hasText(fileFloor)) {
                candidateLevels.add(fileFloor);
            }
        }
        String requiredLevel;
        try {
            requiredLevel = SecurityLevelCatalog.maxDataCode(candidateLevels);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "文件密级封存无效");
        }
        if (!StringUtils.hasText(requiredLevel)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "文件任务缺少有效密级封存");
        }
        String userLevel = classificationUtils
            .getCurrentUserExplicitMaxLevel()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "当前用户未配置显式密级"));
        if (!SecurityLevelCatalog.isDataAtLeast(userLevel, requiredLevel)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "文件任务密级超出当前用户密级");
        }
    }

    private String strictOptionalDataLevel(Object value) {
        if (!StringUtils.hasText(text(value))) {
            return null;
        }
        try {
            return SecurityLevelCatalog.requireDataLevel(value).code();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "文件密级封存无效");
        }
    }

    private void assertActiveAndVisible(InfraDataSource dataSource) {
        if (!ACTIVE.equalsIgnoreCase(text(dataSource.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据源未处于 ACTIVE 状态");
        }
        if (isInstituteMaintainer()) {
            return;
        }
        String owner = DepartmentUtils.normalize(dataSource.getOwnerDept());
        if (!StringUtils.hasText(owner)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "数据源未配置归属部门，禁止普通用户访问");
        }
        String department = DepartmentUtils.normalize(resolveTokenDepartment());
        if (!StringUtils.hasText(department) || !owner.equalsIgnoreCase(department)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问其他部门的数据源");
        }
    }

    private void assertExplicitClearance(InfraDataSource dataSource) {
        String userLevel = classificationUtils
            .getCurrentUserExplicitMaxLevel()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "当前用户未配置显式密级"));
        Object rawLevel = dataSourceProperties(dataSource).get("classification");
        String dataLevel;
        try {
            dataLevel = SecurityLevelCatalog.requireDataLevel(rawLevel).code();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "数据源未配置有效密级");
        }
        if (!SecurityLevelCatalog.isDataAtLeast(userLevel, dataLevel)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "数据源密级超出当前用户密级");
        }
    }

    private TaskVisibilityScope visibleActiveDataSourceIds() {
        boolean instituteMaintainer = isInstituteMaintainer();
        String department = DepartmentUtils.normalize(resolveTokenDepartment());
        String userLevel = classificationUtils.getCurrentUserExplicitMaxLevel().orElse(null);
        Set<UUID> visibleIds = new LinkedHashSet<>();
        Set<UUID> clearedSourceIds = new LinkedHashSet<>();
        List<InfraDataSource> sources = dataSourceRepository.findAll();
        if (sources == null) {
            return new TaskVisibilityScope(Set.of(), Set.of());
        }
        for (InfraDataSource source : sources) {
            if (source == null || source.getId() == null || !ACTIVE.equalsIgnoreCase(text(source.getStatus()))) {
                continue;
            }
            String owner = DepartmentUtils.normalize(source.getOwnerDept());
            if (!instituteMaintainer &&
                (!StringUtils.hasText(owner) || !StringUtils.hasText(department) || !owner.equalsIgnoreCase(department))) {
                continue;
            }
            visibleIds.add(source.getId());
            if (hasSourceClearance(source, userLevel)) {
                clearedSourceIds.add(source.getId());
            }
        }
        return new TaskVisibilityScope(Set.copyOf(visibleIds), Set.copyOf(clearedSourceIds));
    }

    private boolean hasSourceClearance(InfraDataSource dataSource, String userLevel) {
        if (!StringUtils.hasText(userLevel)) {
            return false;
        }
        try {
            String sourceLevel = SecurityLevelCatalog
                .requireDataLevel(dataSourceProperties(dataSource).get("classification"))
                .code();
            return SecurityLevelCatalog.isDataAtLeast(userLevel, sourceLevel);
        } catch (IllegalArgumentException | ResponseStatusException ex) {
            return false;
        }
    }

    private boolean taskVisible(Map<String, Object> task, TaskVisibilityScope visibilityScope) {
        if (task == null || task.isEmpty()) {
            return false;
        }
        UUID sourceId = sourceDataSourceId(task);
        UUID targetId = targetDataSourceId(task);
        if (targetId == null || !visibilityScope.visibleDataSourceIds().contains(targetId)) {
            return false;
        }
        return isFileTask(task)
            ? sourceId == null || visibilityScope.clearedSourceDataSourceIds().contains(sourceId)
            : sourceId != null && visibilityScope.clearedSourceDataSourceIds().contains(sourceId);
    }

    private Map.Entry<?, ?> findNormalizedEntry(Object value, String requestedName) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        String normalizedName = normalizeFieldName(requestedName);
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (normalizedName.equals(normalizeFieldName(String.valueOf(entry.getKey())))) {
                return entry;
            }
        }
        return null;
    }

    private boolean hasExplicitSecretValue(Object value) {
        if (value instanceof String text) {
            return StringUtils.hasText(text);
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        if (value instanceof Iterable<?> values) {
            return values.iterator().hasNext();
        }
        return value != null;
    }

    private Object copyExplicitValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(String.valueOf(key), copyExplicitValue(item)));
            return copy;
        }
        if (value instanceof Iterable<?> values) {
            List<Object> copy = new ArrayList<>();
            values.forEach(item -> copy.add(copyExplicitValue(item)));
            return copy;
        }
        if (value != null && value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            List<Object> copy = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                copy.add(copyExplicitValue(java.lang.reflect.Array.get(value, index)));
            }
            return copy;
        }
        return value;
    }

    private boolean taskClearanceVisible(Map<String, Object> task) {
        if (!isFileTask(task)) {
            return true;
        }
        try {
            assertFileTaskClearance(task);
            return true;
        } catch (ResponseStatusException ex) {
            if (HttpStatus.FORBIDDEN.equals(ex.getStatusCode())) {
                return false;
            }
            throw ex;
        }
    }

    private UUID resolveCanonicalSourceId(Map<String, Object> payload) {
        Map<String, Object> source = mapValue(payload.get("source"));
        Map<String, Object> sourceConfig = mapValue(payload.get("sourceConfig"));
        Map<String, Object> nestedConfig = mapValue(source.get("config"));
        return resolveCanonicalUuid(
            "源数据源",
            payload.get("sourceDataSourceId"),
            source.get("sourceDataSourceId"),
            source.get("dataSourceId"),
            nestedConfig.get("sourceDataSourceId"),
            nestedConfig.get("dataSourceId"),
            sourceConfig.get("sourceDataSourceId"),
            sourceConfig.get("dataSourceId")
        );
    }

    private UUID resolveCanonicalTargetId(Map<String, Object> payload) {
        Map<String, Object> destination = mapValue(payload.get("destination"));
        Map<String, Object> destinationConfig = mapValue(payload.get("destinationConfig"));
        Map<String, Object> nestedConfig = mapValue(destination.get("config"));
        return resolveCanonicalUuid(
            "目标数据源",
            payload.get("targetDataSourceId"),
            payload.get("destinationDataSourceId"),
            destination.get("targetDataSourceId"),
            destination.get("destinationDataSourceId"),
            destination.get("dataSourceId"),
            nestedConfig.get("targetDataSourceId"),
            nestedConfig.get("destinationDataSourceId"),
            nestedConfig.get("dataSourceId"),
            destinationConfig.get("targetDataSourceId"),
            destinationConfig.get("destinationDataSourceId"),
            destinationConfig.get("dataSourceId")
        );
    }

    private UUID resolveCanonicalUuid(String label, Object... aliases) {
        Set<UUID> values = new LinkedHashSet<>();
        for (Object alias : aliases) {
            if (!StringUtils.hasText(text(alias))) {
                continue;
            }
            UUID parsed = parseUuid(alias);
            if (parsed == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + "标识必须是 UUID");
            }
            values.add(parsed);
        }
        if (values.size() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + "标识存在冲突");
        }
        return values.stream().findFirst().orElse(null);
    }

    private void writeCanonicalSourceId(Map<String, Object> payload, String canonicalId) {
        payload.put("sourceDataSourceId", canonicalId);
        overwriteAliases(payload, canonicalId, "sourceDataSourceId");
        Map<String, Object> source = mutableNestedMap(payload, "source");
        if (source != null) {
            source.put("dataSourceId", canonicalId);
            overwriteAliases(source, canonicalId, "sourceDataSourceId", "dataSourceId");
            Map<String, Object> nestedConfig = mutableNestedMap(source, "config");
            if (nestedConfig != null) {
                nestedConfig.put("dataSourceId", canonicalId);
                overwriteAliases(nestedConfig, canonicalId, "sourceDataSourceId", "dataSourceId");
            }
        }
        Map<String, Object> sourceConfig = mutableNestedMap(payload, "sourceConfig");
        if (sourceConfig != null) {
            sourceConfig.put("dataSourceId", canonicalId);
            overwriteAliases(sourceConfig, canonicalId, "sourceDataSourceId", "dataSourceId");
        }
    }

    private void writeCanonicalTargetId(Map<String, Object> payload, String canonicalId) {
        payload.put("targetDataSourceId", canonicalId);
        overwriteAliases(payload, canonicalId, "targetDataSourceId", "destinationDataSourceId");
        Map<String, Object> destination = mutableNestedMap(payload, "destination");
        if (destination != null) {
            destination.put("targetDataSourceId", canonicalId);
            overwriteAliases(destination, canonicalId, "targetDataSourceId", "destinationDataSourceId", "dataSourceId");
            Map<String, Object> nestedConfig = mutableNestedMap(destination, "config");
            if (nestedConfig != null) {
                nestedConfig.put("targetDataSourceId", canonicalId);
                overwriteAliases(nestedConfig, canonicalId, "targetDataSourceId", "destinationDataSourceId", "dataSourceId");
            }
        }
        Map<String, Object> destinationConfig = mutableNestedMap(payload, "destinationConfig");
        if (destinationConfig != null) {
            destinationConfig.put("targetDataSourceId", canonicalId);
            overwriteAliases(destinationConfig, canonicalId, "targetDataSourceId", "destinationDataSourceId", "dataSourceId");
        }
    }

    private void overwriteAliases(Map<String, Object> values, String canonicalId, String... aliases) {
        for (String alias : aliases) {
            if (values.containsKey(alias)) {
                values.put(alias, canonicalId);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mutableNestedMap(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (!(value instanceof Map<?, ?>)) {
            return null;
        }
        Map<String, Object> nested = (Map<String, Object>) value;
        return nested;
    }

    private Map<String, Object> deepMutableMap(Map<String, Object> value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value != null) {
            value.forEach((key, item) -> result.put(key, deepMutableValue(item)));
        }
        return result;
    }

    private Object deepMutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            map.forEach((key, item) -> nested.put(String.valueOf(key), deepMutableValue(item)));
            return nested;
        }
        if (value instanceof Iterable<?> values) {
            List<Object> nested = new ArrayList<>();
            values.forEach(item -> nested.add(deepMutableValue(item)));
            return nested;
        }
        return value;
    }

    private UUID sourceDataSourceId(Map<String, Object> payload) {
        return payload == null ? null : resolveCanonicalSourceId(payload);
    }

    private UUID targetDataSourceId(Map<String, Object> payload) {
        return payload == null ? null : resolveCanonicalTargetId(payload);
    }

    private boolean isFileTask(Map<String, Object> task) {
        Map<String, Object> source = mapValue(task == null ? null : task.get("source"));
        Map<String, Object> nestedSourceConfig = mapValue(source.get("config"));
        Map<String, Object> topSourceConfig = mapValue(task == null ? null : task.get("sourceConfig"));
        if (
            nestedSourceConfig.keySet().stream().anyMatch(key -> key.startsWith("_file") || "_encrypted".equals(key)) ||
            topSourceConfig.keySet().stream().anyMatch(key -> key.startsWith("_file") || "_encrypted".equals(key))
        ) {
            return true;
        }
        String type = text(firstNonNull(
            task == null ? null : task.get("sourceKind"),
            task == null ? null : task.get("sourceType"),
            source.get("sourceType"),
            source.get("type"),
            source.get("definitionId"),
            nestedSourceConfig.get("readerType"),
            topSourceConfig.get("readerType")
        ));
        if (!StringUtils.hasText(type)) {
            return false;
        }
        String normalized = type.toLowerCase(Locale.ROOT);
        return normalized.contains("file") || normalized.contains("excel") || normalized.contains("csv") || normalized.contains("txt");
    }

    private List<Map<String, Object>> classificationSeals(Map<String, Object> task) {
        List<Map<String, Object>> seals = new ArrayList<>();
        addSeal(seals, task == null ? null : task.get("classificationSeal"));
        Map<String, Object> source = mapValue(task == null ? null : task.get("source"));
        addSeal(seals, source.get("classificationSeal"));
        addSeal(seals, mapValue(source.get("config")).get("classificationSeal"));
        addSeal(seals, mapValue(task == null ? null : task.get("sourceConfig")).get("classificationSeal"));
        return seals;
    }

    private void addSeal(List<Map<String, Object>> seals, Object value) {
        Map<String, Object> seal = mapValue(value);
        if (!seal.isEmpty() && seals.stream().noneMatch(existing -> existing.equals(seal))) {
            seals.add(seal);
        }
    }

    private boolean isInternalFilePathField(String fieldName, Object value, boolean apiResourceContext) {
        String normalized = normalizeFieldName(fieldName);
        if (Set.of("hostpath", "containerpath", "filepath", "addaxjobpath").contains(normalized)) {
            return true;
        }
        if (!"path".equals(normalized)) {
            return false;
        }
        return !(apiResourceContext && containsOnlyApiRelativePaths(value)) && containsInternalAbsolutePath(value);
    }

    private boolean isSensitiveResponseField(String fieldName) {
        return DefaultDestinationSyncService.isSensitiveConfigKey(fieldName);
    }

    private boolean isApiResourceField(String fieldName) {
        String normalized = normalizeFieldName(fieldName);
        return "resource".equals(normalized) || "resources".equals(normalized);
    }

    private String normalizeFieldName(String fieldName) {
        return fieldName == null
            ? ""
            : fieldName.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    private boolean containsOnlyApiRelativePaths(Object value) {
        if (value instanceof String path) {
            String candidate = path.trim();
            return StringUtils.hasText(candidate) &&
                !candidate.startsWith("//") &&
                !candidate.toLowerCase(Locale.ROOT).startsWith("file:") &&
                !candidate.contains("://") &&
                !candidate.contains("\\") &&
                !candidate.matches("(?i)^[a-z]:[\\\\/].*") &&
                !CONTROL_CHARACTER.matcher(candidate).find();
        }
        if (value instanceof Iterable<?> values) {
            boolean found = false;
            for (Object item : values) {
                found = true;
                if (!containsOnlyApiRelativePaths(item)) {
                    return false;
                }
            }
            return found;
        }
        if (value != null && value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            if (length == 0) {
                return false;
            }
            for (int index = 0; index < length; index++) {
                if (!containsOnlyApiRelativePaths(java.lang.reflect.Array.get(value, index))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private boolean containsInternalAbsolutePath(Object value) {
        if (value instanceof String path) {
            String candidate = path.trim().replace('\\', '/');
            return candidate.startsWith("/") ||
                candidate.toLowerCase(Locale.ROOT).startsWith("file:") ||
                candidate.matches("(?i)^[a-z]:/.*");
        }
        if (value instanceof Iterable<?> values) {
            for (Object item : values) {
                if (containsInternalAbsolutePath(item)) {
                    return true;
                }
            }
            return false;
        }
        if (value != null && value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int index = 0; index < length; index++) {
                if (containsInternalAbsolutePath(java.lang.reflect.Array.get(value, index))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isApiDataSource(InfraDataSource dataSource) {
        String type = text(dataSource == null ? null : dataSource.getType());
        String connector = text(dataSource == null ? null : dataSource.getConnectorKey());
        String normalized = ((type == null ? "" : type) + " " + (connector == null ? "" : connector)).toLowerCase(Locale.ROOT);
        return normalized.contains("api") || normalized.contains("http") || normalized.contains("rest");
    }

    private Map<String, Object> dataSourceProperties(InfraDataSource dataSource) {
        if (dataSource == null || !StringUtils.hasText(dataSource.getProps())) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(dataSource.getProps(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "数据源密级配置无效");
        }
    }

    private List<Map<String, Object>> pageContent(Map<String, Object> data) {
        Object raw = data.get("content");
        if (!(raw instanceof Iterable<?>)) {
            raw = data.get("items");
        }
        if (!(raw instanceof Iterable<?> values)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : values) {
            Map<String, Object> task = mapValue(item);
            if (!task.isEmpty()) {
                result.add(task);
            }
        }
        return result;
    }

    private long pageTotalElements(Map<String, Object> data, int loaded, int pageSize) {
        Object raw = data.get("totalElements");
        if (raw instanceof Number number) {
            return Math.max(0, number.longValue());
        }
        return pageSize < SCOPE_SCAN_PAGE_SIZE ? loaded : Long.MAX_VALUE;
    }

    private int queryInteger(Object raw, int defaultValue, int min, int max, String field) {
        if (raw == null) {
            return defaultValue;
        }
        String text = text(raw);
        try {
            int value = Integer.parseInt(text);
            if (value < min || value > max) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " 参数无效");
        }
    }

    private void rejectApiNetworkOverrides(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey()).replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
                if (API_TEST_FORBIDDEN_KEYS.contains(key)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "API_TEST_NETWORK_OVERRIDE_FORBIDDEN: " + entry.getKey());
                }
                rejectApiNetworkOverrides(entry.getValue());
            }
        } else if (value instanceof Iterable<?> values) {
            values.forEach(this::rejectApiNetworkOverrides);
        }
    }

    private void validateRelativePath(String path) {
        String value = path.trim();
        if (!value.startsWith("/") || value.startsWith("//") || value.contains("://") || value.contains("\\") || CONTROL_CHARACTER.matcher(value).find()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "API_TEST_RESOURCE_PATH_INVALID: path 必须是严格相对路径");
        }
    }

    private String resolveTokenDepartment() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            if (authentication instanceof JwtAuthenticationToken token) {
                return firstText(
                    token.getToken().getClaims().get("dept_code"),
                    token.getToken().getClaims().get("deptCode"),
                    token.getToken().getClaims().get("department")
                );
            }
            if (authentication != null && authentication.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal) {
                return firstText(
                    principal.getAttribute("dept_code"),
                    principal.getAttribute("deptCode"),
                    principal.getAttribute("department")
                );
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private boolean isInstituteMaintainer() {
        return SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES);
    }

    private record TaskVisibilityScope(Set<UUID> visibleDataSourceIds, Set<UUID> clearedSourceDataSourceIds) {}

    private UUID parseUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString().trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Map<String, Object> mapValue(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
        }
        return result;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? null : value.toString().trim();
    }
}
