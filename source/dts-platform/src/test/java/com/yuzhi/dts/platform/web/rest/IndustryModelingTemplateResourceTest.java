package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.template.IndustryModelingTemplateCatalog;
import com.yuzhi.dts.platform.service.modeling.template.IndustryModelingTemplateContract;
import com.yuzhi.dts.platform.service.modeling.template.IndustryModelingTemplateContract.IndustryModelingTemplate;
import com.yuzhi.dts.platform.service.modeling.template.IndustryModelingTemplateInstaller;
import com.yuzhi.dts.platform.service.modeling.template.IndustryModelingTemplateInstaller.InstallationResult;
import com.yuzhi.dts.platform.service.modeling.template.IndustryModelingTemplateInstaller.InstallationStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class IndustryModelingTemplateResourceTest {

    private final IndustryModelingTemplateCatalog catalog = mock(IndustryModelingTemplateCatalog.class);
    private final IndustryModelingTemplateInstaller installer = mock(IndustryModelingTemplateInstaller.class);
    private final AuditService audit = mock(AuditService.class);
    private final IndustryModelingTemplateResource resource = new IndustryModelingTemplateResource(catalog, installer, audit);

    @Test
    void listsAndResolvesOptionalTemplatesWithoutInstalling() {
        IndustryModelingTemplate template = template();
        when(catalog.listTemplates()).thenReturn(List.of(template));
        when(catalog.requireTemplate("example")).thenReturn(template);

        assertThat(resource.listTemplates().getData()).containsExactly(template);
        assertThat(resource.getTemplate("example").getData()).isEqualTo(template);
    }

    @Test
    void explicitlyInstallsSelectedTemplateIntoSelectedDomain() {
        UUID domainId = UUID.randomUUID();
        IndustryModelingTemplate template = template();
        InstallationResult installed = new InstallationResult(domainId, "example", "1", InstallationStatus.INSTALLED, 1, 1);
        when(catalog.requireTemplate("example")).thenReturn(template);
        when(installer.install(domainId, template)).thenReturn(installed);

        assertThat(resource.installTemplate("example", domainId).getData()).isEqualTo(installed);
        verify(installer).install(domainId, template);
    }

    @Test
    void mapsUnknownTemplateToNotFound() {
        when(catalog.requireTemplate("missing")).thenThrow(new IllegalArgumentException("Unknown modeling template: missing"));

        assertThatThrownBy(() -> resource.getTemplate("missing"))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private static IndustryModelingTemplate template() {
        return new IndustryModelingTemplate(
            IndustryModelingTemplateContract.VERSION,
            "example",
            "1",
            "通用示例",
            null,
            null,
            true,
            IndustryModelingTemplateContract.EXPLICIT_INSTALLATION,
            List.of(new IndustryModelingTemplateContract.BusinessProcessTemplate("sample-process", "示例过程", null)),
            List.of(new IndustryModelingTemplateContract.ConformedDimensionTemplate("sample-dimension", "示例维度", null)),
            List.of()
        );
    }
}
