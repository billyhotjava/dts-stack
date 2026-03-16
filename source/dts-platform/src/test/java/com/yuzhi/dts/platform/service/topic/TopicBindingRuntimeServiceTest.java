package com.yuzhi.dts.platform.service.topic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.topic.TopicBinding;
import com.yuzhi.dts.platform.domain.topic.TopicTemplate;
import com.yuzhi.dts.platform.domain.topic.TopicTemplateEntity;
import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import com.yuzhi.dts.platform.repository.topic.TopicBindingRepository;
import com.yuzhi.dts.platform.repository.topic.TopicTemplateEntityRepository;
import com.yuzhi.dts.platform.repository.topic.TopicTemplateRepository;
import com.yuzhi.dts.platform.service.etl.DbtConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TopicBindingRuntimeServiceTest {

    @Mock
    private DbtConfigService dbtConfigService;

    @Mock
    private TopicTemplateRepository templateRepository;

    @Mock
    private TopicTemplateEntityRepository entityRepository;

    @Mock
    private TopicBindingRepository bindingRepository;

    @Mock
    private ModelingSqlModelRepository modelingSqlModelRepository;

    @TempDir
    Path tempDir;

    @Test
    void compileRuntimeArtifacts_shouldAvoidDuplicateStaticProjectManagementSourceAndWriteCompatibilityVars() throws Exception {
        Path projectDir = tempDir.resolve("dbt");
        Files.createDirectories(projectDir.resolve("models"));
        Files.createDirectories(projectDir.resolve("target"));
        when(dbtConfigService.loadConfig()).thenReturn(configView(projectDir));

        TopicTemplate template = template("project-management", "项目管理专题");
        TopicTemplateEntity entity = entity(template, "project_subject_domain", "pm_ods", "project_subject_domain", true);
        TopicBinding binding = binding(template, entity, "ods", "ods_pm_project_20260316");

        when(templateRepository.findAll()).thenReturn(List.of(template));
        when(entityRepository.findAll()).thenReturn(List.of(entity));
        when(bindingRepository.findAll()).thenReturn(List.of(binding));
        TopicBindingRuntimeService service = new TopicBindingRuntimeService(
            dbtConfigService,
            templateRepository,
            entityRepository,
            bindingRepository,
            modelingSqlModelRepository,
            new ObjectMapper()
        );

        TopicBindingRuntimeService.RuntimeCompilationResult result = service.compileRuntimeArtifacts();

        assertThat(result.enabled()).isTrue();
        assertThat(result.activeBindings()).isEqualTo(1);
        assertThat(result.path()).isEqualTo(projectDir.resolve("models/__topic_bindings/topic_sources.yml").toString());
        assertThat(Files.readString(projectDir.resolve("models/__topic_bindings/topic_sources.yml")))
            .isEqualTo("version: 2\nsources: []\n");
        assertThat(Files.readString(projectDir.resolve("target/topic_binding_vars.json")))
            .contains("project-management")
            .contains("project_subject_domain")
            .contains("ods_pm_project_20260316");
        assertThat(result.vars()).containsKey("topic_bindings");
        assertThat(result.vars())
            .containsEntry("project_management_ods_schema", "ods")
            .containsEntry("project_management_ods_table", "ods_pm_project_20260316");
    }

    @Test
    void diagnose_shouldReportMissingRequiredBindingsForSelectedTopic() {
        TopicTemplate template = template("project-management", "项目管理专题");
        TopicTemplateEntity entity = entity(template, "project_subject_domain", "pm_ods", "project_subject_domain", true);

        when(templateRepository.findAll()).thenReturn(List.of(template));
        when(entityRepository.findAll()).thenReturn(List.of(entity));
        when(bindingRepository.findAll()).thenReturn(List.of());

        TopicBindingRuntimeService service = new TopicBindingRuntimeService(
            dbtConfigService,
            templateRepository,
            entityRepository,
            bindingRepository,
            modelingSqlModelRepository,
            new ObjectMapper()
        );

        TopicBindingRuntimeService.BindingDiagnostics diagnostics = service.diagnose("tag:project-management");

        assertThat(diagnostics.relevantTemplateCodes()).containsExactly("project-management");
        assertThat(diagnostics.missingRequired()).containsExactly("project-management.project_subject_domain");
        assertThat(diagnostics.rows()).singleElement().satisfies(row -> {
            assertThat(row.templateCode()).isEqualTo("project-management");
            assertThat(row.entityCode()).isEqualTo("project_subject_domain");
            assertThat(row.bound()).isFalse();
            assertThat(row.required()).isTrue();
        });
    }

    @Test
    void compileRuntimeArtifacts_shouldWriteEmptySourcesListWhenNoBindingsExist() throws Exception {
        Path projectDir = tempDir.resolve("dbt-empty");
        Files.createDirectories(projectDir.resolve("models"));
        Files.createDirectories(projectDir.resolve("target"));
        when(dbtConfigService.loadConfig()).thenReturn(configView(projectDir));
        when(templateRepository.findAll()).thenReturn(List.of());
        when(entityRepository.findAll()).thenReturn(List.of());
        when(bindingRepository.findAll()).thenReturn(List.of());

        TopicBindingRuntimeService service = new TopicBindingRuntimeService(
            dbtConfigService,
            templateRepository,
            entityRepository,
            bindingRepository,
            modelingSqlModelRepository,
            new ObjectMapper()
        );

        TopicBindingRuntimeService.RuntimeCompilationResult result = service.compileRuntimeArtifacts();

        assertThat(result.enabled()).isTrue();
        assertThat(Files.readString(projectDir.resolve("models/__topic_bindings/topic_sources.yml")))
            .isEqualTo("version: 2\nsources: []\n");
    }

    private DbtConfigService.DbtConfigView configView(Path projectDir) {
        DbtConfigService.DbtWorkspaceConfig config = new DbtConfigService.DbtWorkspaceConfig(
            true,
            projectDir.toString(),
            tempDir.resolve("profiles").toString(),
            "dts",
            "dev",
            null,
            null,
            null,
            Map.of()
        );
        return new DbtConfigService.DbtConfigView(
            true,
            config,
            DbtConfigService.DbtProfileStatus.skipped("test"),
            null,
            new DbtConfigService.DbtWorkspaceStatus(true, "ok", Map.of())
        );
    }

    private TopicTemplate template(String code, String name) {
        TopicTemplate template = new TopicTemplate();
        template.setId(UUID.randomUUID());
        template.setTemplateCode(code);
        template.setTemplateName(name);
        template.setStatus("ACTIVE");
        template.setBindingScope("GLOBAL");
        template.setEnabled(Boolean.TRUE);
        return template;
    }

    private TopicTemplateEntity entity(
        TopicTemplate template,
        String entityCode,
        String sourceName,
        String tableName,
        boolean required
    ) {
        TopicTemplateEntity entity = new TopicTemplateEntity();
        entity.setId(UUID.randomUUID());
        entity.setTemplateId(template.getId());
        entity.setEntityCode(entityCode);
        entity.setEntityName(entityCode);
        entity.setEntityType("ODS_TABLE");
        entity.setRequired(required);
        entity.setSourceName(sourceName);
        entity.setTableName(tableName);
        entity.setExpectedSchema("ods");
        return entity;
    }

    private TopicBinding binding(TopicTemplate template, TopicTemplateEntity entity, String schemaName, String tableName) {
        TopicBinding binding = new TopicBinding();
        binding.setId(UUID.randomUUID());
        binding.setTemplateId(template.getId());
        binding.setEntityId(entity.getId());
        binding.setScopeKey("GLOBAL");
        binding.setBindingMode("ODS_TABLE");
        binding.setSchemaName(schemaName);
        binding.setTableName(tableName);
        binding.setStatus("ACTIVE");
        binding.setBoundBy("tester");
        return binding;
    }
}
