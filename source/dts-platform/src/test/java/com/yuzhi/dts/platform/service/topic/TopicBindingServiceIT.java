package com.yuzhi.dts.platform.service.topic;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.domain.topic.TopicBinding;
import com.yuzhi.dts.platform.domain.topic.TopicTemplate;
import com.yuzhi.dts.platform.domain.topic.TopicTemplateEntity;
import com.yuzhi.dts.platform.repository.topic.TopicBindingRepository;
import com.yuzhi.dts.platform.repository.topic.TopicTemplateEntityRepository;
import com.yuzhi.dts.platform.repository.topic.TopicTemplateRepository;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
@Transactional
class TopicBindingServiceIT {

    @Autowired
    private TopicTemplateBootstrapService bootstrapService;

    @Autowired
    private TopicBindingService topicBindingService;

    @Autowired
    private TopicTemplateRepository topicTemplateRepository;

    @Autowired
    private TopicTemplateEntityRepository topicTemplateEntityRepository;

    @Autowired
    private TopicBindingRepository topicBindingRepository;

    @Test
    void bootstrapDefaultsShouldSeedProjectManagementAndPlmTemplates() {
        bootstrapService.bootstrapDefaults();

        Optional<TopicTemplate> projectManagement = topicTemplateRepository.findByTemplateCodeIgnoreCase("project-management");
        Optional<TopicTemplate> plmOverview = topicTemplateRepository.findByTemplateCodeIgnoreCase("plm-overview");

        assertThat(projectManagement).isPresent();
        assertThat(projectManagement.orElseThrow().getBindingScope()).isEqualTo("GLOBAL");
        assertThat(plmOverview).isPresent();

        assertThat(topicTemplateEntityRepository.findByTemplateIdOrderByEntityCodeAsc(projectManagement.orElseThrow().getId()))
            .extracting(TopicTemplateEntity::getEntityCode)
            .contains("project_subject_domain");

        assertThat(topicTemplateEntityRepository.findByTemplateIdOrderByEntityCodeAsc(plmOverview.orElseThrow().getId()))
            .extracting(TopicTemplateEntity::getEntityCode)
            .contains("plm_bom_domain", "plm_change_domain");
    }

    @Test
    void bindOdsTableShouldPersistGlobalTopicBinding() {
        bootstrapService.bootstrapDefaults();
        TopicTemplate template = topicTemplateRepository.findByTemplateCodeIgnoreCase("project-management").orElseThrow();
        TopicTemplateEntity entity = topicTemplateEntityRepository
            .findFirstByTemplateIdAndEntityCodeIgnoreCase(template.getId(), "project_subject_domain")
            .orElseThrow();

        UUID dataSourceId = UUID.randomUUID();
        UUID odsMappingId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();

        TopicBinding binding = topicBindingService.bindOdsTable(
            new TopicBindingService.BindOdsTableRequest(
                template.getTemplateCode(),
                entity.getEntityCode(),
                dataSourceId,
                "ods",
                "pm_upload_20260316",
                odsMappingId,
                batchId,
                "tester",
                "bind from test"
            )
        );

        assertThat(binding.getId()).isNotNull();
        assertThat(binding.getTemplateId()).isEqualTo(template.getId());
        assertThat(binding.getEntityId()).isEqualTo(entity.getId());
        assertThat(binding.getBindingMode()).isEqualTo("ODS_TABLE");
        assertThat(binding.getSchemaName()).isEqualTo("ods");
        assertThat(binding.getTableName()).isEqualTo("pm_upload_20260316");
        assertThat(binding.getStatus()).isEqualTo("ACTIVE");
        assertThat(binding.getScopeKey()).isEqualTo("GLOBAL");
        assertThat(binding.getBoundBy()).isEqualTo("tester");

        assertThat(topicBindingRepository.findByTemplateIdAndEntityIdAndScopeKey(template.getId(), entity.getId(), "GLOBAL"))
            .isPresent()
            .get()
            .extracting(TopicBinding::getTableName)
            .isEqualTo("pm_upload_20260316");
    }
}
