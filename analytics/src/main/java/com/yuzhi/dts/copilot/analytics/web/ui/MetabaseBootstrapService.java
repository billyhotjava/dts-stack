package com.yuzhi.dts.copilot.analytics.web.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.copilot.analytics.config.ApplicationProperties;
import com.yuzhi.dts.copilot.analytics.service.SetupStateService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseLocale;
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
    private final SetupStateService setupStateService;

    private volatile ObjectNode bootstrapTemplate;
    private volatile JsonNode userLocalizationTemplate;
    private volatile JsonNode siteLocalizationTemplate;

    public MetabaseBootstrapService(
            ObjectMapper mapper,
            ApplicationProperties applicationProperties,
            SetupStateService setupStateService) {
        this.mapper = mapper;
        this.applicationProperties = applicationProperties;
        this.setupStateService = setupStateService;
    }

    @PostConstruct
    void loadTemplates() throws IOException {
        bootstrapTemplate = (ObjectNode)
                mapper.readTree(readClasspathText("metabase/bootstrap/bootstrap-default.json"));
        userLocalizationTemplate =
                mapper.readTree(readClasspathText("metabase/bootstrap/user-localization-default.json"));
        siteLocalizationTemplate =
                mapper.readTree(readClasspathText("metabase/bootstrap/site-localization-default.json"));
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
        boolean setupCompleted = setupStateService.isSetupCompleted();
        root.put("has-user-setup", setupCompleted);
        if (setupCompleted) {
            root.putNull("setup-token");
        } else {
            root.put("setup-token", setupStateService.getOrCreateSetupToken());
        }
        root.put("startup-time-millis", 0.0);
        root.put("startup-time", OffsetDateTime.now().toString());

        String locale = MetabaseLocale.resolve(request);
        root.put("site-locale", locale);

        ArrayNode availableLocales = mapper.createArrayNode();
        availableLocales.add(mapper.createArrayNode().add("zh").add("Chinese"));
        availableLocales.add(mapper.createArrayNode().add("en").add("English"));
        root.set("available-locales", availableLocales);

        if (root.has("version") && root.get("version").isObject()) {
            ObjectNode version = (ObjectNode) root.get("version");
            String appVersion = applicationProperties.service().version();
            version.put("tag", appVersion == null ? "v0.0.0" : "v" + appVersion);
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
