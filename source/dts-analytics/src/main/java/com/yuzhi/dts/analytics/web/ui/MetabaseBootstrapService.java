package com.yuzhi.dts.analytics.web.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.analytics.config.ApplicationProperties;
import com.yuzhi.dts.analytics.config.MetabaseUiProperties;
import com.yuzhi.dts.analytics.service.SetupStateService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class MetabaseBootstrapService {

    private final ObjectMapper mapper;
    private final ApplicationProperties applicationProperties;
    private final MetabaseUiProperties uiProperties;
    private final SetupStateService setupStateService;

    private volatile ObjectNode bootstrapTemplate;
    private volatile JsonNode userLocalizationTemplate;
    private volatile JsonNode siteLocalizationTemplate;

    public MetabaseBootstrapService(
            ObjectMapper mapper,
            ApplicationProperties applicationProperties,
            MetabaseUiProperties uiProperties,
            SetupStateService setupStateService) {
        this.mapper = mapper;
        this.applicationProperties = applicationProperties;
        this.uiProperties = uiProperties;
        this.setupStateService = setupStateService;
    }

    @PostConstruct
    void loadTemplates() throws IOException {
        String bundleVersion = uiProperties.bundleVersion();
        bootstrapTemplate = (ObjectNode)
                mapper.readTree(readClasspathText("metabase/ui/v%s/bootstrap-default.json".formatted(bundleVersion)));
        userLocalizationTemplate =
                mapper.readTree(readClasspathText("metabase/ui/v%s/user-localization-default.json".formatted(bundleVersion)));
        siteLocalizationTemplate =
                mapper.readTree(readClasspathText("metabase/ui/v%s/site-localization-default.json".formatted(bundleVersion)));
    }

    public ObjectNode buildBootstrap(HttpServletRequest request) {
        ObjectNode root = bootstrapTemplate.deepCopy();

        String baseHref = ExternalPathResolver.resolveBaseHref(request);
        String siteUrl =
                "%s://%s%s".formatted(
                        ExternalPathResolver.resolveExternalScheme(request),
                        ExternalPathResolver.resolveExternalHost(request),
                        baseHref);

        root.put("site-url", siteUrl);
        String defaultName = applicationProperties.service().name() == null ? "Metabase" : applicationProperties.service().name();
        String siteName = setupStateService.getSiteName().orElse(defaultName);
        root.put("site-name", siteName);
        root.put("application-name", siteName);
        root.put("anon-tracking-enabled", false);
        root.put("setup-token", setupStateService.getOrCreateSetupToken());
        root.put("has-user-setup", setupStateService.isSetupCompleted());
        root.put("startup-time-millis", 0.0);
        root.put("startup-time", OffsetDateTime.now().toString());

        if (root.has("version") && root.get("version").isObject()) {
            ObjectNode version = (ObjectNode) root.get("version");
            version.put("tag", "v" + uiProperties.targetVersion());
        }
        return root;
    }

    public JsonNode userLocalization() {
        return userLocalizationTemplate;
    }

    public JsonNode siteLocalization() {
        return siteLocalizationTemplate;
    }

    public String setupToken() {
        return setupStateService.getOrCreateSetupToken();
    }

    private String readClasspathText(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        try (var in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
