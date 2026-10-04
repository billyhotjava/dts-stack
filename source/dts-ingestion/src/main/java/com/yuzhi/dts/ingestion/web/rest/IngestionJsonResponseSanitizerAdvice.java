package com.yuzhi.dts.ingestion.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.service.security.IngestionSensitiveConfigSupport;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Recursively removes managed secrets from every JSON response in ingestion REST APIs. */
@RestControllerAdvice(basePackages = "com.yuzhi.dts.ingestion.web.rest")
public class IngestionJsonResponseSanitizerAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper;

    public IngestionJsonResponseSanitizerAdvice(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(
        MethodParameter returnType,
        Class<? extends HttpMessageConverter<?>> converterType
    ) {
        return MappingJackson2HttpMessageConverter.class.isAssignableFrom(converterType);
    }

    @Override
    public Object beforeBodyWrite(
        Object body,
        MethodParameter returnType,
        MediaType selectedContentType,
        Class<? extends HttpMessageConverter<?>> selectedConverterType,
        ServerHttpRequest request,
        ServerHttpResponse response
    ) {
        if (body == null) {
            return null;
        }
        try {
            JsonNode tree = body instanceof JsonNode jsonNode ? jsonNode : objectMapper.valueToTree(body);
            return IngestionSensitiveConfigSupport.sanitize(tree);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("INGESTION_RESPONSE_SANITIZATION_FAILED", ex);
        }
    }
}
