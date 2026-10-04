package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringDraftService;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticSyncCommandService;
import java.sql.Connection;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class ModelDeliveryStatusTransactionTest {
    @Test
    void aggregationDoesNotAcquireAConnectionBeforeCallingItsReaders() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(mock(Connection.class));
        var proxy = proxy(new DataSourceTransactionManager(dataSource));

        assertThatThrownBy(() -> proxy.get("tenant", "actor", UUID.randomUUID(), null, null))
            .isInstanceOf(ProbeComplete.class);

        verify(dataSource, never()).getConnection();
    }

    @Test
    void aggregationSuspendsAndRestoresAnExistingTransaction() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(mock(Connection.class));
        var manager = new DataSourceTransactionManager(dataSource);
        var proxy = proxy(manager);

        new TransactionTemplate(manager).executeWithoutResult(status -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThatThrownBy(() -> proxy.get("tenant", "actor", UUID.randomUUID(), null, null))
                .isInstanceOf(ProbeComplete.class);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        });
        verify(dataSource, times(1)).getConnection();
    }

    private ModelDeliveryStatusQueryService proxy(DataSourceTransactionManager manager) {
        ModelSpecApplicationService models = mock(ModelSpecApplicationService.class);
        when(models.get(any(), any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                .as("delivery aggregation must not hold a transaction while invoking downstream readers")
                .isFalse();
            throw new ProbeComplete();
        });
        var target = new ModelDeliveryStatusQueryService(models, mock(ModelReleaseCandidateApplicationService.class),
            mock(ModelAuthoringDraftService.class), mock(CatalogModelSemanticSyncCommandService.class),
            mock(CandidateQualityRuleContextService.class));
        var factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (ModelDeliveryStatusQueryService) factory.getProxy();
    }

    private static class ProbeComplete extends RuntimeException {}
}
