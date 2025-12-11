package com.yuzhi.dts.admin.service.mdm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.config.MdmGatewayProperties;
import com.yuzhi.dts.admin.service.OrganizationService;
import com.yuzhi.dts.admin.service.dto.personnel.PersonnelPayload;
import com.yuzhi.dts.admin.service.personnel.PersonnelImportService;
import com.yuzhi.dts.common.net.IpAddressUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.ByteArrayResource;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;

@Service
public class MdmGatewayService {

    private static final Logger LOG = LoggerFactory.getLogger("dts.mdm.gateway");
    private static final DateTimeFormatter TS_DIR = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TS_FILE = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final RestTemplate restTemplate;
    private final MdmGatewayProperties properties;
    private final ObjectMapper objectMapper;
    private final PersonnelImportService personnelImportService;
    private final OrganizationService organizationService;
    private final com.yuzhi.dts.admin.repository.OrganizationRepository organizationRepository;
    private final Executor taskExecutor;

    public MdmGatewayService(
        RestTemplateBuilder restTemplateBuilder,
        MdmGatewayProperties properties,
        ObjectMapper objectMapper,
        PersonnelImportService personnelImportService,
        OrganizationService organizationService,
        com.yuzhi.dts.admin.repository.OrganizationRepository organizationRepository,
        @Qualifier("taskExecutor") ObjectProvider<Executor> taskExecutorProvider
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.personnelImportService = personnelImportService;
        this.organizationService = organizationService;
        this.organizationRepository = organizationRepository;
        this.taskExecutor = taskExecutorProvider.getIfAvailable(() -> (Runnable command) -> command.run());
        this.restTemplate = restTemplateBuilder
            .setConnectTimeout(properties.getUpstream().getConnectTimeout())
            .setReadTimeout(properties.getUpstream().getReadTimeout())
            .build();
    }

