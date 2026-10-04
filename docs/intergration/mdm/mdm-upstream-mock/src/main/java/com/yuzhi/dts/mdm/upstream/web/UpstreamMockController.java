package com.yuzhi.dts.mdm.upstream.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.mdm.upstream.config.UpstreamMockProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

@RestController
public class UpstreamMockController {

    private static final Logger LOG = LoggerFactory.getLogger("mdm.upstream.mock");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final int MAX_LOGS = 200;

    private final UpstreamMockProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final List<PullLog> pullLogs = Collections.synchronizedList(new ArrayList<>());
    private final List<CallbackLog> callbackLogs = Collections.synchronizedList(new ArrayList<>());

    public UpstreamMockController(
        UpstreamMockProperties props,
        RestTemplate restTemplate,
        ObjectMapper objectMapper,
        ResourceLoader resourceLoader
    ) {
        this.props = props;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    @GetMapping("/api/mdm/ping")
    public Map<String, Object> ping() {
        return Map.of("status", "ok", "callbackUrl", props.getCallbackUrl(), "dataType", props.getDataType());
    }

    /**
     * 模拟院方接口：接受 pull/handshake 请求，然后回调推送全量文件。
     */
    @PostMapping(value = "/api/mdm/pull", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_JSON_VALUE, MediaType.ALL_VALUE })
    public ResponseEntity<Map<String, Object>> pull(
        @RequestParam Map<String, String> params,
        @RequestParam(value = "file", required = false) MultipartFile file,
        @RequestPart(value = "body", required = false) Map<String, Object> bodyPart,
        @RequestPart(value = "payload", required = false) Map<String, Object> payloadPart
    ) throws IOException {
        Map<String, Object> body = firstNonNull(bodyPart, payloadPart);
        String callbackUrl = firstNonBlank(params.get("callbackUrl"), props.getCallbackUrl());
        String dataType = firstNonBlank(params.get("dataType"), props.getDataType());
        String filename = props.getFilePrefix() + TS.format(LocalDateTime.now()) + props.getFileSuffix();
        byte[] payloadBytes = loadSample(bytesOrNull(file), body);
        long payloadSize = payloadBytes == null ? 0 : payloadBytes.length;

        addPullLog(callbackUrl, dataType, filename, payloadSize, params);
        CallbackResult cb = sendCallback(callbackUrl, dataType, filename, payloadBytes);
        LOG.info(
            "mock.pull received params={} body={} payloadBytes={} -> callback status={} msg={}",
            params,
            body,
            payloadSize,
            cb.status,
            cb.message
        );
        Map<String, Object> requestEcho = new LinkedHashMap<>();
        requestEcho.put("params", params);
        requestEcho.put("body", body);
        requestEcho.put("callbackUrl", callbackUrl);
        requestEcho.put("dataType", dataType);
        requestEcho.put("sentFilename", filename);
        requestEcho.put("payloadBytes", payloadSize);
        requestEcho.put("callbackStatus", cb.status);
        requestEcho.put("callbackMessage", cb.message);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "ok");
        resp.put("callbackStatus", cb.status);
        resp.put("callbackBody", cb.body);
        resp.put("echo", requestEcho);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/api/mdm/logs/pulls")
    public List<PullLog> listPullLogs() {
        synchronized (pullLogs) {
            return List.copyOf(pullLogs);
        }
    }

    @GetMapping("/api/mdm/logs/callbacks")
    public List<CallbackLog> listCallbackLogs() {
        synchronized (callbackLogs) {
            return List.copyOf(callbackLogs);
        }
    }

    private CallbackResult sendCallback(String callbackUrl, String dataType, String filename, byte[] bytes) {
        CallbackResult r = new CallbackResult();
        try {
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("dataType", dataType);
            ByteArrayResource fileRes = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
            form.add(props.getFilePartName(), fileRes);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            // Token 可为空，留作兼容院方无鉴权的场景
            if (StringUtils.isNotBlank(props.getCallbackToken())) {
                headers.add(props.getSignatureHeader(), props.getCallbackToken().trim());
            }

            ResponseEntity<String> resp = restTemplate.postForEntity(callbackUrl, new HttpEntity<>(form, headers), String.class);
            r.status = resp.getStatusCodeValue();
            r.body = resp.getBody();
            r.message = "sent";
        } catch (Exception e) {
            r.status = 500;
            r.message = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        addCallbackLog(callbackUrl, dataType, filename, r.status, r.message);
        return r;
    }

    private byte[] bytesOrNull(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }
        return file.getBytes();
    }

