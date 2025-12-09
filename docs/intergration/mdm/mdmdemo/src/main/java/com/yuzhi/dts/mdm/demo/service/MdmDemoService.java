package com.yuzhi.dts.mdm.demo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.mdm.demo.config.MdmDemoProperties;
import jakarta.annotation.PostConstruct;
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
import java.util.Set;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MdmDemoService {

    private static final Logger LOG = LoggerFactory.getLogger("mdm.demo");
    private static final DateTimeFormatter TS_DIR = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TS_FILE = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final ObjectMapper objectMapper;
    private final MdmDemoProperties properties;
    private final RestTemplate restTemplate;

    public MdmDemoService(ObjectMapper objectMapper, MdmDemoProperties properties, RestTemplateBuilder restTemplateBuilder) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.restTemplate = restTemplateBuilder
            .setConnectTimeout(properties.getUpstream().getConnectTimeout())
            .setReadTimeout(properties.getUpstream().getReadTimeout())
            .build();
    }

    @PostConstruct
    void ensureStorage() throws IOException {
        Files.createDirectories(Paths.get(properties.getStoragePath()));
    }

    public HandshakeResult handshake() {
        requireEnabled();
        String url = StringUtils.trimToNull(properties.getUpstream().getHandshakeUrl());
        if (url == null) {
            throw new IllegalStateException("缺少握手地址 mdm.demo.upstream.handshake-url");
        }
        Map<String, Object> payload = new HashMap<>();
        // payload.put("systemCode", properties.getRegistry().getSystemCode());
        // payload.put("callbackUrl", properties.getCallback().getUrl());
        // payload.put("callbackSecret", properties.getCallback().getAuthToken());
        // payload.put("securityDomain", properties.getRegistry().getSecurityDomain());
        // payload.put("pushMode", properties.getRegistry().getPushMode());
        // payload.put("dataTypes", properties.getRegistry().getDataTypes());
        // 院方示例中的扩展参数，可继续按需添加
        payload.put("sysCode", "BI9010");
        payload.put("dataRange", "9010");
        payload.put("areaSecurity", "9001");
        payload.put("areaBusiness", "B");
        payload.put("dataType", "sync-demand");
        String sendStr;
        try {
            sendStr = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化握手 payload 失败", e);
        }
        // 按院方风格：multipart，一个文件部件 + 额外参数表单字段（文件内容与参数分离）
        String filename = "orgItDemand" + TS_FILE.format(java.time.LocalDateTime.now()) + ".txt";
        ByteArrayResource fileRes = new ByteArrayResource(sendStr.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", fileRes);
        // 院方示例中的参数表单（可按需扩展），保持与文件内容分离
        form.add("targetNodeCode","9000");
        form.add("targetDomainCode","B");
        form.add("targetServiceId","orgit");
        form.add("sourceServiceId","WXXXX");
        form.add("approval","1");
        form.add("title","BI9010_IT_ORG_SYNC");
        form.add("dataType","sync-demand");
        form.add("securityLevel","1");
        form.add("filedType","B");
        form.add("version","1");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (StringUtils.isNotBlank(properties.getUpstream().getAuthToken())) {
            headers.setBearerAuth(properties.getUpstream().getAuthToken().trim());
        }
        ResponseEntity<String> resp = restTemplate.postForEntity(url, new HttpEntity<>(form, headers), String.class);
        LOG.info("mdm.handshake (multipart) url={} filename={} status={} body={}", url, filename, resp.getStatusCodeValue(), resp.getBody());
        HandshakeResult result = new HandshakeResult();
        result.requestId = UUID.randomUUID().toString();
        result.upstreamStatus = resp.getStatusCodeValue();
        result.upstreamBody = resp.getBody();
        return result;
    }

    public CallbackResult receive(Map<String, String> params, MultipartFile file, HttpServletRequest request) throws IOException {
        requireEnabled();
        String dataType = params == null ? null : params.get("dataType");
        dataType = StringUtils.defaultString(dataType, "unknown");
        checkSignature(request);

        if ("sync_demand".equalsIgnoreCase(dataType)) {
            CallbackResult result = new CallbackResult();
            result.mode = "sync_demand";
            result.dataType = dataType;
            result.clientIp = clientIp(request);
            LOG.info("mdm.receive sync_demand accepted clientIp={} dataType={}", result.clientIp, dataType);
            return result;
        }

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("缺少文件，且 dataType 不是 sync_demand");
        }

        LocalDateTime now = LocalDateTime.now();
        String batchId = TS_FILE.format(now);
        Path dir = Paths.get(properties.getStoragePath(), TS_DIR.format(now));
        Files.createDirectories(dir);
        // 忽略上游文件名，统一按批次时间戳命名，便于离线调试
        Path saveTo = dir.resolve(batchId + ".json");
        byte[] bytes = file.getBytes();
        Files.write(saveTo, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        String md5 = org.springframework.util.DigestUtils.md5DigestAsHex(bytes);

        String body = new String(bytes, StandardCharsets.UTF_8);
        CallbackPayload payload = parsePayload(body);

        CallbackResult result = new CallbackResult();
        result.batchId = batchId;
        result.mode = "push";
        result.dataType = dataType;
        result.file = saveTo.toAbsolutePath().toString();
        result.size = bytes.length;
        result.md5 = md5;
        result.clientIp = clientIp(request);
        result.userRecords = payload.users().size();
        result.deptRecords = payload.depts().size();
        result.missingUsers = findMissing(payload.users(), properties.getRequired().getUsers());
        result.missingDepts = findMissing(payload.depts(), properties.getRequired().getDepts());

        LOG.info(
            "mdm.callback saved file={} size={} md5={} clientIp={} users={} depts={} missingUsers={} missingDepts={}",
            result.file,
            result.size,
            md5,
            result.clientIp,
            result.userRecords,
            result.deptRecords,
            result.missingUsers,
            result.missingDepts
        );
        return result;
    }

    private CallbackPayload parsePayload(String rawBody) {
        if (!StringUtils.isNotBlank(rawBody)) {
            return new CallbackPayload(List.of(), List.of());
        }
        try {
            Object parsed = objectMapper.readValue(rawBody, Object.class);
            // 期望格式：{"users":[...], "depts":[...]}
            if (parsed instanceof Map<?, ?> map) {
                List<Map<String, Object>> users = extractList(map.get("users"));
                List<Map<String, Object>> depts = extractList(map.get("depts"));
                return new CallbackPayload(users, depts);
            }
            // 兼容：顶层数组，视作 users
            if (parsed instanceof List<?> list) {
                return new CallbackPayload(castList(list), List.of());
            }
        } catch (Exception e) {
            LOG.warn("mdm.callback.parse failed: {}", e.getMessage());
        }
        return new CallbackPayload(List.of(), List.of());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractList(Object val) {
        if (!(val instanceof List<?> list)) {
            return List.of();
        }
        return castList(list);
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
        Set<String> required = new HashSet<>();
        for (String part : StringUtils.split(StringUtils.defaultString(requiredCsv), ',')) {
            if (StringUtils.isNotBlank(part)) {
                required.add(part.trim());
            }
        }
        if (required.isEmpty()) {
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

    private void checkSignature(HttpServletRequest request) {
        String expected = StringUtils.trimToNull(properties.getCallback().getAuthToken());
        if (expected == null) {
            return;
        }
        String headerName = StringUtils.defaultIfBlank(properties.getCallback().getSignatureHeader(), "X-Signature");
        String provided = request == null ? null : StringUtils.trimToNull(request.getHeader(headerName));
        if (!Objects.equals(expected, provided)) {
            throw new SecurityException("回调鉴权失败，头部 " + headerName + " 不匹配");
        }
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String xff = StringUtils.trimToNull(request.getHeader("X-Forwarded-For"));
        if (xff != null) {
            int idx = xff.indexOf(',');
            return idx > 0 ? xff.substring(0, idx).trim() : xff;
        }
        String real = StringUtils.trimToNull(request.getHeader("X-Real-IP"));
        if (real != null) {
            return real;
        }
        return request.getRemoteAddr();
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("MDM demo 未启用");
        }
    }

    public record CallbackPayload(List<Map<String, Object>> users, List<Map<String, Object>> depts) {}

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
        public Set<String> missingUsers = Set.of();
        public Set<String> missingDepts = Set.of();
    }

    public static class HandshakeResult {
        public String requestId;
        public int upstreamStatus;
        public String upstreamBody;
    }
}
