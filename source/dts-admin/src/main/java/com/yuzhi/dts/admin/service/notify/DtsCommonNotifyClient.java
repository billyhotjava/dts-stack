package com.yuzhi.dts.admin.service.notify;

import java.net.URI;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class DtsCommonNotifyClient {

    private static final Logger log = LoggerFactory.getLogger(DtsCommonNotifyClient.class);

    @Value("${dts.common.notify.enabled:false}")
    private boolean enabled;

    @Value("${dts.common.notify.base-url:}")
    private String baseUrl;
    @Value("${dts.common.notify.token:}")
    private String token;

    private final RestTemplate http = new RestTemplate();

    public void trySend(String template, Map<String, Object> variables) {
        if (!enabled || baseUrl == null || baseUrl.isBlank()) return;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (token != null && !token.isBlank()) {
                headers.setBearerAuth(token);
            }
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(Map.of("template", template, "variables", variables), headers);
            ResponseEntity<Void> resp = http.postForEntity(URI.create(baseUrl + "/api/notify"), entity, Void.class);
            if (!resp.getStatusCode().is2xxSuccessful()) log.warn("Notify failed: {}", resp.getStatusCode());
        } catch (Exception e) {
            log.warn("Notify error: {}", e.toString());
        }
    }
}
