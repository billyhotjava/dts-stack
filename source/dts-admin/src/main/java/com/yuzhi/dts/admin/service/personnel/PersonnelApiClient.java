package com.yuzhi.dts.admin.service.personnel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.config.PersonnelSyncProperties;
import com.yuzhi.dts.admin.service.dto.personnel.PersonnelPayload;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class PersonnelApiClient {

    private static final Logger LOG = LoggerFactory.getLogger(PersonnelApiClient.class);
    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAP = new TypeReference<>() {};

    private final PersonnelSyncProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public PersonnelApiClient(PersonnelSyncProperties properties, RestTemplateBuilder builder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = builder
            .setConnectTimeout(properties.getApi().getConnectTimeout())
            .setReadTimeout(properties.getApi().getReadTimeout())
            .build();
    }

    public ApiFetchResult fetch(String cursor) {
        if (!properties.getApi().isEnabled()) {
            throw new PersonnelImportException("人员主数据 API 尚未启用");
        }
        String endpoint = properties.getApi().getEndpoint();
        if (!StringUtils.hasText(endpoint)) {
            throw new PersonnelImportException("未配置人员主数据 API 地址");
        }
        HttpMethod method = properties.getApi().getMethod() == null ? HttpMethod.GET : properties.getApi().getMethod();
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (StringUtils.hasText(properties.getApi().getAuthToken())) {
            headers.set(HttpHeaders.AUTHORIZATION, properties.getApi().getAuthToken());
        }
        properties.getApi().getHeaders().forEach(headers::set);
        URI uri = buildUri(endpoint, method, cursor);
        Object body = method == HttpMethod.GET ? null : buildBody(cursor);
        HttpEntity<Object> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(uri, method, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new PersonnelImportException("同步失败，HTTP " + response.getStatusCode());
            }
            String payload = response.getBody();
            List<PersonnelPayload> records = parsePayload(payload);
            String nextCursor = extractNextCursor(payload);
            LOG.info("Fetched {} personnel records via API cursor={} next={}", records.size(), cursor, nextCursor);
            return new ApiFetchResult(records, nextCursor);
        } catch (HttpStatusCodeException ex) {
            throw new PersonnelImportException("同步失败，HTTP " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString(), ex);
        } catch (PersonnelImportException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PersonnelImportException("同步失败:" + ex.getMessage(), ex);
        }
    }

    private URI buildUri(String endpoint, HttpMethod method, String cursor) {
        if (method == HttpMethod.GET && StringUtils.hasText(cursor)) {
            return UriComponentsBuilder.fromUriString(endpoint).queryParam("cursor", cursor).build(true).toUri();
        }
        return UriComponentsBuilder.fromUriString(endpoint).build(true).toUri();
    }

    private Map<String, Object> buildBody(String cursor) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (StringUtils.hasText(cursor)) {
            body.put("cursor", cursor);
        }
        return body;
    }

    private List<PersonnelPayload> parsePayload(String body) throws Exception {
        if (!StringUtils.hasText(body)) {
            return List.of();
        }
        String trimmed = body.trim();
        List<Map<String, Object>> rawRecords = new ArrayList<>();
        if (trimmed.startsWith("[")) {
            rawRecords.addAll(objectMapper.readValue(trimmed, LIST_OF_MAP));
        } else if (trimmed.startsWith("{")) {
            JsonNode root = objectMapper.readTree(trimmed);
            JsonNode dataNode = root.path("data");
            if (dataNode.isMissingNode() || dataNode.isNull()) {
                dataNode = root.path("records");
            }
            if (dataNode.isArray()) {
                rawRecords.addAll(objectMapper.convertValue(dataNode, LIST_OF_MAP));
            } else {
                rawRecords.add(objectMapper.convertValue(root, new TypeReference<Map<String, Object>>() {}));
            }
        }
        return rawRecords.stream().map(PersonnelPayloadMapper::fromMap).toList();
    }

    private String extractNextCursor(String body) {
        if (!StringUtils.hasText(body) || body.trim().startsWith("[")) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            List<String> candidates = List.of("nextCursor", "next_cursor", "cursor", "next_token");
            for (String key : candidates) {
                JsonNode node = root.path(key);
                if (!node.isMissingNode() && node.isTextual() && StringUtils.hasText(node.asText())) {
                    return node.asText();
                }
            }
        } catch (Exception ex) {
            LOG.debug("Failed to parse next cursor: {}", ex.getMessage());
        }
        return null;
    }

    public record ApiFetchResult(List<PersonnelPayload> records, String nextCursor) {}
}
