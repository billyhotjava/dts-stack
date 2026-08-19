package com.yuzhi.dts.analytics.service.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsCard;
import com.yuzhi.dts.analytics.domain.AnalyticsRevision;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsRevisionRepository;
import com.yuzhi.dts.analytics.service.analysis.AnalysisApplicationService;
import com.yuzhi.dts.analytics.service.analysis.AnalysisQuerySpec;
import com.yuzhi.dts.analytics.service.analysis.AnalysisQuerySpecParser;
import com.yuzhi.dts.analytics.service.analysis.AnalysisQuerySpecValidator;
import com.yuzhi.dts.analytics.service.analysis.GovernedAnalysisDatasetContract;
import com.yuzhi.dts.analytics.service.analysis.GovernedAnalysisDatasetContractProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class AnalysisPublicationServiceTest {

    private AnalyticsCardRepository cards;
    private AnalyticsRevisionRepository revisions;
    private GovernedAnalysisDatasetContractProvider contracts;
    private PublicationEntityLock entityLock;
    private AnalysisPublicationService service;
    private AnalyticsCard card;
    private GovernedAnalysisDatasetContract contract;
    private AnalyticsUser actor;

    @BeforeEach
    void setUp() {
        cards = mock(AnalyticsCardRepository.class);
        revisions = mock(AnalyticsRevisionRepository.class);
        contracts = mock(GovernedAnalysisDatasetContractProvider.class);
        entityLock = mock(PublicationEntityLock.class);
        AnalysisQuerySpecParser parser = new AnalysisQuerySpecParser(new ObjectMapper());
        service = new AnalysisPublicationService(
            cards,
            revisions,
            contracts,
            parser,
            new AnalysisQuerySpecValidator(),
            mock(AnalysisApplicationService.class),
            entityLock,
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-08-17T06:00:00Z"), ZoneOffset.UTC)
        );
        contract = contract();
        card = analysisCard(parser, contract);
        actor = actor(7L);
        when(cards.findById(11L)).thenReturn(Optional.of(card));
        when(entityLock.analysis(11L)).thenReturn(card);
        when(contracts.get(contract.datasetId(), 1, "checksum-v1")).thenReturn(contract);
        when(revisions.findMaxVersionNo("analysis", 11L)).thenReturn(0);
        when(revisions.findCurrentPublished("analysis", 11L)).thenReturn(Optional.empty());
        when(cards.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(revisions.save(any())).thenAnswer(invocation -> {
            AnalyticsRevision revision = invocation.getArgument(0);
            revision.setId(91L);
            return revision;
        });
    }

    @Test
    void createsServiceFromSpringContextWhenClockTestSeamAlsoExists() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(AnalyticsCardRepository.class, () -> mock(AnalyticsCardRepository.class));
            context.registerBean(AnalyticsRevisionRepository.class, () -> mock(AnalyticsRevisionRepository.class));
            context.registerBean(
                GovernedAnalysisDatasetContractProvider.class,
                () -> mock(GovernedAnalysisDatasetContractProvider.class)
            );
            context.registerBean(AnalysisQuerySpecParser.class, () -> mock(AnalysisQuerySpecParser.class));
            context.registerBean(AnalysisQuerySpecValidator.class, () -> mock(AnalysisQuerySpecValidator.class));
            context.registerBean(AnalysisApplicationService.class, () -> mock(AnalysisApplicationService.class));
            context.registerBean(PublicationEntityLock.class, () -> mock(PublicationEntityLock.class));
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.register(AnalysisPublicationService.class);
            context.refresh();

            assertThat(context.getBean(AnalysisPublicationService.class)).isNotNull();
        }
    }

    @Test
    void validationBlocksAnEmptyAudienceWithoutChangingLifecycle() {
        AnalysisPublicationService.ValidationResult result = service.validate(
            11L,
            actor,
            new AnalysisPublicationService.PublicationCommand(List.of(), List.of(), "DATA_INTERNAL", null)
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.blockers()).extracting(AnalysisPublicationService.PublicationIssue::code)
            .contains("ANALYSIS_AUDIENCE_REQUIRED");
        assertThat(card.getLifecycleStatus()).isEqualTo("DRAFT");
        assertThat(card.getPublishedRevisionId()).isNull();
    }

    @Test
    void publishPinsContractAndAtomicallyAdvancesThePublishedPointer() {
        AnalysisPublicationService.PublicationResult result = service.publish(
            11L,
            actor,
            new AnalysisPublicationService.PublicationCommand(List.of("D1"), List.of("ROLE_ANALYST"), "DATA_INTERNAL", null)
        );

        assertThat(result.versionNo()).isEqualTo(1);
        assertThat(result.contractChecksum()).hasSize(64);
        assertThat(result.dependencySnapshot()).containsEntry("contractChecksum", "checksum-v1");
        assertThat(card.getLifecycleStatus()).isEqualTo("PUBLISHED");
        assertThat(card.getPublishedRevisionId()).isEqualTo(91L);
    }

    private static AnalyticsCard analysisCard(AnalysisQuerySpecParser parser, GovernedAnalysisDatasetContract contract) {
        AnalysisQuerySpec spec = new AnalysisQuerySpec(
            "dts.analysis/v1",
            new AnalysisQuerySpec.DatasetRef(contract.datasetId(), 1, "r1", "checksum-v1"),
            List.of(new AnalysisQuerySpec.DimensionSelection("project_code", null)),
            List.of(), List.of(), List.of(), null, List.of(), 100,
            new AnalysisQuerySpec.Visualization("table", Map.of())
        );
        AnalyticsCard value = new AnalyticsCard();
        value.setId(11L);
        value.setEntityId("analysis-11");
        value.setName("项目分析");
        value.setCardType("analysis");
        value.setLifecycleStatus("DRAFT");
        value.setCreatorId(7L);
        value.setDatabaseId(3L);
        value.setDatasetQueryJson(parser.write(spec));
        value.setVisualizationSettingsJson("{}");
        value.setDisplay("table");
        value.setQueryDatasetId(contract.datasetId());
        value.setQueryDatasetVersion(1);
        value.setSemanticContractVersion("r1");
        return value;
    }

    private static GovernedAnalysisDatasetContract contract() {
        return new GovernedAnalysisDatasetContract(
            UUID.randomUUID(), 1, "PUBLISHED", UUID.randomUUID(), "select project_code from ads_project",
            List.of(new GovernedAnalysisDatasetContract.Dimension(
                "project_code", "项目编码", "text", List.of(), "DATA_INTERNAL", List.of("EQ")
            )),
            List.of(), List.of(), List.of(), "DATA_INTERNAL", "r1", "checksum-v1"
        );
    }

    private static AnalyticsUser actor(long id) {
        AnalyticsUser value = new AnalyticsUser();
        value.setId(id);
        value.setEmail("owner@example.test");
        value.setActive(true);
        return value;
    }
}