    private byte[] loadSample(byte[] uploaded, Map<String, Object> body) throws IOException {
        byte[] candidate = null;
        // 优先使用真实推送样例；如果上传内容只是 sync-demand 请求（无用户/组织数据），则改用示例文件
        if (uploaded != null && uploaded.length > 0) {
            try {
                Object parsed = objectMapper.readValue(uploaded, Object.class);
                if (looksLikePayload(parsed)) {
                    candidate = uploaded;
                } else {
                    LOG.info("uploaded payload looks like sync-demand; fallback to sample");
                }
            } catch (Exception e) {
                LOG.warn("parse uploaded payload failed: {}", e.getMessage());
                candidate = uploaded;
            }
        }
        // 如果 body 里有 json 字符串则优先使用
        if (candidate == null && body != null && !body.isEmpty()) {
            candidate = objectMapper.writeValueAsBytes(body);
        }
        if (candidate == null) {
            Resource res = resourceLoader.getResource(props.getSampleLocation());
            if (res.exists()) {
                try {
                    candidate = res.getInputStream().readAllBytes();
                } catch (Exception e) {
                    LOG.warn("load sample failed: {}", e.getMessage());
                }
            }
        }
        if (candidate == null) {
            // fallback 内置示例
            String fallback = """
                {
                  "desp": {
                    "dataRange": "9010",
                    "sendTime": 1765057111
                  },
                  "user": [
                    {
                      "createTime": "1765057111",
                      "deptCode": "9010",
                      "diepId": "32",
                      "identityCard": "510xxxx",
                      "orgCode": "9010",
                      "securityLevel": "3",
                      "status": "1",
                      "updateTime": "202512081765085911",
                      "userCode": "ldgbgusd-10",
                      "userName": "测试员工1"
                    }
                  ],
                  "orgId": [
                    {
                      "deptCode": "9010",
                      "deptName": "demo app",
                      "diepId": "9a3Eaxxx",
                      "orgCode": "9010",
                      "parentCode": "90",
                      "shortName": "十所",
                      "sort": "11",
                      "status": "1",
                      "type": "0"
                    }
                  ]
                }
                """;
            candidate = fallback.getBytes(StandardCharsets.UTF_8);
        }
        // 合并多文档样例，院方真实数据是单文件包含全部人员/机构
        try {
            Map<String, Object> merged = mergeMultiDoc(candidate);
            if (merged != null) {
                LOG.info("mock.sample merged users={} orgs={}", sizeOfList(merged.get("user")), sizeOfList(merged.get("orgId")));
                return objectMapper.writeValueAsBytes(merged);
            }
        } catch (Exception e) {
            LOG.warn("merge sample docs failed: {}", e.getMessage());
        }
        return candidate;
    }

    private boolean looksLikePayload(Object parsed) {
        if (!(parsed instanceof Map<?, ?> map)) {
            return false;
        }
        Object users = map.get("user");
        if (users instanceof List<?> list && !list.isEmpty()) {
            return true;
        }
        Object depts = firstNonNull(map.get("orgId"), map.get("orgIt"));
        return depts instanceof List<?> list && !list.isEmpty();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeMultiDoc(byte[] data) throws IOException {
        String raw = new String(data, StandardCharsets.UTF_8);
        if (!raw.contains("---")) {
            Object parsed = objectMapper.readValue(raw, Object.class);
            if (parsed instanceof Map<?, ?> m) {
                return (Map<String, Object>) m;
            }
            return null;
        }
        String[] parts = raw.split("(?m)^---\\s*$");
        List<Map<String, Object>> docs = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                String cleaned = java.util.Arrays
                    .stream(trimmed.split("\\R"))
                    .filter(line -> !line.trim().startsWith("#"))
                    .collect(java.util.stream.Collectors.joining("\n"))
                    .trim();
                if (cleaned.isEmpty()) {
                    continue;
                }
                Object parsed = objectMapper.readValue(cleaned, Object.class);
                if (parsed instanceof Map<?, ?> m) {
                    docs.add((Map<String, Object>) m);
                }
            } catch (Exception e) {
                LOG.warn("skip invalid sample part: {}", e.getMessage());
            }
        }
        if (docs.isEmpty()) {
            return null;
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.putAll(docs.get(0)); // desp 等保持第一段

        List<Map<String, Object>> allUsers = new ArrayList<>();
        List<Map<String, Object>> allOrgs = new ArrayList<>();
        for (Map<String, Object> m : docs) {
            Object u = m.get("user");
            if (u instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> mm) {
                        allUsers.add(new LinkedHashMap<>((Map<String, Object>) mm));
                    }
                }
            }
            Object o = firstNonNull(m.get("orgId"), m.get("orgIt"));
            if (o instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> mm) {
                        allOrgs.add(new LinkedHashMap<>((Map<String, Object>) mm));
                    }
                }
            }
        }
        merged.put("user", allUsers);
        merged.put("orgId", allOrgs);
        merged.put("orgIt", allOrgs);
        return merged;
    }

    private int sizeOfList(Object obj) {
        if (obj instanceof List<?> list) {
            return list.size();
        }
        return 0;
    }

    private String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (StringUtils.isNotBlank(v)) {
                return v.trim();
            }
        }
        return null;
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private void addPullLog(String callbackUrl, String dataType, String filename, long payloadSize, Map<String, String> params) {
        Map<String, String> paramCopy = params == null ? Map.of() : new LinkedHashMap<>(params);
        PullLog log = new PullLog(LocalDateTime.now().toString(), callbackUrl, dataType, filename, payloadSize, paramCopy);
        synchronized (pullLogs) {
            pullLogs.add(0, log);
            trimIfNeeded(pullLogs);
        }
    }

    private void addCallbackLog(String callbackUrl, String dataType, String filename, int status, String message) {
        CallbackLog log = new CallbackLog(LocalDateTime.now().toString(), callbackUrl, dataType, filename, status, message);
        synchronized (callbackLogs) {
            callbackLogs.add(0, log);
            trimIfNeeded(callbackLogs);
        }
    }

    private void trimIfNeeded(List<?> list) {
        while (list.size() > MAX_LOGS) {
            list.remove(list.size() - 1);
        }
    }

    private static class CallbackResult {
        int status;
        String message;
        String body;
    }

    public record PullLog(String time, String callbackUrl, String dataType, String filename, long payloadBytes, Map<String, String> params) {}

    public record CallbackLog(String time, String callbackUrl, String dataType, String filename, int status, String message) {}
}
