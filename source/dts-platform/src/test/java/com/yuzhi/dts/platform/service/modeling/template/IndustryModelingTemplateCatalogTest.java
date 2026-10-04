package com.yuzhi.dts.platform.service.modeling.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class IndustryModelingTemplateCatalogTest {

    private final IndustryModelingTemplateCatalog catalog = new IndustryModelingTemplateCatalog(new ObjectMapper());

    @Test
    void listsOptionalTemplatesWithoutInstallingAnything() {
        var templates = catalog.listTemplates();

        assertThat(templates).hasSize(1);
        assertThat(templates.getFirst().templateId()).isEqualTo("pjm");
        assertThat(templates.getFirst().optional()).isTrue();
        assertThat(templates.getFirst().installationMode()).isEqualTo("EXPLICIT");
    }

    @Test
    void resolvesVersionedPjmTemplateFromOptionalResourcePack() {
        var template = catalog.requireTemplate("pjm");

        assertThat(template.contractVersion()).isEqualTo(IndustryModelingTemplateContract.VERSION);
        assertThat(template.version()).isEqualTo("1");
        assertThat(template.businessProcesses()).extracting(IndustryModelingTemplateContract.BusinessProcessTemplate::processId).contains("node-plan-loop");
        assertThat(template.conformedDimensions()).extracting(IndustryModelingTemplateContract.ConformedDimensionTemplate::dimensionId).contains("completion-status");
    }

    @Test
    void rejectsUnknownTemplateId() {
        assertThatThrownBy(() -> catalog.requireTemplate("missing")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("missing");
    }

    @Test
    void exposesOptionalModelDiagnosticsOnlyForAnExplicitTemplateSelection() {
        var rule = catalog.findDiagnosticRule("pjm", "biz_dws_progress_monthly_v2").orElseThrow();

        assertThat(rule.findingsWhenEmptyWithNonEmptyUpstream()).anyMatch(item -> item.contains("plan_year"));
        assertThat(rule.recommendedQueries()).anyMatch(item -> item.contains("biz_dwd_project_node_v2"));
        assertThat(catalog.findDiagnosticRule("pjm", "generic_model")).isEmpty();
    }
}
