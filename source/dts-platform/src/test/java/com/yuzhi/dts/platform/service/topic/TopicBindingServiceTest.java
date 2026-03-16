package com.yuzhi.dts.platform.service.topic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping;
import com.yuzhi.dts.platform.domain.topic.TopicBinding;
import com.yuzhi.dts.platform.domain.topic.TopicTemplate;
import com.yuzhi.dts.platform.domain.topic.TopicTemplateEntity;
import com.yuzhi.dts.platform.repository.infra.InfraOdsTableMappingRepository;
import com.yuzhi.dts.platform.repository.topic.TopicBindingRepository;
import com.yuzhi.dts.platform.repository.topic.TopicTemplateEntityRepository;
import com.yuzhi.dts.platform.repository.topic.TopicTemplateRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TopicBindingServiceTest {

    @Mock
    private TopicTemplateRepository templateRepository;

    @Mock
    private TopicTemplateEntityRepository entityRepository;

    @Mock
    private TopicBindingRepository bindingRepository;

    @Mock
    private InfraOdsTableMappingRepository odsTableMappingRepository;

    @InjectMocks
    private TopicBindingService topicBindingService;

    @Captor
    private ArgumentCaptor<TopicBinding> bindingCaptor;

    @Test
    void bindOdsTable_shouldUsePhysicalSchemaFromMappingWhenMappingIdProvided() {
        TopicTemplate template = new TopicTemplate();
        template.setId(UUID.randomUUID());
        template.setTemplateCode("project-management");

        TopicTemplateEntity entity = new TopicTemplateEntity();
        entity.setId(UUID.randomUUID());
        entity.setTemplateId(template.getId());
        entity.setEntityCode("project_subject_domain");
        entity.setExpectedSchema("ods");
        entity.setTableName("project_subject_domain");

        UUID mappingId = UUID.randomUUID();
        InfraOdsTableMapping mapping = new InfraOdsTableMapping();
        mapping.setId(mappingId);
        mapping.setEnabled(Boolean.TRUE);
        mapping.setOdsSchema("public");
        mapping.setOdsTable("ods_prj_prjtest2000");

        when(templateRepository.findByTemplateCodeIgnoreCase("project-management")).thenReturn(Optional.of(template));
        when(entityRepository.findFirstByTemplateIdAndEntityCodeIgnoreCase(template.getId(), "project_subject_domain"))
            .thenReturn(Optional.of(entity));
        when(bindingRepository.findByTemplateIdAndEntityIdAndScopeKey(template.getId(), entity.getId(), "GLOBAL"))
            .thenReturn(Optional.empty());
        when(odsTableMappingRepository.findById(mappingId)).thenReturn(Optional.of(mapping));
        when(bindingRepository.save(bindingCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        TopicBinding binding = topicBindingService.bindOdsTable(
            new TopicBindingService.BindOdsTableRequest(
                "project-management",
                "project_subject_domain",
                null,
                "ods",
                "ods_prj_prjtest2000",
                mappingId,
                null,
                "tester",
                "bind from unit test"
            )
        );

        assertThat(bindingCaptor.getValue().getSchemaName()).isEqualTo("public");
        assertThat(bindingCaptor.getValue().getTableName()).isEqualTo("ods_prj_prjtest2000");
        assertThat(binding.getSchemaName()).isEqualTo("public");
    }
}
