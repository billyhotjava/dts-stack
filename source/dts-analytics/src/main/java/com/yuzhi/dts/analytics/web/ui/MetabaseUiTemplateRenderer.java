package com.yuzhi.dts.analytics.web.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.config.ApplicationProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class MetabaseUiTemplateRenderer {

    private final MetabaseBootstrapService bootstrapService;
    private final ObjectMapper mapper;
    private final ApplicationProperties applicationProperties;

    public MetabaseUiTemplateRenderer(
            MetabaseBootstrapService bootstrapService, ObjectMapper mapper, ApplicationProperties applicationProperties) {
        this.bootstrapService = bootstrapService;
        this.mapper = mapper;
        this.applicationProperties = applicationProperties;
    }

    public String renderIndex(HttpServletRequest request) throws IOException {
        String template = readStatic("index.html");
        String baseHref = ExternalPathResolver.resolveBaseHref(request);

        String html = template;
        html = replaceBlock(html, "{{#enableAnonTracking}}", "{{/enableAnonTracking}}", "");
        html = html.replace("{{{language}}}", "en");
        html = html.replace("{{{favicon}}}", "app/assets/img/favicon.ico");
        html = html.replace("{{{baseHref}}}", baseHref);
        html = html.replace("{{{uri}}}", baseHref);
        html = html.replace("{{{embedCode}}}", "");
        html = html.replace("{{{applicationName}}}", resolveApplicationName());
        html = html.replace("{{{bootstrapJSON}}}", bootstrapJson(request));
        html = html.replace("{{{userLocalizationJSON}}}", userLocalizationJson());
        html = html.replace("{{{siteLocalizationJSON}}}", siteLocalizationJson());
        html = html.replace("{{{bootstrapJS}}}", readStatic("inline_js/index_bootstrap.js"));
        html = html.replace("{{{googleAnalyticsJS}}}", "");
        return html;
    }

    private String resolveApplicationName() {
        ApplicationProperties.Service service = applicationProperties.service();
        if (service != null && service.name() != null && !service.name().isBlank()) {
            return service.name();
        }
        return "Metabase";
    }

    private String bootstrapJson(HttpServletRequest request) throws JsonProcessingException {
        return mapper.writeValueAsString(bootstrapService.buildBootstrap(request));
    }

    private String userLocalizationJson() throws JsonProcessingException {
        return mapper.writeValueAsString(bootstrapService.userLocalization());
    }

    private String siteLocalizationJson() throws JsonProcessingException {
        return mapper.writeValueAsString(bootstrapService.siteLocalization());
    }

    private String replaceBlock(String input, String startToken, String endToken, String replacement) {
        int start = input.indexOf(startToken);
        if (start < 0) {
            return input;
        }
        int end = input.indexOf(endToken, start);
        if (end < 0) {
            return input;
        }
        return input.substring(0, start) + replacement + input.substring(end + endToken.length());
    }

    private String readStatic(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource("static/" + path);
        try (var in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
