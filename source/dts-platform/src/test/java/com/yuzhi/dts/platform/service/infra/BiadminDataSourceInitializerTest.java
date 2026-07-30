package com.yuzhi.dts.platform.service.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BiadminDataSourceInitializerTest {

    @Mock
    private InfraDataSourceRepository dataSourceRepository;

    @Mock
    private InfraSecretService secretService;

    private BiadminDataSourceInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new BiadminDataSourceInitializer(dataSourceRepository, secretService, new ObjectMapper());
        ReflectionTestUtils.setField(initializer, "pgHost", "dts-pg");
        ReflectionTestUtils.setField(initializer, "pgPort", "5432");
        ReflectionTestUtils.setField(initializer, "pgDbBiadmin", "biadmin");
        ReflectionTestUtils.setField(initializer, "pgUserBiadmin", "biadmin");
        ReflectionTestUtils.setField(initializer, "pgPwdBiadmin", "secret");
        ReflectionTestUtils.setField(initializer, "autoRegister", true);
    }

    @Test
    void initializeBiadminDataSource_doesNotTreatBiadmin1AsBiadmin() {
        InfraDataSource wrongDatabase = source(
            UUID.randomUUID(),
            "数仓 (biadmin)",
            "jdbc:postgresql://dts-pg:5432/biadmin1"
        );
        wrongDatabase.setSecureProps(new byte[] { 1 });
        when(dataSourceRepository.findAll()).thenReturn(List.of(wrongDatabase));
        when(dataSourceRepository.save(any(InfraDataSource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        initializer.initializeBiadminDataSource();

        ArgumentCaptor<InfraDataSource> captor = ArgumentCaptor.forClass(InfraDataSource.class);
        verify(dataSourceRepository).save(captor.capture());
        InfraDataSource created = captor.getValue();
        assertThat(created).isNotSameAs(wrongDatabase);
        assertThat(created.getJdbcUrl()).isEqualTo("jdbc:postgresql://dts-pg:5432/biadmin");
    }

    @Test
    void initializeBiadminDataSource_keepsLegacyManagedMirrorForAdminReconciliation() {
        InfraDataSource legacyMirror = source(
            UUID.fromString("a0000000-0000-0000-0000-000000000001"),
            "数仓 (biadmin)",
            "jdbc:postgresql://dts-pg:5432/biadmin1"
        );
        when(dataSourceRepository.findAll()).thenReturn(List.of(legacyMirror));
        when(dataSourceRepository.save(legacyMirror)).thenReturn(legacyMirror);

        initializer.initializeBiadminDataSource();

        verify(secretService).applySecrets(legacyMirror, java.util.Map.of("password", "secret"));
        verify(dataSourceRepository).save(legacyMirror);
    }

    @Test
    void initializeBiadminDataSource_doesNotAdoptExternalSourcesWithTheSameDatabaseName() {
        InfraDataSource externalPostgres = source(
            UUID.randomUUID(),
            "外部 PostgreSQL",
            "jdbc:postgresql://external-pg:5432/biadmin"
        );
        InfraDataSource externalMySql = source(
            UUID.randomUUID(),
            "外部 MySQL",
            "jdbc:mysql://external-mysql:3306/biadmin"
        );
        externalPostgres.setSecureProps(new byte[] { 1 });
        externalMySql.setSecureProps(new byte[] { 1 });
        when(dataSourceRepository.findAll()).thenReturn(List.of(externalPostgres, externalMySql));
        when(dataSourceRepository.save(any(InfraDataSource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        initializer.initializeBiadminDataSource();

        ArgumentCaptor<InfraDataSource> captor = ArgumentCaptor.forClass(InfraDataSource.class);
        verify(dataSourceRepository).save(captor.capture());
        assertThat(captor.getValue())
            .isNotSameAs(externalPostgres)
            .isNotSameAs(externalMySql);
        assertThat(captor.getValue().getJdbcUrl()).isEqualTo("jdbc:postgresql://dts-pg:5432/biadmin");
    }

    @Test
    void initializeBiadminDataSource_doesNotAbortApplicationReadyWhenRepositorySaveFails() {
        when(dataSourceRepository.findAll()).thenReturn(List.of());
        when(dataSourceRepository.save(any(InfraDataSource.class)))
            .thenThrow(new IllegalStateException("database unavailable"));

        org.assertj.core.api.Assertions.assertThatCode(initializer::initializeBiadminDataSource)
            .doesNotThrowAnyException();
    }

    private InfraDataSource source(UUID id, String name, String jdbcUrl) {
        InfraDataSource source = new InfraDataSource();
        source.setId(id);
        source.setName(name);
        source.setJdbcUrl(jdbcUrl);
        source.setType("postgres");
        source.setStatus("ACTIVE");
        return source;
    }
}