    public PullResult triggerUpstreamPull(Map<String, Object> requestPayload) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("MDM 网关未启用");
        }
        Map<String, Object> payload = new HashMap<>();
        // 默认 payload：来自 registry（院方示例）+ payloadTemplate
        payload.put("dataRange", properties.getRegistry().getDataRange());
        payload.put("areaSecurity", properties.getRegistry().getAreaSecurity());
        payload.put("areaBusiness", properties.getRegistry().getAreaBusiness());
        payload.put("sysCode", properties.getRegistry().getSystemCode());
        payload.put("dataType", properties.getRegistry().getDataType());
        payload.put("sendTime", System.currentTimeMillis());
        payload.putAll(properties.getUpstream().getPayloadTemplate());
        // 调用方可以覆盖
        if (requestPayload != null) {
            payload.putAll(requestPayload);
        }
        String base = StringUtils.trimToEmpty(properties.getUpstream().getBaseUrl());
        String path = StringUtils.defaultString(properties.getUpstream().getPullPath(), "/api/mdm/pull");
        String url = base.endsWith("/") ? base.substring(0, base.length() - 1) + path : base + path;

        ResponseEntity<String> response;
        if (properties.getUpstream().isUseMultipart()) {
            response = doMultipartPull(url, payload);
        } else {
            response = doJsonPull(url, payload);
        }
        LOG.info("mdm.pull.response status={} body={}", response.getStatusCodeValue(), response.getBody());

        PullResult result = new PullResult();
        result.requestId = UUID.randomUUID().toString();
        result.upstreamStatus = response.getStatusCodeValue();
        result.upstreamBody = response.getBody();
        return result;
    }

    private ResponseEntity<String> doJsonPull(String url, Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.isNotBlank(properties.getUpstream().getAuthToken())) {
            headers.setBearerAuth(properties.getUpstream().getAuthToken().trim());
        }
        String body;
        try {
            body = objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("序列化拉取参数失败: " + e.getMessage(), e);
        }
        LOG.info("mdm.pull.request (json) url={} payload={}", url, body);
        return restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> doMultipartPull(String url, Map<String, Object> payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("序列化拉取参数失败: " + e.getMessage(), e);
        }
        String ts = TS_FILE.format(LocalDateTime.now());
        String filename = properties.getUpstream().getFilePrefix() + ts + properties.getUpstream().getFileSuffix();
        ByteArrayResource fileRes = new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add(properties.getUpstream().getFilePartName(), fileRes);
        // 院方示例：文件与附加表单参数分离，formParams 用于 targetNode 等
        if (properties.getUpstream().getFormParams() != null) {
            properties
                .getUpstream()
                .getFormParams()
                .forEach((k, v) -> {
                    if (StringUtils.isNotBlank(k) && v != null) {
                        form.add(k, v);
                    }
                });
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (StringUtils.isNotBlank(properties.getUpstream().getAuthToken())) {
            headers.setBearerAuth(properties.getUpstream().getAuthToken().trim());
        }
        LOG.info(
            "mdm.pull.request (multipart) url={} filename={} payloadLength={} formParams={}",
            url,
            filename,
            json.length(),
            properties.getUpstream().getFormParams()
        );
        return restTemplate.postForEntity(url, new HttpEntity<>(form, headers), String.class);
    }

    public CallbackResult handleReceive(Map<String, String> params, String typeOrDataType, MultipartFile file, String rawBody, HttpServletRequest request)
        throws IOException {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("MDM 网关未启用");
        }
        String dataType = params == null ? null : params.get("dataType");
        if (!StringUtils.isNotBlank(dataType)) {
            dataType = typeOrDataType;
        }
        dataType = StringUtils.defaultString(dataType, "unknown");
        String clientIp = request == null
            ? null
            : IpAddressUtils.resolveClientIp(
                request.getHeader("X-Forwarded-For"),
                request.getHeader("X-Real-IP"),
                request.getRemoteAddr()
            );
        validateToken(request);

        byte[] bytes;
        if (file != null && !file.isEmpty()) {
            bytes = file.getBytes();
        } else {
            bytes = rawBody == null ? new byte[0] : rawBody.getBytes(StandardCharsets.UTF_8);
        }
        String body = new String(bytes, StandardCharsets.UTF_8);
        boolean isSyncDemand = isSyncDemand(dataType);
        // sync-demand 且无负载时仅登记
        if (isSyncDemand && (bytes == null || bytes.length == 0)) {
            CallbackResult result = new CallbackResult();
            result.batchId = "sync-" + TS_FILE.format(LocalDateTime.now());
            result.clientIp = clientIp;
            result.dataType = dataType;
            result.mode = "sync_demand";
            LOG.info("mdm.callback.sync-demand accepted (no payload) clientIp={} dataType={}", clientIp, dataType);
            return result;
        }

        Object parsedObj = parseObject(body);

        LocalDateTime now = LocalDateTime.now();
        String batchId = TS_FILE.format(now);
        String fileBase = StringUtils.firstNonBlank(deriveFileBase(parsedObj), batchId);
        Path dir = Path.of(properties.getStoragePath(), TS_DIR.format(now));
        Files.createDirectories(dir);
        Path saveTo = dir.resolve(fileBase + ".json");

        Files.write(saveTo, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        String md5 = DigestUtils.md5DigestAsHex(bytes);
        Map<String, Object> meta = new HashMap<>();
        meta.put("path", saveTo.toAbsolutePath().toString());
        meta.put("size", bytes.length);
        meta.put("md5", md5);
        meta.put("clientIp", clientIp);
        meta.put("receivedAt", now.toString());
        LOG.info("mdm.callback.saved file={} size={} md5={} clientIp={}", meta.get("path"), meta.get("size"), md5, clientIp);
        CallbackResult result = new CallbackResult();
        result.batchId = batchId;
        result.mode = "push";
        result.dataType = dataType;
        result.file = saveTo.toAbsolutePath().toString();
        result.size = bytes.length;
        result.md5 = md5;
        result.clientIp = clientIp;

        // 异步解析与导入，快速向院方返回成功
        final Object parsedSnapshot = parsedObj;
        final byte[] bytesSnapshot = bytes;
        final CallbackResult resultSnapshot = result;
        final String dataTypeSnapshot = dataType;
        final String clientIpSnapshot = clientIp;
        final String md5Snapshot = md5;
        taskExecutor.execute(() -> processSavedPayload(parsedSnapshot, bytesSnapshot, resultSnapshot, dataTypeSnapshot, clientIpSnapshot, md5Snapshot));
        return result;
    }

    private void processSavedPayload(Object parsedObj, byte[] bytes, CallbackResult result, String dataType, String clientIp, String md5) {
        try {
            LOG.info("mdm.callback.async.start file={} dataType={} clientIp={}", result.file, dataType, clientIp);
            Object parsed = parsedObj != null ? parsedObj : parseObject(new String(bytes, StandardCharsets.UTF_8));
            List<Map<String, Object>> rawUsers = extractUsers(parsed);
            List<Map<String, Object>> rawDepts = extractDepts(parsed);
            result.userRecords = rawUsers.size();
            result.deptRecords = rawDepts.size();
            result.records = result.userRecords + result.deptRecords;
            result.missingUsers = findMissing(rawUsers, properties.getRequired().getUsers());
            result.missingDepts = findMissing(rawDepts, properties.getRequired().getDepts());
            Set<String> union = new HashSet<>();
            union.addAll(result.missingUsers);
            union.addAll(result.missingDepts);
            result.missingRequired = union;

            if (!result.missingRequired.isEmpty()) {
                LOG.warn(
                    "mdm.callback.validation failed missingRequired={} file={} clientIp={}",
                    result.missingRequired,
                    result.file,
                    clientIp
                );
                return;
            }
            LOG.info(
                "mdm.callback.parsed file={} clientIp={} users={} depts={} dataType={} md5={}",
                result.file,
                clientIp,
                result.userRecords,
                result.deptRecords,
                dataType,
                md5
            );

            List<OrganizationService.MdmOrgRecord> orgs = rawDepts
                .stream()
                .map(this::mapOrgPayload)
                .filter(Objects::nonNull)
                .toList();

            int orgsApplied = 0;
            if (!orgs.isEmpty()) {
                orgsApplied = organizationService.syncFromMdm(orgs);
                result.imported = orgsApplied;
                LOG.info("mdm.callback.import.orgs file={} applied={} payloadDepts={}", result.file, orgsApplied, orgs.size());
            }

            List<PersonnelPayload> users = rawUsers.stream().map(this::mapUserPayload).filter(Objects::nonNull).toList();
            if (!users.isEmpty()) {
                var importResult = personnelImportService.importFromMdm(
                    "mdm-callback-" + result.batchId,
                    users,
                    Map.of("file", result.file, "md5", md5, "clientIp", clientIp, "dataType", dataType, "orgsApplied", orgsApplied)
                );
                result.imported = importResult.successRecords();
                result.importBatchId = importResult.batchId();
                result.importFailed = importResult.failureRecords();
                LOG.info(
                    "mdm.callback.import.users file={} success={} failed={} payloadUsers={}",
                    result.file,
                    importResult.successRecords(),
                    importResult.failureRecords(),
                    users.size()
                );
            }
            if (!orgs.isEmpty()) {
                try {
                    organizationService.pushTreeToKeycloak();
                } catch (Exception e) {
                    LOG.warn("mdm.callback.push-keycloak failed: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            LOG.error("mdm.callback.async.failed file={} error={}", result.file, e.getMessage(), e);
        }
    }

    private boolean isSyncDemand(String dataType) {
        return StringUtils.equalsIgnoreCase("sync_demand", dataType) || StringUtils.equalsIgnoreCase("sync-demand", dataType);
    }

    private void validateToken(HttpServletRequest request) {
        String expected = properties.getCallback().getAuthToken();
        if (StringUtils.isBlank(expected)) {
            return;
        }
        String headerName = Optional.ofNullable(properties.getCallback().getSignatureHeader()).filter(StringUtils::isNotBlank).orElse("X-Signature");
        String provided = request != null ? request.getHeader(headerName) : null;
        if (!Objects.equals(expected, StringUtils.trimToNull(provided))) {
            LOG.warn("mdm.callback.token-mismatch expectedHeader={} remoteAddr={}", headerName, request != null ? request.getRemoteAddr() : "unknown");
            throw new SecurityException("回调鉴权失败");
        }
    }

    private OrganizationService.MdmOrgRecord mapOrgPayload(Map<String, Object> m) {
        String deptCode = string(m.get("deptCode"));
        String deptName = string(m.get("deptName"));
        String parentCode = string(m.get("parentCode"));
        String orgCode = string(m.get("orgCode"));
        String shortName = string(m.get("shortName"));
        String status = string(m.get("status"));
        String sort = string(m.get("sort"));
        String type = string(m.get("type"));
        if (!StringUtils.isNotBlank(deptCode) && !StringUtils.isNotBlank(deptName)) {
            return null;
        }
        return new OrganizationService.MdmOrgRecord(deptCode, orgCode, parentCode, deptName, shortName, status, sort, type);
    }

    @SuppressWarnings("unchecked")
    private List<PersonnelPayload> parseUserPayloads(String body) {
        if (!StringUtils.isNotBlank(body)) {
            return List.of();
        }
        try {
            Object parsed = objectMapper.readValue(body, Object.class);
            if (!(parsed instanceof List<?> list)) {
                return List.of();
            }
            return list
                .stream()
                .filter(item -> item instanceof Map)
                .map(item -> mapUserPayload((Map<String, Object>) item))
                .filter(Objects::nonNull)
                .toList();
        } catch (Exception ex) {
            LOG.warn("mdm.callback.parse-users.failed error={}", ex.getMessage());
            return List.of();
        }
    }

    private PersonnelPayload mapUserPayload(Map<String, Object> m) {
        String userCode = string(m.get("userCode"));
        String userName = string(m.get("userName"));
        String deptCode = string(m.get("deptCode"));
        String status = statusToLifecycle(m.get("status"));
        String securityLevel = string(m.get("securityLevel"));
        Map<String, Object> attrs = new HashMap<>();
        m.forEach((k, v) -> {
            if (v != null) {
                attrs.put(k, v);
            }
        });
        if (!StringUtils.isNotBlank(userCode) && !StringUtils.isNotBlank(userName)) {
            return null;
        }
        String deptName = StringUtils.defaultIfBlank(string(m.get("deptName")), resolveDeptName(deptCode));
        return new PersonnelPayload(
            userCode, // personCode
            string(m.get("diepId")), // externalId
            userCode, // account
            userName,
            string(m.get("identityCard")),
            deptCode,
            deptName,
            null,
            null,
            string(m.get("sort")),
            null,
            null,
            status,
            null,
            null,
            attrs
        );
    }

    private String resolveDeptName(String deptCode) {
        if (!StringUtils.isNotBlank(deptCode)) {
            return null;
        }
        return organizationService
            .findByDeptCodeIgnoreCase(deptCode)
            .map(com.yuzhi.dts.admin.domain.OrganizationNode::getName)
            .orElse(null);
    }

    private String string(Object value) {
        return value == null ? null : StringUtils.trimToNull(String.valueOf(value));
    }

    private String statusToLifecycle(Object statusObj) {
        if (statusObj == null) {
            return "ACTIVE";
        }
        String s = String.valueOf(statusObj).trim();
        if ("0".equals(s)) {
            return "INACTIVE";
        }
        if ("1".equals(s)) {
            return "ACTIVE";
        }
        return s;
    }

    private Object parseObject(String body) {
        if (!StringUtils.isNotBlank(body)) {
            return null;
        }
        try {
            return objectMapper.readValue(body, Object.class);
        } catch (Exception e) {
            LOG.warn("mdm.callback.parse-object failed: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractUsers(Object parsed) {
        if (parsed instanceof Map<?, ?> map) {
            Object val = firstNonNull(map.get("users"), map.get("user"));
            return extractList(val);
        }
        if (parsed instanceof List<?>) {
            return castList((List<?>) parsed);
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractDepts(Object parsed) {
        if (parsed instanceof Map<?, ?> map) {
            Object val = firstNonNull(map.get("depts"), map.get("orgId"), map.get("orgIds"), map.get("orgs"), map.get("orgIt"));
            return extractList(val);
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractList(Object val) {
        if (!(val instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(List<?> list) {
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    private Set<String> findMissing(List<Map<String, Object>> list, String requiredCsv) {
        if (!StringUtils.isNotBlank(requiredCsv)) {
            return Set.of();
        }
        Set<String> required = new HashSet<>();
        for (String part : StringUtils.split(requiredCsv, ',')) {
            if (StringUtils.isNotBlank(part)) {
                required.add(part.trim());
            }
        }
        if (required.isEmpty() || list == null || list.isEmpty()) {
            return Set.of();
        }
        Set<String> missing = new HashSet<>();
        for (Map<String, Object> m : list) {
            for (String field : required) {
                Object val = m.get(field);
                if (val == null || StringUtils.isBlank(String.valueOf(val))) {
                    missing.add(field);
                }
            }
        }
        return missing;
    }

    private Object firstNonNull(Object... candidates) {
        if (candidates == null) {
            return null;
        }
        for (Object c : candidates) {
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String deriveFileBase(Object parsed) {
        if (!(parsed instanceof Map<?, ?> map)) {
            return null;
        }
        Object desp = map.get("desp");
        if (desp instanceof Map<?, ?> m) {
            String sendTime = string(m.get("sendTime"));
            if (StringUtils.isNotBlank(sendTime)) {
                String normalized = normalizeSendTime(sendTime);
                if (StringUtils.isNotBlank(normalized)) {
                    return normalized;
                }
            }
        }
        Object users = firstNonNull(map.get("user"), map.get("users"));
        if (users instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> u) {
                String ts = string(u.get("updateTime"));
                if (StringUtils.isNotBlank(ts)) {
                    return sanitizeFilename(ts);
                }
            }
        }
        return null;
    }

    private String sanitizeFilename(String name) {
        if (name == null) {
            return null;
        }
        String cleaned = name.replaceAll("[^0-9A-Za-z_-]", "");
        return StringUtils.isNotBlank(cleaned) ? cleaned : null;
    }

    /**
     * Accept both epoch seconds/millis and formatted yyyyMMddHHmmssSSS strings.
     * Falls back to a sanitized version if parsing fails.
     */
    private String normalizeSendTime(String raw) {
        String trimmed = StringUtils.trimToEmpty(raw);
        // Already looks like yyyyMMddHHmmssSSS
        if (trimmed.matches("\\d{17}")) {
            return trimmed;
        }
        // Epoch seconds or millis
        String digitsOnly = trimmed.replaceAll("\\D", "");
        if (digitsOnly.length() == 13) {
            try {
                long millis = Long.parseLong(digitsOnly);
                return TS_FILE.format(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
            } catch (Exception ignored) {}
        }
        if (digitsOnly.length() == 10) {
            try {
                long seconds = Long.parseLong(digitsOnly);
                return TS_FILE.format(
                    java.time.Instant.ofEpochSecond(seconds).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                );
            } catch (Exception ignored) {}
        }
        // last resort: sanitized raw
        return sanitizeFilename(trimmed);
    }

    public static class PullResult {
        public String requestId;
        public int upstreamStatus;
        public String upstreamBody;
    }

    public static class CallbackResult {
        public String batchId;
        public String mode;
        public String dataType;
        public String file;
        public long size;
        public String md5;
        public String clientIp;
        public int userRecords;
        public int deptRecords;
        public int records;
        public Set<String> missingRequired = Set.of();
        public Set<String> missingUsers = Set.of();
        public Set<String> missingDepts = Set.of();
        public Integer imported;
        public Long importBatchId;
        public Integer importFailed;
    }

}
