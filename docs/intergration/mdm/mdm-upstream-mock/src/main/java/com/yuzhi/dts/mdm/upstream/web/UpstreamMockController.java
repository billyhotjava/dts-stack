package com.yuzhi.dts.mdm.upstream.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.mdm.upstream.config.UpstreamMockProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;

@RestController
public class UpstreamMockController {

    private static final Logger LOG = LoggerFactory.getLogger("mdm.upstream.mock");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final UpstreamMockProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

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
        @RequestBody(required = false) Map<String, Object> body
    ) throws IOException {
        String callbackUrl = firstNonBlank(params.get("callbackUrl"), props.getCallbackUrl());
        String dataType = firstNonBlank(params.get("dataType"), props.getDataType());
        String filename = props.getFilePrefix() + TS.format(LocalDateTime.now()) + props.getFileSuffix();
        byte[] payloadBytes = loadSample(bytesOrNull(file), body);

        Map<String, Object> requestEcho = Map.of(
            "params", params,
            "body", body,
            "callbackUrl", callbackUrl,
            "dataType", dataType,
            "sentFilename", filename
        );

        CallbackResult cb = sendCallback(callbackUrl, dataType, filename, payloadBytes);
        LOG.info("mock.pull received params={} body={} -> callback status={} msg={}", params, body, cb.status, cb.message);
        return ResponseEntity.ok(
            Map.of(
                "status",
                "ok",
                "callbackStatus",
                cb.status,
                "callbackBody",
                cb.body,
                "echo",
                requestEcho
            )
        );
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
            if (StringUtils.isNotBlank(props.getCallbackToken())) {
                headers.add(props.getSignatureHeader(), props.getCallbackToken().trim());
            }

            ResponseEntity<String> resp = restTemplate.postForEntity(callbackUrl, new HttpEntity<>(form, headers), String.class);
            r.status = resp.getStatusCodeValue();
            r.body = resp.getBody();
            r.message = "sent";
        } catch (Exception e) {
            r.status = 500;
            r.message = e.getMessage();
        }
        return r;
    }

    private byte[] bytesOrNull(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }
        return file.getBytes();
    }

    private byte[] loadSample(byte[] uploaded, Map<String, Object> body) throws IOException {
        if (uploaded != null && uploaded.length > 0) {
            return uploaded;
        }
        // 如果 body 里有 json 字符串则优先使用
        if (body != null && !body.isEmpty()) {
            return objectMapper.writeValueAsBytes(body);
        }
        Resource res = resourceLoader.getResource(props.getSampleLocation());
        if (res.exists()) {
            try {
                return res.getInputStream().readAllBytes();
            } catch (Exception e) {
                LOG.warn("load sample failed: {}", e.getMessage());
            }
        }
        // fallback 内置示例
        String fallback = """
            {"users":[{"userCode":"A1001","userName":"张三","deptCode":"D001"}],"depts":[{"deptCode":"D001","deptName":"研发部","parentCode":"ROOT"}]}
            """;
        return fallback.getBytes(StandardCharsets.UTF_8);
    }

    private String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (StringUtils.isNotBlank(v)) {
                return v.trim();
            }
        }
        return null;
    }

    private static class CallbackResult {
        int status;
        String message;
        String body;
    }
}
