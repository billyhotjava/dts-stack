package com.yuzhi.dts.analytics.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsCard;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.analytics.service.EntityIdGenerator;
import com.yuzhi.dts.analytics.service.analysis.AnalysisApplicationService.CreateAnalysisCommand;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalysisApplicationServiceTest {

    @Mock
    private AnalyticsCardRepository cardRepository;

    @Mock
    private GovernedAnalysisDatasetContractProvider contractProvider;

    @Mock
    private AnalyticsDatabaseBindingResolver databaseBindingResolver;

    @Mock
    private EntityIdGenerator entityIdGenerator;

    private AnalysisApplicationService service;
    private GovernedAnalysisDatasetContract contract;
    private AnalyticsUser actor;

    @BeforeEach
    void setUp() {
        AnalysisQuerySpecParser parser = new AnalysisQuerySpecParser(new ObjectMapper());
        service = new AnalysisApplicationService(
            cardRepository,
            contractProvider,
            databaseBindingResolver,
            entityIdGenerator,
            parser,
            new AnalysisQuerySpecValidator()
        );
        contract = contract();
        actor = actor();
        lenient().when(entityIdGenerator.newEntityId()).thenReturn("analysis_entity_001");
        lenient().when(databaseBindingResolver.requireDatabaseId(contract.sourceDatasourceId())).thenReturn(9L);
        lenient().when(contractProvider.get(contract.datasetId(), contract.version(), contract.contractChecksum())).thenReturn(contract);
        lenient().when(cardRepository.save(any(AnalyticsCard.class))).thenAnswer(invocation -> {
            AnalyticsCard card = invocation.getArgument(0);
            if (card.getId() == null) card.setId(42L);
            return card;
        });
    }

    @Test
    void create_shouldPersistAContractPinnedAnalysisWithoutMbqlOrRawSql() {
        AnalysisApplicationService.AnalysisDto created = service.create(
            new CreateAnalysisCommand("项目健康分析", "项目组合健康度", spec(), null),
            actor,
            "idem-001"
        );

        assertThat(created.id()).isEqualTo(42L);
        assertThat(created.lifecycleStatus()).isEqualTo("DRAFT");
        assertThat(created.queryDatasetId()).isEqualTo(contract.datasetId());
        assertThat(created.queryDatasetVersion()).isEqualTo(2);
        assertThat(created.contractVersion()).isEqualTo("r7");

        var saved = org.mockito.ArgumentCaptor.forClass(AnalyticsCard.class);
        verify(cardRepository).save(saved.capture());
        assertThat(saved.getValue().getCardType()).isEqualTo("analysis");
        assertThat(saved.getValue().getDatasetQueryJson())
            .contains("dts.analysis/v1", contract.contractChecksum())
            .doesNotContain("native", "rawSql", "MBQL", contract.baseSql());
        assertThat(saved.getValue().getIdempotencyKey()).isEqualTo("idem-001");
    }

    @Test
    void create_shouldReturnTheExistingAnalysisForTheSameActorAndIdempotencyKey() {
        AnalyticsCard existing = existingCard();
        when(cardRepository.findByCreatorIdAndIdempotencyKey(actor.getId(), "idem-001"))
            .thenReturn(Optional.of(existing));

        AnalysisApplicationService.AnalysisDto result = service.create(
            new CreateAnalysisCommand("ignored", null, spec(), null),
            actor,
            "idem-001"
        );

        assertThat(result.id()).isEqualTo(existing.getId());
        verify(cardRepository, times(0)).save(any());
    }

    @Test
    void update_shouldRejectAStaleOptimisticVersion() {
        AnalyticsCard existing = existingCard();
        existing.setAnalysisVersion(5L);
        when(cardRepository.findById(42L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
            service.update(
                42L,
                new AnalysisApplicationService.UpdateAnalysisCommand("新名称", null, spec(), 4L),
                actor
            )
        )
            .isInstanceOf(AnalysisConflictException.class)
            .hasMessageContaining("ANALYSIS_VERSION_CONFLICT");
    }

    private AnalyticsCard existingCard() {
        AnalyticsCard card = new AnalyticsCard();
        card.setId(42L);
        card.setEntityId("analysis_entity_001");
        card.setName("项目健康分析");
        card.setCardType("analysis");
        card.setCreatorId(actor.getId());
        card.setDatabaseId(9L);
        card.setDatasetQueryJson(new AnalysisQuerySpecParser(new ObjectMapper()).write(spec()));
        card.setQueryDatasetId(contract.datasetId());
        card.setQueryDatasetVersion(2);
        card.setSemanticContractVersion("r7");
        card.setLifecycleStatus("DRAFT");
        card.setAnalysisVersion(0L);
        card.setIdempotencyKey("idem-001");
        return card;
    }

    private AnalysisQuerySpec spec() {
        return new AnalysisQuerySpec(
            "dts.analysis/v1",
            new AnalysisQuerySpec.DatasetRef(contract.datasetId(), 2, "r7", contract.contractChecksum()),
            List.of(new AnalysisQuerySpec.DimensionSelection("project_code", null)),
            List.of(new AnalysisQuerySpec.MetricSelection("record_count", null)),
            List.of(),
            List.of(),
            null,
            List.of(),
            100,
            new AnalysisQuerySpec.Visualization("table", Map.of())
        );
    }

    private GovernedAnalysisDatasetContract contract() {
        return new GovernedAnalysisDatasetContract(
            UUID.randomUUID(),
            2,
            "PUBLISHED",
            UUID.randomUUID(),
            "select project_code from ads_project_health",
            List.of(
                new GovernedAnalysisDatasetContract.Dimension(
                    "project_code",
                    "项目编码",
                    "VARCHAR",
                    List.of(),
                    "DATA_INTERNAL",
                    List.of("EQ", "IN")
                )
            ),
            List.of(new GovernedAnalysisDatasetContract.Metric("record_count", "记录数", "COUNT", null, "COUNT(*)")),
            List.of(),
            List.of(),
            "DATA_INTERNAL",
            "r7",
            "b".repeat(64)
        );
    }

    private AnalyticsUser actor() {
        AnalyticsUser user = new AnalyticsUser();
        user.setId(7L);
        user.setPlatformUsername("alice");
        user.setActive(true);
        return user;
    }
}
