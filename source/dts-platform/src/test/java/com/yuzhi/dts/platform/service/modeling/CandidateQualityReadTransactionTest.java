package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationRepository;
import com.yuzhi.dts.platform.service.governance.DefaultLakeDatasetGuard;
import com.yuzhi.dts.platform.service.governance.QualityRuleService;
import com.yuzhi.dts.platform.service.modeling.ModelGovernancePolicyPort.Policy;
import com.yuzhi.dts.platform.service.modeling.ModelGovernancePolicyPort.QualityGate;
import com.yuzhi.dts.platform.service.modeling.ModelGovernancePolicyPort.StandardCoverage;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryView;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

/** Real Spring transaction advice around evidence reads; JDBC is mocked, no database is required. */
class CandidateQualityReadTransactionTest {

    @ParameterizedTest
    @EnumSource(value = DeliveryStatus.class, names = { "DRAFT", "BUILDING", "BUILD_FAILED", "CANCELLED", "STALE" })
    void preBuildContextDoesNotRequireSuccessfulPhysicalEvidence(DeliveryStatus state) throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.candidate.status()).thenReturn(state);

        var context = fixture.outer.execute(status -> fixture.context.context(fixture.candidate, null));

        assertThat(context.assets()).isEmpty();
        assertThat(context.governanceQualityCode()).isEqualTo("MODEL_SPEC_GOVERNANCE_QUALITY_BUILD_REQUIRED");
        assertThat(context.primaryAction().code()).isEqualTo("NONE");
        verifyNoInteractions(fixture.jdbc, fixture.targets, fixture.defaultLake, fixture.rules);
    }

    @Test
    void missingEvidenceInQualityContextDoesNotRollBackDeliveryRead() throws Exception {
        Fixture fixture = new Fixture();

        var context = fixture.outer.execute(status -> {
            var result = fixture.context.context(fixture.candidate, null);
            assertThat(status.isRollbackOnly()).isFalse();
            return result;
        });

        assertThat(context.assets()).isEmpty();
        assertThat(context.governanceQualityCode()).isEqualTo("MODEL_SPEC_GOVERNANCE_QUALITY_CONTEXT_UNAVAILABLE");
        assertThat(context.primaryAction().code()).isEqualTo("NONE");
    }

    @Test
    void missingEvidenceInWorkbenchSummaryDoesNotRollBackDeliveryRead() throws Exception {
        Fixture fixture = new Fixture();

        var summary = fixture.outer.execute(status -> {
            var result = fixture.governance.evaluateForRead(fixture.candidate);
            assertThat(status.isRollbackOnly()).isFalse();
            return result;
        });

        assertThat(summary.required()).isTrue();
        assertThat(summary.passed()).isFalse();
        assertThat(summary.code()).isEqualTo("MODEL_SPEC_GOVERNANCE_QUALITY_BUILD_EVIDENCE_UNAVAILABLE");
    }

    @Test
    void publicationStillRejectsMissingEvidenceInsideItsCommandTransaction() throws Exception {
        Fixture fixture = new Fixture();
        TransactionTemplate command = new TransactionTemplate(fixture.transactions);

        assertThatThrownBy(() -> command.execute(status -> fixture.governance.requirePublishable(fixture.candidate)))
            .isInstanceOf(ModelReleaseCandidateException.class);
    }

    private static class Fixture {
        final DataSource dataSource = mock(DataSource.class);
        final DataSourceTransactionManager transactions = new DataSourceTransactionManager(dataSource);
        final TransactionTemplate outer = new TransactionTemplate(transactions);
        final JdbcTemplate jdbc = mock(JdbcTemplate.class);
        final CandidateView candidate = mock(CandidateView.class);
        final ModelExecutionTargetCatalogResolver targets = mock(ModelExecutionTargetCatalogResolver.class);
        final DefaultLakeDatasetGuard defaultLake = mock(DefaultLakeDatasetGuard.class);
        final QualityRuleService rules = mock(QualityRuleService.class);
        final CandidateGovernanceQualityEvidenceService governance;
        final CandidateQualityRuleContextService context;

        Fixture() throws SQLException {
            when(dataSource.getConnection()).thenAnswer(invocation -> {
                Connection connection = mock(Connection.class);
                when(connection.getAutoCommit()).thenReturn(true);
                return connection;
            });
            outer.setReadOnly(true);
            UUID modelId = UUID.randomUUID();
            EntryView entry = mock(EntryView.class);
            when(entry.modelSpecId()).thenReturn(modelId);
            when(candidate.entries()).thenReturn(List.of(entry));
            when(candidate.id()).thenReturn(UUID.randomUUID());
            when(candidate.status()).thenReturn(DeliveryStatus.BUILT);
            when(candidate.version()).thenReturn(1);
            when(targets.resolve(candidate)).thenReturn(new ModelExecutionTargetCatalogResolver.ResolvedCatalogTarget(
                "postgres-primary", UUID.randomUUID(), "postgres"));
            when(defaultLake.currentDefaultLakeSourceId()).thenReturn(Optional.empty());
            ModelGovernancePolicyPort policy = mock(ModelGovernancePolicyPort.class);
            when(policy.resolve()).thenReturn(Policy.available(StandardCoverage.KEY_AND_MEASURE, QualityGate.BLOCKING));

            // The real repository throws for the empty JDBC result, inside REQUIRED transaction advice.
            CandidatePublicationEvidenceRepository evidence = transactional(
                new CandidatePublicationEvidenceRepository(jdbc, new ObjectMapper()), CandidatePublicationEvidenceRepository.class);
            governance = transactional(new CandidateGovernanceQualityEvidenceService(evidence, targets,
                mock(QualityEvidencePort.class), policy, Clock.systemUTC()), CandidateGovernanceQualityEvidenceService.class);
            context = transactional(new CandidateQualityRuleContextService(evidence, targets,
                mock(CatalogDatasetRepository.class), mock(CandidatePublicationRepository.class), defaultLake, rules, governance),
                CandidateQualityRuleContextService.class);
        }

        private <T> T transactional(T target, Class<T> type) {
            TransactionInterceptor advice = new TransactionInterceptor();
            advice.setTransactionManager(transactions);
            advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
            ProxyFactory factory = new ProxyFactory(target);
            factory.setProxyTargetClass(true);
            factory.addAdvice(advice);
            return type.cast(factory.getProxy());
        }
    }
}
