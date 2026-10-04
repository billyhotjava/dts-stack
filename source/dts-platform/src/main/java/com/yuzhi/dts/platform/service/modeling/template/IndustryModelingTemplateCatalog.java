package com.yuzhi.dts.platform.service.modeling.template;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.template.IndustryModelingTemplateContract.IndustryModelingTemplate;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/** Read-only catalog of optional modeling templates shipped as classpath resources. */
@Service
public class IndustryModelingTemplateCatalog {

    private static final String INDEX_RESOURCE = "config/modeling-templates/index.json";

    private final List<IndustryModelingTemplate> templates;
    private final Map<String, IndustryModelingTemplate> templatesById;

    public IndustryModelingTemplateCatalog(ObjectMapper objectMapper) {
        TemplateIndex index = read(objectMapper, INDEX_RESOURCE, TemplateIndex.class);
        if (index.contractVersion() != IndustryModelingTemplateContract.VERSION) {
            throw new IllegalStateException("Unsupported modeling template index version: " + index.contractVersion());
        }

        Map<String, IndustryModelingTemplate> loaded = new LinkedHashMap<>();
        for (TemplateDescriptor descriptor : index.templates()) {
            IndustryModelingTemplate template = read(objectMapper, descriptor.resource(), IndustryModelingTemplate.class);
            if (!descriptor.templateId().equals(template.templateId()) || !descriptor.version().equals(template.version())) {
                throw new IllegalStateException("Modeling template descriptor does not match resource: " + descriptor.resource());
            }
            if (loaded.putIfAbsent(template.templateId(), template) != null) {
                throw new IllegalStateException("Duplicate modeling template id: " + template.templateId());
            }
            long distinctDiagnosticModels = template.diagnosticRules().stream().map(rule -> rule.modelName()).distinct().count();
            if (distinctDiagnosticModels != template.diagnosticRules().size()) {
                throw new IllegalStateException("Duplicate model diagnostic rule in template: " + template.templateId());
            }
        }
        templatesById = Map.copyOf(loaded);
        templates = List.copyOf(loaded.values());
    }

    public List<IndustryModelingTemplate> listTemplates() {
        return templates;
    }

    public IndustryModelingTemplate requireTemplate(String templateId) {
        String normalized = templateId == null ? "" : templateId.trim().toLowerCase(Locale.ROOT);
        IndustryModelingTemplate template = templatesById.get(normalized);
        if (template == null) throw new IllegalArgumentException("Unknown modeling template: " + templateId);
        return template;
    }

    /** Template extensions are resolved only after a caller explicitly selects a template. */
    public Optional<IndustryModelingTemplateContract.ModelDiagnosticRule> findDiagnosticRule(String templateId, String modelName) {
        String normalized = modelName == null ? "" : modelName.trim().toLowerCase(Locale.ROOT);
        return requireTemplate(templateId).diagnosticRules().stream().filter(rule -> rule.modelName().equals(normalized)).findFirst();
    }

    private static <T> T read(ObjectMapper objectMapper, String resourcePath, Class<T> type) {
        try (InputStream input = new ClassPathResource(resourcePath).getInputStream()) {
            return objectMapper.readValue(input, type);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load modeling template resource: " + resourcePath, exception);
        }
    }

    private record TemplateIndex(int contractVersion, List<TemplateDescriptor> templates) {
        private TemplateIndex {
            templates = List.copyOf(templates);
        }
    }

    private record TemplateDescriptor(String templateId, String version, String resource) {}
}
