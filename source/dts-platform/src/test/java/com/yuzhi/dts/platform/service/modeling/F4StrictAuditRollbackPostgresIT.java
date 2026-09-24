package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.config.ModelMaterializationProperties;
import com.yuzhi.dts.platform.config.PlatformEventProperties;
import com.yuzhi.dts.platform.domain.event.PlatformEventOutbox;
import com.yuzhi.dts.platform.repository.audit.PlatformAuditOutboxRepository;
import com.yuzhi.dts.platform.repository.audit.PlatformAuditOutboxRepository.EnqueueCommand;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationRepository;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository;
import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository.LeaseRecord;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository.LeaseStatus;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationAvailabilityPinRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository.QueuedBuildGroup;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationDispatchRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationRunRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationSourceSnapshotRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.PhysicalRelationObservationRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.ArchitectureDictionaryWriteGuard;
import com.yuzhi.dts.platform.service.catalog.CatalogMaterializationSourceAvailabilityPort;
import com.yuzhi.dts.platform.service.etl.DbtScopedProjectService;
import com.yuzhi.dts.platform.service.etl.DbtConfigService;
import com.yuzhi.dts.platform.service.etl.DbtRuntimeTargetException;
import com.yuzhi.dts.platform.service.event.PlatformEventOutboxService;
import com.yuzhi.dts.platform.service.modeling.ModelExecutionTargetCatalogResolver.ResolvedCatalogTarget;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAuditView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationRunArtifactService.FinalizeCommand;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationRunArtifactService.SyncProbeCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateOrigin;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.TransitionCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CompatibilityMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Grain;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.ExpectedRelationType;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.PhysicalColumn;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.PhysicalRelationObservation;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingService;
import com.yuzhi.dts.platform.repository.modeling.WarehouseLayerRepository;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerApplicationService;
import com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort;
import com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort.DomainResolution;
import com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort.ResolutionStatus;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(
    properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.cache.use_second_level_cache=false",
        "spring.jpa.show-sql=false",
        "spring.liquibase.enabled=true",
        "spring.liquibase.change-log=classpath:config/liquibase/master.xml",
        "spring.data.jpa.repositories.enabled=false",
    }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(F4StrictAuditRollbackPostgresIT.FocusedConfig.class)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class F4StrictAuditRollbackPostgresIT {

    private static final String TENANT = "s83-f4-audit-atomic";
    private static final String ACTOR = "release-operator";
    private static final Instant NOW = Instant.parse("2026-08-02T01:00:00Z");
    private static final String MODEL_CHECKSUM = "b".repeat(64);
    private static final String IMPLEMENTATION_CHECKSUM = "c".repeat(64);
    private static final String AUDIT_PAYLOAD_HASH = "f".repeat(64);
    private static final String ATOMIC_TENANT = "s84-f4-dimension-model";
    private static final String ATOMIC_ACTOR = "dimension-model-owner";
    private static final UUID ATOMIC_PLAN_ID = UUID.fromString(
        "10000000-0000-4000-8000-000000000084"
    );
    private static final UUID ATOMIC_DOMAIN_ID = UUID.fromString(
        "20000000-0000-4000-8000-000000000084"
    );
    private static final UUID ATOMIC_OPERATION_ID = UUID.fromString(
        "50000000-0000-4000-8000-000000000084"
    );
    private static final String ATOMIC_DEFINITION_KEY =
        "dm:v2:dimension:" + ATOMIC_OPERATION_ID;
    private static final String ATOMIC_MODEL_KEY =
        "dm:v2:model:" + ATOMIC_OPERATION_ID;
    private static final String ATOMIC_AUDIT_EVENT_ID =
        "f4-dimension-model-" + ATOMIC_OPERATION_ID;

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        "postgres:17.4"
    )
        .withDatabaseName("f4_strict_audit_rollback_it")
        .withUsername("f4_strict_audit")
        .withPassword("f4_strict_audit");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TempDir
    Path project;

    @Autowired
    private CandidatePublicationCommitService publicationCommit;

    @Autowired
    private CandidatePublicationEvidenceRepository publicationEvidence;

    @Autowired
    private ModelMaterializationStartService materializationStart;

    @Autowired
    private ModelMaterializationDispatchRepository materializationDispatch;

    @Autowired
    private DbtRuntimeProfileLeaseRepository runtimeProfileLeases;

    @Autowired
    private ModelMaterializationSourceAvailabilityGuard sourceAvailability;

    @Autowired
    private ModelMaterializationRunArtifactService runArtifacts;

    @Autowired
    private ModelReleaseCandidateService candidateCommands;

    @Autowired
    private ModelReleaseCandidateRepository candidates;

    @Autowired
    private ModelSpecSnapshotCodec codec;

    @Autowired
    private PlatformAuditOutboxRepository auditOutbox;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DimensionModelCreateRequestDecoder dimensionModelDecoder;

    @Autowired
    private DimensionModelApplicationService dimensionModels;

    @MockBean
    private ModelExecutionTargetCatalogResolver targetResolver;

    @MockBean
    private DbtScopedProjectService scopedProjects;

    @MockBean
    private DbtConfigService dbtConfig;

    @MockBean
    private ModelingExecutionAuthorization executionAuthorization;

    @MockBean
    private ModelingSourceScopeGuard sourceScope;

    @MockBean
    private CandidateQualityAssetRegistrationService qualityAssets;

    @MockBean
    private PhysicalRelationInspectorRegistry inspectorRegistry;

    @MockBean
    private AuditService auditService;

    @MockBean
    private ModelSpecSourceValidationPort sourceValidation;

    @MockBean
    private CatalogMaterializationSourceAvailabilityPort catalogAvailability;

    private Scope scope;

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    @EnableJpaRepositories(
        basePackageClasses = com.yuzhi.dts.platform.repository.event.PlatformEventOutboxRepository.class
    )
    @EntityScan(basePackageClasses = PlatformEventOutbox.class)
    static class FocusedConfig {

        private static final Clock TEST_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        ModelMaterializationProperties materializationProperties() {
            return new ModelMaterializationProperties();
        }

        @Bean
        ModelSpecSnapshotCodec modelSpecSnapshotCodec(ObjectMapper objectMapper) {
            return new ModelSpecSnapshotCodec(objectMapper);
        }

        @Bean
        ModelSpecRepository modelSpecRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
            return new ModelSpecRepository(jdbc, objectMapper);
        }

        @Bean
        DimensionDefinitionRepository dimensionDefinitionRepository(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper
        ) {
            return new DimensionDefinitionRepository(jdbc, objectMapper);
        }

        @Bean
        ModelSpecReader modelSpecReader(
            ModelSpecRepository repository,
            ModelSpecSnapshotCodec codec
        ) {
            return new ModelSpecReader(repository, codec);
        }

        @Bean
        ModelSpecFeatureFlags modelSpecFeatureFlags() {
            return new ModelSpecFeatureFlags(true);
        }

        @Bean
        ModelSpecDomainWriteAccessPort modelSpecDomainWriteAccessPort() {
            return domainId -> true;
        }

        @Bean
        ModelSpecDomainReadAccessPort modelSpecDomainReadAccessPort() {
            return new ModelSpecDomainReadAccessPort() {
                @Override
                public boolean canRead(UUID domainId) {
                    return true;
                }

                @Override
                public Set<UUID> visibleDomainIds() {
                    return Set.of(ATOMIC_DOMAIN_ID);
                }
            };
        }

        @Bean
        ModelSpecPlanWriteAccessPort modelSpecPlanWriteAccessPort(
            JdbcTemplate jdbc
        ) {
            var access = org.mockito.Mockito.mock(ModelSpecPlanWriteAccessPort.class);
            org.mockito.Mockito.when(access.canReadPlan(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
            org.mockito.Mockito.when(access.canReadPlan(org.mockito.ArgumentMatchers.any())).thenReturn(true);
            org.mockito.Mockito.when(access.canMaintain(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
            org.mockito.Mockito.when(access.canEdit(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
            return access;
        }

        @Bean
        CatalogDomainResolutionPort catalogDomainResolutionPort() {
            return domainId ->
                new DomainResolution(
                    domainId,
                    ResolutionStatus.AVAILABLE,
                    "Atomic dimension domain",
                    "ATOMIC_DIMENSION_DOMAIN",
                    ATOMIC_ACTOR,
                    "Strict audit rollback fixture"
                );
        }

        @Bean
        DimensionDefinitionApplicationService dimensionDefinitionApplicationService(
            DimensionDefinitionRepository repository,
            ObjectMapper objectMapper,
            ModelSpecDomainWriteAccessPort domainWriteAccess,
            ModelSpecDomainReadAccessPort domainReadAccess,
            AuditService auditService
        ) {
            return new DimensionDefinitionApplicationService(
                repository,
                objectMapper,
                domainWriteAccess,
                domainReadAccess,
                auditService
            );
        }

        @Bean
        ModelSpecApplicationService modelSpecApplicationService(
            ModelSpecRepository repository,
            DimensionDefinitionRepository dimensionDefinitions,
            ModelSpecSnapshotCodec codec,
            CatalogDomainResolutionPort domainResolution,
            ModelSpecDomainWriteAccessPort domainWriteAccess,
            ModelSpecDomainReadAccessPort domainReadAccess,
            ModelSpecPlanWriteAccessPort planWriteAccess,
            ModelSpecSourceValidationPort sourceValidation,
            ModelSpecReader reader,
            ModelSpecFeatureFlags featureFlags,
            AuditService auditService
        ) {
            return new ModelSpecApplicationService(
                repository,
                dimensionDefinitions,
                codec,
                domainResolution,
                domainWriteAccess,
                domainReadAccess,
                planWriteAccess,
                sourceValidation,
                reader,
                featureFlags,
                auditService,
                new WarehouseLayerApplicationService(
                    org.mockito.Mockito.mock(WarehouseLayerRepository.class),
                    auditService,
                    org.mockito.Mockito.mock(ArchitectureDictionaryWriteGuard.class)
                )
            );
        }

        @Bean
        ModelSpecCreateRequestDecoder modelSpecCreateRequestDecoder(
            ObjectMapper objectMapper
        ) {
            return new ModelSpecCreateRequestDecoder(objectMapper);
        }

        @Bean
        ModelSpecUpdateRequestDecoder modelSpecUpdateRequestDecoder(
            ObjectMapper objectMapper
        ) {
            return new ModelSpecUpdateRequestDecoder(objectMapper);
        }

        @Bean
        DimensionModelCreateRequestDecoder dimensionModelCreateRequestDecoder(
            ObjectMapper objectMapper,
            ModelSpecCreateRequestDecoder createDecoder,
            ModelSpecUpdateRequestDecoder updateDecoder
        ) {
            return new DimensionModelCreateRequestDecoder(
                objectMapper,
                createDecoder,
                updateDecoder
            );
        }

        @Bean
        DimensionModelApplicationService dimensionModelApplicationService(
            DimensionDefinitionApplicationService dimensionDefinitions,
            ModelSpecApplicationService modelSpecs,
            DimensionDefinitionRepository dimensionDefinitionRepository,
            ModelSpecRepository modelSpecRepository,
            DimensionModelCreateRequestDecoder decoder,
            ModelSpecSnapshotCodec codec,
            AuditService auditService
        ) {
            return new DimensionModelApplicationService(
                dimensionDefinitions,
                modelSpecs,
                dimensionDefinitionRepository,
                modelSpecRepository,
                decoder,
                codec,
                auditService
            );
        }

        @Bean
        ModelLifecycleRepository modelLifecycleRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
            return new ModelLifecycleRepository(jdbc, objectMapper);
        }

        @Bean
        ModelReleaseCandidateRepository modelReleaseCandidateRepository(JdbcTemplate jdbc) {
            return new ModelReleaseCandidateRepository(jdbc);
        }

        @Bean
        CandidatePublicationRepository candidatePublicationRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
            return new CandidatePublicationRepository(jdbc, objectMapper);
        }

        @Bean
        CandidatePublicationEvidenceRepository candidatePublicationEvidenceRepository(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper
        ) {
            return new CandidatePublicationEvidenceRepository(jdbc, objectMapper);
        }

        @Bean
        CatalogModelServingProjectionRepository catalogModelServingProjectionRepository(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper
        ) {
            return new CatalogModelServingProjectionRepository(jdbc, objectMapper);
        }

        @Bean
        PlatformAuditOutboxRepository platformAuditOutboxRepository(JdbcTemplate jdbc) {
            return new PlatformAuditOutboxRepository(jdbc);
        }

        @Bean
        ModelMaterializationBuildRepository modelMaterializationBuildRepository(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            ModelMaterializationProperties properties
        ) {
            return new ModelMaterializationBuildRepository(jdbc, objectMapper, properties);
        }

        @Bean
        DbtRuntimeProfileLeaseRepository dbtRuntimeProfileLeaseRepository(
            JdbcTemplate jdbc
        ) {
            return new DbtRuntimeProfileLeaseRepository(jdbc);
        }

        @Bean
        ModelMaterializationDispatchRepository modelMaterializationDispatchRepository(
            JdbcTemplate jdbc
        ) {
            return new ModelMaterializationDispatchRepository(jdbc);
        }

        @Bean
        ModelMaterializationRunRepository modelMaterializationRunRepository(JdbcTemplate jdbc) {
            return new ModelMaterializationRunRepository(jdbc);
        }

        @Bean
        ModelMaterializationSourceSnapshotRepository modelMaterializationSourceSnapshotRepository(
            JdbcTemplate jdbc
        ) {
            return new ModelMaterializationSourceSnapshotRepository(jdbc);
        }

        @Bean
        ModelMaterializationAvailabilityPinRepository modelMaterializationAvailabilityPinRepository(
            JdbcTemplate jdbc
        ) {
            return new ModelMaterializationAvailabilityPinRepository(jdbc);
        }

        @Bean
        PhysicalRelationObservationRepository physicalRelationObservationRepository(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper
        ) {
            return new PhysicalRelationObservationRepository(jdbc, objectMapper);
        }

        @Bean
        PlatformEventOutboxService platformEventOutboxService(
            com.yuzhi.dts.platform.repository.event.PlatformEventOutboxRepository repository,
            ObjectMapper objectMapper,
            AuditService auditService,
            PlatformEventProperties properties
        ) {
            return new PlatformEventOutboxService(repository, objectMapper, auditService, properties);
        }

        @Bean
        ModelLifecyclePublicationService modelLifecyclePublicationService(
            ModelSpecRepository modelSpecs,
            ModelLifecycleRepository lifecycle,
            ModelSpecSnapshotCodec codec
        ) {
            return new ModelLifecyclePublicationService(modelSpecs, lifecycle, codec);
        }

        @Bean
        CatalogModelServingService catalogModelServingService(
            CatalogModelServingProjectionRepository repository,
            PlatformEventOutboxService outbox
        ) {
            return new CatalogModelServingService(repository, outbox, TEST_CLOCK);
        }

        @Bean
        ModelReleaseCandidateService modelReleaseCandidateService(
            ModelReleaseCandidateRepository repository,
            ObjectMapper objectMapper,
            ModelMaterializationBuildRepository retryDriftGate,
            AuditService auditService
        ) {
            return new ModelReleaseCandidateService(
                repository,
                objectMapper,
                TEST_CLOCK,
                UUID::randomUUID,
                null,
                retryDriftGate,
                auditService
            );
        }

        @Bean
        ModelMaterializationSourceAvailabilityGuard modelMaterializationSourceAvailabilityGuard(
            ModelMaterializationSourceSnapshotRepository snapshots,
            ModelSpecSourceValidationPort sourceValidation,
            CatalogMaterializationSourceAvailabilityPort catalogAvailability,
            ModelMaterializationAvailabilityPinRepository pins,
            ObjectMapper objectMapper
        ) {
            return new ModelMaterializationSourceAvailabilityGuard(
                snapshots,
                sourceValidation,
                catalogAvailability,
                pins,
                objectMapper
            );
        }

        @Bean
        ModelMaterializationAvailabilityAuditService modelMaterializationAvailabilityAuditService(
            AuditService auditService
        ) {
            return new ModelMaterializationAvailabilityAuditService(auditService);
        }

        @Bean
        ModelMaterializationStartService modelMaterializationStartService(
            ModelReleaseCandidateService candidates,
            ModelMaterializationBuildRepository builds,
            ModelMaterializationSourceAvailabilityGuard sourceAvailability,
            ModelMaterializationAvailabilityAuditService availabilityAudit,
            DbtConfigService dbtConfig
        ) {
            return new ModelMaterializationStartService(
                candidates,
                builds,
                sourceAvailability,
                availabilityAudit,
                dbtConfig,
                TEST_CLOCK
            );
        }

        @Bean
        ModelMaterializationRunArtifactService modelMaterializationRunArtifactService(
            ModelMaterializationRunRepository runs,
            ModelMaterializationBuildRepository builds,
            ModelMaterializationSourceAvailabilityGuard sourceAvailability,
            DbtScopedProjectService scopedProjects,
            PhysicalRelationInspectorRegistry inspectors,
            PhysicalRelationObservationRepository observations,
            ModelReleaseCandidateService candidates,
            CandidateQualityAssetRegistrationService qualityAssets,
            AuditService auditService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
        ) {
            return new ModelMaterializationRunArtifactService(
                runs,
                builds,
                sourceAvailability,
                scopedProjects,
                inspectors,
                observations,
                candidates,
                qualityAssets,
                auditService,
                objectMapper,
                TEST_CLOCK,
                new TransactionTemplate(transactionManager)
            );
        }

        @Bean
        CandidatePublicationCommitService candidatePublicationCommitService(
            CandidatePublicationEvidenceRepository evidence,
            ModelSpecRepository modelSpecs,
            ModelLifecycleRepository lifecycle,
            ModelSpecSnapshotCodec codec,
            ModelLifecyclePublicationService lifecyclePublication,
            CandidatePublicationRepository publications,
            PlatformEventOutboxService outbox,
            ModelReleaseCandidateService candidateCommands,
            ModelExecutionTargetCatalogResolver targetResolver,
            AuditService auditService,
            CatalogModelServingService catalogServing
        ) {
            return new CandidatePublicationCommitService(
                evidence,
                modelSpecs,
                lifecycle,
                codec,
                lifecyclePublication,
                publications,
                outbox,
                candidateCommands,
                targetResolver,
                TEST_CLOCK,
                auditService,
                catalogServing
            );
        }
    }

    @BeforeEach
    void setUp() {
        scope = Scope.create();
        reset(
            dbtConfig,
            targetResolver,
            scopedProjects,
            inspectorRegistry,
            auditService,
            sourceValidation,
            catalogAvailability
        );
        when(
            auditService.auditActionStrict(
                anyString(),
                any(AuditStage.class),
                anyString(),
                any()
            )
        ).thenReturn(UUID.randomUUID());
        when(
            auditService.auditActionAsStrict(
                anyString(),
                anyString(),
                any(Instant.class),
                anyString(),
                any(AuditStage.class),
                anyString(),
                any()
            )
        ).thenReturn(UUID.randomUUID());
        when(catalogAvailability.lockAndRead(any())).thenReturn(List.of());
        when(catalogAvailability.lockAndCompare(any())).thenReturn(List.of());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "MODEL_SOURCE_AVAILABILITY_FENCE_ACTIVE",
        "MODEL_MATERIALIZATION_SOURCE_GENERATION_STALE",
        "MODEL_MATERIALIZATION_SOURCE_PIN_MISSING"
    })
    void preparationSourceFailureCommitsAfterRollbackAndSurvivesLateFinalize(String code) {
        QueuedBuildGroup build = seedSubmittedMaterialization(scope, false);
        var recorder = preparationFailureRecorder();
        AtomicBoolean audited = new AtomicBoolean();
        doAnswer(invocation -> {
            assertThat(pipelineStatus(build.pipelineRunGroupId())).isEqualTo("FAILED_STALE");
            assertThat(candidateStatus(scope)).isEqualTo("STALE");
            enqueueAuditThenVerifyVisible(scope.auditEventId());
            audited.set(true);
            return UUID.randomUUID();
        }).when(auditService).auditActionAsStrict(
            eq("airflow"), anyString(), any(Instant.class), eq("MODEL_MATERIALIZATION_RUN_FAILED"),
            eq(AuditStage.FAIL), eq(build.pipelineRunGroupId().toString()), any()
        );

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.queryForList("select id from modeling_materialization_dispatch where id = ? for update", build.pipelineRunGroupId());
            recorder.recordAfterRollback(build.pipelineRunGroupId(), code);
            assertThat(candidateStatus(scope)).isEqualTo("BUILDING");
            assertThat(audited).isFalse();
            throw new IllegalStateException("original preparation refusal");
        })).hasMessage("original preparation refusal");

        assertThat(audited).isTrue();
        assertThat(auditOutboxCount(scope.auditEventId())).isEqualTo(1);
        assertThat(candidateStatus(scope)).isEqualTo("STALE");
        assertThat(candidateVersion(scope)).isEqualTo(3);
        assertThat(pipelineStatus(build.pipelineRunGroupId())).isEqualTo("FAILED_STALE");
        assertThat(jdbc.queryForMap(
            "select status, last_error_code from modeling_materialization_dispatch where id = ?", build.pipelineRunGroupId()
        )).containsEntry("status", "FAILED").containsEntry("last_error_code", code);
        recorder.recordAfterRollback(build.pipelineRunGroupId(), "MODEL_SOURCE_AVAILABILITY_FENCE_ACTIVE");
        for (String outcome : List.of("SUCCEEDED", "FAILED")) {
            assertThatThrownBy(() -> runArtifacts.finalizeRun(build.pipelineRunGroupId(), new FinalizeCommand(outcome)))
                .isInstanceOf(ModelMaterializationRuntimeException.class);
        }
        assertThat(jdbc.queryForObject(
            "select last_error_code from modeling_materialization_dispatch where id = ?", String.class, build.pipelineRunGroupId()
        )).isEqualTo(code);
        assertThat(candidateVersion(scope)).isEqualTo(3);
        assertThat(pipelineStatus(build.pipelineRunGroupId())).isEqualTo("FAILED_STALE");
    }

    @Test
    void preparationSourceStrictAuditFailureRollsBackEveryStateAndPreservesOriginalError() {
        QueuedBuildGroup build = seedSubmittedMaterialization(scope, false);
        AtomicBoolean audited = new AtomicBoolean();
        doAnswer(invocation -> {
            assertThat(pipelineStatus(build.pipelineRunGroupId())).isEqualTo("FAILED_STALE");
            assertThat(candidateStatus(scope)).isEqualTo("STALE");
            enqueueAuditThenVerifyVisible(scope.auditEventId());
            audited.set(true);
            throw new IllegalStateException("strict preparation audit unavailable");
        }).when(auditService).auditActionAsStrict(
            eq("airflow"), anyString(), any(Instant.class), eq("MODEL_MATERIALIZATION_RUN_FAILED"),
            eq(AuditStage.FAIL), eq(build.pipelineRunGroupId().toString()), any()
        );
        var recorder = preparationFailureRecorder();

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            recorder.recordAfterRollback(build.pipelineRunGroupId(), "MODEL_SOURCE_AVAILABILITY_FENCE_ACTIVE");
            throw new IllegalStateException("original preparation refusal");
        })).hasMessage("original preparation refusal");

        assertThat(audited).isTrue();
        assertThat(pipelineStatus(build.pipelineRunGroupId())).isEqualTo("SUBMITTED");
        assertThat(candidateStatus(scope)).isEqualTo("BUILDING");
        assertThat(candidateVersion(scope)).isEqualTo(2);
        assertThat(auditOutboxCount(scope.auditEventId())).isZero();
        assertThat(jdbc.queryForMap(
            "select status, last_error_code from modeling_materialization_dispatch where id = ?", build.pipelineRunGroupId()
        )).containsEntry("status", "SUBMITTED").containsEntry("last_error_code", null);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = { "CONSUMED", "SUPERSEDED", "VERSION_CHANGED", "COMPLETED", "NEWER_ATTEMPT" })
    void preparationSourceFailureIgnoresLateAndSupersededAttempts(String variant) {
        QueuedBuildGroup build = seedSubmittedMaterialization(scope, "CONSUMED".equals(variant));
        if ("SUPERSEDED".equals(variant)) {
            jdbc.update("update modeling_model_release_candidate set status = 'STALE' where id = ?", scope.candidateId());
        } else if ("VERSION_CHANGED".equals(variant)) {
            jdbc.update("update modeling_model_release_candidate set version = version + 1 where id = ?", scope.candidateId());
        } else if ("COMPLETED".equals(variant)) {
            jdbc.update("update modeling_materialization_dispatch set status = 'COMPLETED' where id = ?", build.pipelineRunGroupId());
        } else if ("NEWER_ATTEMPT".equals(variant)) {
            jdbc.update("""
                insert into modeling_materialization_dispatch (
                    id, tenant_id, candidate_id, candidate_version, attempt,
                    execution_target_key, airflow_dag_id, airflow_run_id, artifact_bundle_checksum,
                    status, created_at, last_modified_at
                ) select ?, tenant_id, candidate_id, candidate_version, attempt + 1,
                         execution_target_key, airflow_dag_id, airflow_run_id || '_new', artifact_bundle_checksum,
                         'FAILED', created_at, last_modified_at
                    from modeling_materialization_dispatch where id = ?
                """, UUID.randomUUID(), build.pipelineRunGroupId());
        }
        String candidateBefore = candidateStatus(scope);
        int versionBefore = candidateVersion(scope);

        preparationFailureRecorder().recordAfterRollback(build.pipelineRunGroupId(), "MODEL_SOURCE_AVAILABILITY_FENCE_ACTIVE");

        assertThat(candidateStatus(scope)).isEqualTo(candidateBefore);
        assertThat(candidateVersion(scope)).isEqualTo(versionBefore);
        assertThat(pipelineStatus(build.pipelineRunGroupId())).isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject(
            "select last_error_code from modeling_materialization_dispatch where id = ?", String.class, build.pipelineRunGroupId()
        )).isNull();
    }

    private ModelMaterializationRuntimeFailureRecorder preparationFailureRecorder() {
        return new ModelMaterializationRuntimeFailureRecorder(
            materializationDispatch, runArtifacts, transactionManager, FocusedConfig.TEST_CLOCK
        );
    }

    @Test
    void targetAdmissionFailureRollsBackCandidateTransitionAndCreatesNoExecution() {
        seedCanonicalModelAndCandidate(scope, DeliveryStatus.DRAFT, 1, true);
        when(dbtConfig.loadRuntimeConfig()).thenThrow(new DbtRuntimeTargetException(
            "DBT_TARGET_DEFAULT_LAKE_MISMATCH", "目标数仓与默认数据湖不一致"
        ));

        assertThatThrownBy(() -> materializationStart.startWithBuild(
            TENANT, ACTOR, scope.candidateId(), 1, "target-denied-" + scope.candidateId(), "target admission proof"
        )).isInstanceOf(ModelReleaseCandidateException.class)
            .extracting(error -> ((ModelReleaseCandidateException) error).code())
            .isEqualTo("DBT_TARGET_DEFAULT_LAKE_MISMATCH");

        assertThat(candidateStatus(scope)).isEqualTo("DRAFT");
        assertThat(candidateVersion(scope)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "select count(*) from modeling_pipeline_run where tenant_id = ? and release_candidate_id = ?",
            Integer.class, TENANT, scope.candidateId()
        )).isZero();
        assertThat(jdbc.queryForObject(
            "select count(*) from modeling_model_release_candidate_command where tenant_id = ? and candidate_id = ?",
            Integer.class, TENANT, scope.candidateId()
        )).isZero();
    }

    @Test
    void publicationStrictAuditFailureRollsBackProductionPublicationServingCandidateAndOutboxes()
        throws Exception {
        CandidateView publishing = seedPublishingCandidate(scope);
        when(targetResolver.resolve(any(CandidateView.class))).thenReturn(
            new ResolvedCatalogTarget(
                "postgres:warehouse/prod",
                scope.sourceId(),
                "postgres"
            )
        );
        AtomicBoolean strictAuditReached = new AtomicBoolean();
        doAnswer(invocation -> {
            entityManager.flush();
            assertPublicationFactsVisibleInsideTransaction(
                scope,
                publishing.version() + 1
            );
            enqueueAuditThenVerifyVisible(scope.auditEventId());
            strictAuditReached.set(true);
            throw new IllegalStateException("strict audit unavailable");
        })
            .when(auditService)
            .auditActionStrict(
                eq("MODEL_RELEASE_CANDIDATE_PUBLISH"),
                eq(AuditStage.SUCCESS),
                eq(scope.candidateId().toString()),
                any()
            );

        assertThat(AopUtils.isAopProxy(publicationCommit)).isTrue();
        assertThatThrownBy(() ->
            publicationCommit.commit(
                TENANT,
                ACTOR,
                publishing,
                "publish-" + scope.candidateId(),
                "strict audit rollback proof"
            )
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("strict audit unavailable");

        assertThat(strictAuditReached).isTrue();
        assertPublicationFactsRolledBack(scope, publishing.version());
    }

    @Test
    void artifactStrictAuditFailureRollsBackProductionRunObservationAndAuditOutbox()
        throws Exception {
        QueuedBuildGroup build = seedSubmittedMaterialization(scope);
        writeArtifacts(scope, build);
        configureExternalArtifactReads(scope, build);
        AtomicBoolean strictAuditReached = new AtomicBoolean();
        doAnswer(invocation -> {
            assertThat(pipelineStatus(build.pipelineRunGroupId()))
                .isEqualTo("DBT_SUCCEEDED");
            assertThat(observationCount(build.pipelineRunGroupId()))
                .isEqualTo(1);
            assertThat(candidateStatus(scope)).isEqualTo("BUILDING");
            enqueueAuditThenVerifyVisible(scope.auditEventId());
            strictAuditReached.set(true);
            throw new IllegalStateException("strict machine audit unavailable");
        })
            .when(auditService)
            .auditActionAsStrict(
                eq("airflow"),
                anyString(),
                any(Instant.class),
                eq("MODEL_MATERIALIZATION_ARTIFACTS_SYNCED"),
                eq(AuditStage.SUCCESS),
                eq(build.pipelineRunGroupId().toString()),
                any()
            );

        assertThatThrownBy(() ->
            runArtifacts.syncAndProbe(
                build.pipelineRunGroupId(),
                new SyncProbeCommand(
                    "RELEASE_BUILD",
                    build.artifactBundleChecksum()
                )
            )
        )
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Machine audit persistence failed")
            .hasRootCauseMessage("strict machine audit unavailable");

        assertThat(strictAuditReached).isTrue();
        assertThat(pipelineStatus(build.pipelineRunGroupId()))
            .isEqualTo("SUBMITTED");
        assertThat(observationCount(build.pipelineRunGroupId())).isZero();
        assertThat(candidateStatus(scope)).isEqualTo("BUILDING");
        assertThat(candidateVersion(scope)).isEqualTo(2);
        assertThat(auditOutboxCount(scope.auditEventId())).isZero();
    }

    @Test
    void finalizeFailureStrictAuditRollsBackProductionRunCandidateAndAuditOutbox()
        throws Exception {
        QueuedBuildGroup build = seedSubmittedMaterialization(scope);
        AtomicBoolean strictAuditReached = new AtomicBoolean();
        doAnswer(invocation -> {
            assertThat(pipelineStatus(build.pipelineRunGroupId()))
                .isEqualTo("FAILED");
            assertThat(candidateStatus(scope)).isEqualTo("BUILD_FAILED");
            enqueueAuditThenVerifyVisible(scope.auditEventId());
            strictAuditReached.set(true);
            throw new IllegalStateException("strict machine audit unavailable");
        })
            .when(auditService)
            .auditActionAsStrict(
                eq("airflow"),
                anyString(),
                any(Instant.class),
                eq("MODEL_MATERIALIZATION_RUN_FAILED"),
                eq(AuditStage.FAIL),
                eq(build.pipelineRunGroupId().toString()),
                any()
            );

        assertThatThrownBy(() ->
            runArtifacts.finalizeRun(
                build.pipelineRunGroupId(),
                new FinalizeCommand("FAILED")
            )
        )
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Machine audit persistence failed")
            .hasRootCauseMessage("strict machine audit unavailable");

        assertThat(strictAuditReached).isTrue();
        assertThat(pipelineStatus(build.pipelineRunGroupId()))
            .isEqualTo("SUBMITTED");
        assertThat(candidateStatus(scope)).isEqualTo("BUILDING");
        assertThat(candidateVersion(scope)).isEqualTo(2);
        assertThat(auditOutboxCount(scope.auditEventId())).isZero();
    }

    @Test
    void dimensionModelStrictAuditFailureRollsBackBothRevisionLedgersAndAuditOutbox()
        throws Exception {
        cleanupAtomicFixture();
        seedAtomicPlanAndDomain();
        AtomicBoolean strictAuditReached = new AtomicBoolean();
        AtomicReference<UUID> definitionId = new AtomicReference<>();
        AtomicReference<UUID> modelId = new AtomicReference<>();
        try {
            DimensionModelCreateRequestDecoder.PreparedCreate prepared =
                dimensionModelDecoder.decode(
                    objectMapper.readTree(atomicDimensionModelRequest())
            );
            doAnswer(invocation -> {
                assertThat(
                    TransactionSynchronizationManager.isActualTransactionActive()
                ).isTrue();
                entityManager.flush();
                UUID persistedDefinitionId = jdbc.queryForObject(
                    "select id from modeling_dimension_definition where tenant_id = ? and idempotency_key = ?",
                    UUID.class,
                    ATOMIC_TENANT,
                    ATOMIC_DEFINITION_KEY
                );
                UUID persistedModelId = jdbc.queryForObject(
                    "select id from modeling_model_spec where tenant_id = ? and idempotency_key = ?",
                    UUID.class,
                    ATOMIC_TENANT,
                    ATOMIC_MODEL_KEY
                );
                definitionId.set(persistedDefinitionId);
                modelId.set(persistedModelId);

                assertThat(
                    jdbc.queryForObject(
                        "select revision from modeling_dimension_definition where tenant_id = ? and id = ?",
                        Integer.class,
                        ATOMIC_TENANT,
                        persistedDefinitionId
                    )
                ).isEqualTo(2);
                assertThat(
                    jdbc.queryForList(
                        "select revision from modeling_dimension_definition_revision where tenant_id = ? and dimension_definition_id = ? order by revision",
                        Integer.class,
                        ATOMIC_TENANT,
                        persistedDefinitionId
                    )
                ).containsExactly(1, 2);
                assertThat(
                    jdbc.queryForObject(
                        "select revision from modeling_model_spec where tenant_id = ? and id = ?",
                        Integer.class,
                        ATOMIC_TENANT,
                        persistedModelId
                    )
                ).isEqualTo(2);
                assertThat(
                    jdbc.queryForList(
                        "select revision from modeling_model_spec_revision where tenant_id = ? and model_spec_id = ? order by revision",
                        Integer.class,
                        ATOMIC_TENANT,
                        persistedModelId
                    )
                ).containsExactly(1, 2);

                auditOutbox.enqueue(
                    new EnqueueCommand(
                        ATOMIC_TENANT,
                        ATOMIC_AUDIT_EVENT_ID,
                        NOW,
                        AUDIT_PAYLOAD_HASH,
                        "{}"
                    )
                );
                assertThat(auditOutboxCount(ATOMIC_AUDIT_EVENT_ID)).isOne();
                assertThat(
                    count(
                        "select count(*) from platform_audit_outbox where tenant_id = ?",
                        ATOMIC_TENANT
                    )
                ).isOne();
                strictAuditReached.set(true);
                throw new IllegalStateException(
                    "dimension model strict audit unavailable"
                );
            })
                .when(auditService)
                .auditActionStrict(
                    eq("MODELING_DIMENSION_MODEL_CREATE"),
                    eq(AuditStage.SUCCESS),
                    anyString(),
                    any()
                );

            assertThat(AopUtils.isAopProxy(dimensionModels)).isTrue();
            assertThatThrownBy(() ->
                dimensionModels.create(ATOMIC_TENANT, ATOMIC_ACTOR, prepared)
            )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("dimension model strict audit unavailable");

            assertThat(strictAuditReached).isTrue();
            assertThat(definitionId.get()).isNotNull();
            assertThat(modelId.get()).isNotNull();
            assertThat(
                count(
                    "select count(*) from modeling_dimension_definition where tenant_id = ? and id = ?",
                    ATOMIC_TENANT,
                    definitionId.get()
                )
            ).isZero();
            assertThat(
                count(
                    "select count(*) from modeling_dimension_definition_revision where tenant_id = ? and dimension_definition_id = ?",
                    ATOMIC_TENANT,
                    definitionId.get()
                )
            ).isZero();
            assertThat(
                count(
                    "select count(*) from modeling_model_spec where tenant_id = ? and id = ?",
                    ATOMIC_TENANT,
                    modelId.get()
                )
            ).isZero();
            assertThat(
                count(
                    "select count(*) from modeling_model_spec_revision where tenant_id = ? and model_spec_id = ?",
                    ATOMIC_TENANT,
                    modelId.get()
                )
            ).isZero();
            assertThat(auditOutboxCount(ATOMIC_AUDIT_EVENT_ID)).isZero();
            assertThat(
                count(
                    "select count(*) from platform_audit_outbox where tenant_id = ?",
                    ATOMIC_TENANT
                )
            ).isZero();
        } finally {
            cleanupAtomicFixture();
        }
    }

    private void seedAtomicPlanAndDomain() {
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
            jdbc.update(
                """
                insert into catalog_domain (
                    id, name, code, lifecycle_status, access_policy
                ) values (?, 'Atomic dimension domain',
                          'ATOMIC_DIMENSION_DOMAIN_84', 'ACTIVE', 'PUBLIC')
                """,
                ATOMIC_DOMAIN_ID
            );
            jdbc.update(
                """
                insert into modeling_warehouse_plan (
                    id, tenant_id, owner, code, name, owner_id,
                    onboarding_mode, lifecycle_status, status, version,
                    created_date, last_modified_date
                ) values (?, ?, ?, 'atomic_dimension_plan_84',
                          'Atomic dimension plan', ?, 'BUSINESS_FIRST',
                          'BASELINE_READY', 'DRAFT', 1,
                          current_timestamp, current_timestamp)
                """,
                ATOMIC_PLAN_ID,
                ATOMIC_TENANT,
                ATOMIC_ACTOR,
                ATOMIC_ACTOR
            );
            jdbc.update(
                """
                insert into modeling_warehouse_plan_domain (
                    id, tenant_id, plan_id, domain_id, confirmation_status,
                    last_validated_at, created_date, last_modified_date
                ) values (?, ?, ?, ?, 'CONFIRMED', current_timestamp,
                          current_timestamp, current_timestamp)
                """,
                UUID.fromString("30000000-0000-4000-8000-000000000084"),
                ATOMIC_TENANT,
                ATOMIC_PLAN_ID,
                ATOMIC_DOMAIN_ID
            );
        });
    }

    private void cleanupAtomicFixture() {
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
            jdbc.update(
                "delete from platform_audit_outbox where tenant_id = ? and event_id = ?",
                ATOMIC_TENANT,
                ATOMIC_AUDIT_EVENT_ID
            );
            jdbc.update(
                """
                delete from modeling_model_spec_revision
                 where tenant_id = ? and model_spec_id in (
                       select id from modeling_model_spec
                        where tenant_id = ? and idempotency_key = ?)
                """,
                ATOMIC_TENANT,
                ATOMIC_TENANT,
                ATOMIC_MODEL_KEY
            );
            jdbc.update(
                "delete from modeling_model_spec where tenant_id = ? and idempotency_key = ?",
                ATOMIC_TENANT,
                ATOMIC_MODEL_KEY
            );
            jdbc.update(
                """
                delete from modeling_dimension_definition_revision
                 where tenant_id = ? and dimension_definition_id in (
                       select id from modeling_dimension_definition
                        where tenant_id = ? and idempotency_key = ?)
                """,
                ATOMIC_TENANT,
                ATOMIC_TENANT,
                ATOMIC_DEFINITION_KEY
            );
            jdbc.update(
                "delete from modeling_dimension_definition where tenant_id = ? and idempotency_key = ?",
                ATOMIC_TENANT,
                ATOMIC_DEFINITION_KEY
            );
            jdbc.update(
                "delete from modeling_warehouse_plan_domain where tenant_id = ? and plan_id = ?",
                ATOMIC_TENANT,
                ATOMIC_PLAN_ID
            );
            jdbc.update(
                "delete from modeling_warehouse_plan where tenant_id = ? and id = ?",
                ATOMIC_TENANT,
                ATOMIC_PLAN_ID
            );
            jdbc.update(
                "delete from catalog_domain where id = ?",
                ATOMIC_DOMAIN_ID
            );
        });
    }

    private static String atomicDimensionModelRequest() {
        return """
            {
              "operationId":"50000000-0000-4000-8000-000000000084",
              "definitionBinding":{
                "mode":"CREATE",
                "definition":{
                  "domainId":"20000000-0000-4000-8000-000000000084",
                  "name":"Atomic customer dimension",
                  "definition":"Strict audit rollback fixture",
                  "ownerId":"dimension-model-owner",
                  "reuseScope":"DOMAIN",
                  "scopeType":"DOMAIN",
                  "hierarchies":[],
                  "attributes":[{
                    "code":"CUSTOMER_CODE",
                    "name":"Customer code",
                    "definition":"Stable customer business key",
                    "primaryKey":true,
                    "order":1
                  }]
                }
              },
              "modelSpec":{
                "planId":"10000000-0000-4000-8000-000000000084",
                "domainId":"20000000-0000-4000-8000-000000000084",
                "modelType":"DIMENSION",
                "layer":"DWD",
                "name":"dim_atomic_customer",
                "description":"Complete atomic dimension model",
                "implementationMode":"DESIGNER_GENERATED",
                "materialization":"table",
                "businessActivityRef":null,
                "consumptionScenario":null,
                "grain":{"statement":"one row per customer","keys":["customer_code"]},
                "factShape":null,
                "timeSemantics":null,
                "generationStrategy":null,
                "dimensionProfile":{
                  "hierarchies":[],
                  "scdPolicy":{"type":"NONE"}
                },
                "dataMartId":null,
                "variantCode":"DEFAULT",
                "fields":[{
                  "name":"customer_code",
                  "displayName":"Customer code",
                  "dataType":"STRING",
                  "nullable":false,
                  "role":"KEY",
                  "dimensionAttributeCode":"CUSTOMER_CODE"
                }],
                "sourceRefs":[],
                "dependsOn":[],
                "dimensionRefs":[],
                "metricRefs":[],
                "standardBindings":[]
              }
            }
            """;
    }

    private void seedCanonicalModelAndCandidate(
        Scope current,
        DeliveryStatus status,
        int version,
        boolean publicationShape
    ) {
        TransactionTemplate transaction = new TransactionTemplate(
            transactionManager
        );
        transaction.executeWithoutResult(ignored -> {
            jdbc.update(
                """
                insert into modeling_warehouse_plan (
                    id, tenant_id, owner, code, name, owner_id,
                    onboarding_mode, lifecycle_status, status, version,
                    created_date, last_modified_date
                ) values (?, ?, ?, ?, ?, ?, 'BUSINESS_FIRST', 'DRAFT',
                          'DRAFT', 1, current_timestamp, current_timestamp)
                """,
                current.planId(),
                TENANT,
                ACTOR,
                "f4_" + current.planId().toString().replace("-", ""),
                "F4 strict audit plan",
                ACTOR
            );
            jdbc.update(
                """
                insert into catalog_domain (
                    id, name, code, lifecycle_status, access_policy
                ) values (?, ?, ?, 'ACTIVE', 'PUBLIC')
                """,
                current.domainId(),
                "F4 strict audit domain",
                "F4_" + current.domainId().toString().replace("-", "")
            );
            ModelSpecView model = model(current);
            String snapshot = codec.write(model);
            jdbc.update(
                """
                insert into modeling_model_spec (
                    id, tenant_id, plan_id, layer, warehouse_layer_code, model_type,
                    implementation_mode, name, status, revision, version,
                    created_date, last_modified_date, contract_version,
                    domain_id, current_checksum, idempotency_key,
                    idempotency_request_hash, idempotency_response_snapshot
                ) values (?, ?, ?, 'DWD', 'DWD', 'FACT', 'DESIGNER_GENERATED', ?,
                          'DRAFT', 1, 1, current_timestamp, current_timestamp,
                          2, ?, ?, ?, ?, cast(? as jsonb))
                """,
                current.modelId(),
                TENANT,
                current.planId(),
                model.name(),
                current.domainId(),
                model.checksum(),
                "model-" + current.modelId(),
                "a".repeat(64),
                snapshot
            );
            jdbc.update(
                """
                insert into modeling_model_spec_revision (
                    id, model_spec_id, revision, status, content_checksum,
                    created_date, last_modified_date, tenant_id,
                    contract_version, snapshot_json, created_by
                ) values (?, ?, 1, 'DRAFT', ?, current_timestamp,
                          current_timestamp, ?, 2, cast(? as jsonb), ?)
                """,
                UUID.randomUUID(),
                current.modelId(),
                model.checksum(),
                TENANT,
                snapshot,
                ACTOR
            );
            seedImplementation(current);
            if (publicationShape) seedCompiledArtifact(current);
            assertThat(candidates.insert(candidate(current, status, version)))
                .isEqualTo(1);
        });
    }

    private void seedImplementation(Scope current) {
        Integer inserted = jdbc.queryForObject(
            """
            with implementation_head as (
                insert into modeling_model_implementation (
                    id, tenant_id, model_spec_id, plan_id, model_revision,
                    model_checksum, ownership, project_key, dbt_unique_id,
                    status, idempotency_key, created_by, created_date,
                    last_modified_date, implementation_revision,
                    current_implementation_checksum, input_mode, inputs_json,
                    field_mappings_json, settings_json, materialization
                ) values (?, ?, ?, ?, 1, ?, 'DESIGNER_GENERATED', 'dts_test', ?,
                          'ACTIVE', ?, ?, current_timestamp, current_timestamp,
                          1, ?, 'GENERATED',
                          cast('[{"generatorType":"RELEASE_IT","config":{}}]' as jsonb),
                          cast('[]' as jsonb), cast(? as jsonb), 'table')
                returning tenant_id, id, implementation_revision,
                          current_implementation_checksum, input_mode,
                          inputs_json, field_mappings_json, settings_json,
                          ownership, materialization
            ), implementation_revision as (
                insert into modeling_model_implementation_revision (
                    id, tenant_id, implementation_id, revision,
                    content_checksum, input_mode, inputs_json,
                    field_mappings_json, settings_json, ownership,
                    materialization, created_by, created_date
                )
                select ?, tenant_id, id, implementation_revision,
                       current_implementation_checksum, input_mode,
                       inputs_json, field_mappings_json, settings_json,
                       ownership, materialization, ?, current_timestamp
                  from implementation_head
                returning 1
            )
            select count(*)::int from implementation_revision
            """,
            Integer.class,
            current.implementationId(),
            TENANT,
            current.modelId(),
            current.planId(),
            model(current).checksum(),
            current.dbtUniqueId(),
            "implementation-" + current.implementationId(),
            ACTOR,
            IMPLEMENTATION_CHECKSUM,
            "{\"targetPhysicalName\":\"" +
            current.targetIdentifier() +
            "\",\"loadStrategy\":\"FULL\",\"partitionFields\":[]}",
            UUID.randomUUID(),
            ACTOR
        );
        assertThat(inserted).isEqualTo(1);
    }

    private void seedCompiledArtifact(Scope current) {
        String sql =
            "{{ config(materialized='table',alias='" +
            current.targetIdentifier() +
            "') }}\nselect 1::uuid as project_id\n";
        jdbc.update(
            """
            insert into modeling_dbt_artifact (
                id, model_spec_id, plan_id, project_key, dbt_unique_id,
                artifact_key, artifact_type, path, content_checksum, content,
                status, revision, model_checksum, ownership, idempotency_key,
                implementation_revision, node_kind, materialization,
                physical_asset_ref, created_date, last_modified_date
            ) values (?, ?, ?, 'dts_test', ?, ?, 'SQL', ?, ?, ?, 'COMPILED',
                      1, ?, 'DESIGNER_GENERATED', ?, 1, 'MODEL', 'table',
                      null, current_timestamp, current_timestamp)
            """,
            UUID.randomUUID(),
            current.modelId(),
            current.planId(),
            current.dbtUniqueId(),
            "SQL:models/dwd/" + current.targetIdentifier() + ".sql",
            "models/dwd/" + current.targetIdentifier() + ".sql",
            sha256(sql),
            sql,
            model(current).checksum(),
            "artifact-" + current.modelId()
        );
    }

    private QueuedBuildGroup seedSubmittedMaterialization(Scope current) {
        return seedSubmittedMaterialization(current, true);
    }

    private QueuedBuildGroup seedSubmittedMaterialization(Scope current, boolean consumeRuntime) {
        seedCanonicalModelAndCandidate(
            current,
            DeliveryStatus.DRAFT,
            1,
            true
        );
        QueuedBuildGroup build = materializationStart
            .startWithBuild(
                TENANT,
                ACTOR,
                current.candidateId(),
                1,
                "start-" + current.candidateId(),
                "prepare strict audit rollback proof"
            )
            .build();
        assertThat(build).isNotNull();
        var claimed = materializationDispatch
            .claimNext(NOW, Duration.ofMinutes(5))
            .orElseThrow();
        assertThat(claimed.id()).isEqualTo(build.pipelineRunGroupId());
        materializationDispatch.markPrepared(
            claimed.id(),
            build.artifactBundleChecksum(),
            "sha256:" + sha256(claimed.id().toString()),
            NOW.plus(Duration.ofMinutes(10)),
            NOW
        );
        if (consumeRuntime) {
            UUID leaseId = UUID.randomUUID();
            runtimeProfileLeases.issue(
                new LeaseRecord(
                    leaseId,
                    TENANT,
                    build.runs().getFirst().id(),
                    build.airflowRunId(),
                    "PROD",
                    build.executionTargetKey(),
                    "dev",
                    "sha256:" + sha256("credential:" + claimed.id()),
                    LeaseStatus.ISSUED,
                    NOW,
                    NOW.plus(Duration.ofDays(1)),
                    null,
                    null
                )
            );
            assertThat(runtimeProfileLeases.consume(leaseId)).isTrue();
            assertThat(
                materializationDispatch.attachRuntimeLease(
                    claimed.id(),
                    leaseId,
                    NOW.plus(Duration.ofMinutes(1))
                )
            ).isTrue();
            sourceAvailability.pinDispatchCurrent(claimed.id(), NOW);
        }
        materializationDispatch.markSubmitted(claimed.id(), false, NOW);
        reset(auditService);
        when(
            auditService.auditActionStrict(
                anyString(),
                any(AuditStage.class),
                anyString(),
                any()
            )
        ).thenReturn(UUID.randomUUID());
        when(
            auditService.auditActionAsStrict(
                anyString(),
                anyString(),
                any(Instant.class),
                anyString(),
                any(AuditStage.class),
                anyString(),
                any()
            )
        ).thenReturn(UUID.randomUUID());
        return build;
    }

    private CandidateView seedPublishingCandidate(Scope current)
        throws Exception {
        QueuedBuildGroup build = seedSubmittedMaterialization(current);
        writeArtifacts(current, build);
        configureExternalArtifactReads(current, build);
        runArtifacts.syncAndProbe(
            build.pipelineRunGroupId(),
            new SyncProbeCommand(
                "RELEASE_BUILD",
                build.artifactBundleChecksum()
            )
        );
        assertThat(pipelineStatus(build.pipelineRunGroupId()))
            .isEqualTo("BUILT");
        assertThat(candidateStatus(current)).isEqualTo("BUILDING");
        assertThat(
            sourceAvailability
                .checkPinnedCurrentForUpdate(build.pipelineRunGroupId())
                .current()
        ).isTrue();
        runArtifacts.finalizeRun(
            build.pipelineRunGroupId(),
            new FinalizeCommand("SUCCEEDED")
        );

        CandidateView candidate = candidates
            .find(TENANT, current.candidateId())
            .orElseThrow();
        assertThat(candidate.status()).isEqualTo(DeliveryStatus.BUILT);
        candidate = transition(
            candidate,
            "quality-operator",
            DeliveryStatus.QUALITY_RUNNING
        );
        candidate = transition(
            candidate,
            "quality-operator",
            DeliveryStatus.QUALITY_PASSED
        );
        candidate = transition(
            candidate,
            "release-submitter",
            DeliveryStatus.REVIEW_PENDING
        );
        candidate = transition(
            candidate,
            "release-approver",
            DeliveryStatus.APPROVED
        );
        candidate = transition(candidate, ACTOR, DeliveryStatus.PUBLISHING);
        assertThat(candidate.status()).isEqualTo(DeliveryStatus.PUBLISHING);
        assertThat(
            count(
                "select count(*) from modeling_materialization_dispatch where tenant_id = ? and candidate_id = ? and status = 'COMPLETED'",
                TENANT,
                current.candidateId()
            )
        ).isEqualTo(1);
        assertThat(publicationEvidence.requireCurrent(candidate, true))
            .hasSize(1);
        return candidate;
    }

    private CandidateView transition(
        CandidateView candidate,
        String actor,
        DeliveryStatus target
    ) {
        return candidateCommands
            .transition(
                TENANT,
                actor,
                candidate.id(),
                new TransitionCommand(
                    candidate.version(),
                    target,
                    "f4-" + target.name().toLowerCase() + "-" + candidate.id(),
                    "prepare strict audit rollback publication"
                )
            )
            .candidate();
    }

    private void configureExternalArtifactReads(
        Scope current,
        QueuedBuildGroup build
    ) {
        when(
            scopedProjects.verifyCandidateProject(
                build.artifactBundleChecksum()
            )
        ).thenReturn(project);
        PhysicalRelationInspector inspector =
            org.mockito.Mockito.mock(PhysicalRelationInspector.class);
        when(inspectorRegistry.require("postgres")).thenReturn(inspector);
        when(inspector.adapter()).thenReturn("postgres");
        when(inspector.dataTypeMatches(anyString(), anyString()))
            .thenReturn(true);
        when(inspector.observe(any(), any())).thenReturn(
            new PhysicalRelationObservation(
                true,
                ExpectedRelationType.TABLE,
                List.of(
                    new PhysicalColumn(1, "project_id", "uuid", false)
                ),
                "e".repeat(64),
                NOW,
                null
            )
        );
    }

    private void writeArtifacts(Scope current, QueuedBuildGroup build)
        throws Exception {
        Files.createDirectories(project.resolve("target"));
        String manifest =
            """
            {
              "metadata": {"invocation_id": "%s"},
              "nodes": {
                "%s": {
                  "unique_id": "%s",
                  "database": "warehouse",
                  "schema": "finance",
                  "alias": "%s",
                  "resource_type": "model",
                  "config": {
                    "materialized": "table",
                    "meta": {
                      "modelSpecId": "%s",
                      "modelRevision": 1,
                      "modelChecksum": "%s",
                      "implementationRevision": 1,
                      "implementationChecksum": "%s"
                    }
                  },
                  "columns": {
                    "project_id": {"name": "project_id", "data_type": "uuid"}
                  }
                }
              }
            }
            """.formatted(
                build.dbtInvocationId(),
                current.dbtUniqueId(),
                current.dbtUniqueId(),
                current.targetIdentifier(),
                current.modelId(),
                model(current).checksum(),
                IMPLEMENTATION_CHECKSUM
            );
        String results =
            """
            {
              "metadata": {"invocation_id": "%s"},
              "results": [{"unique_id": "%s", "status": "success"}]
            }
            """.formatted(
                build.dbtInvocationId(),
                current.dbtUniqueId()
            );
        Files.writeString(
            project.resolve("target/manifest.json"),
            manifest,
            StandardCharsets.UTF_8
        );
        Files.writeString(
            project.resolve("target/run_results.json"),
            results,
            StandardCharsets.UTF_8
        );
    }

    private void assertPublicationFactsVisibleInsideTransaction(
        Scope current,
        int publishedVersion
    ) {
        assertThat(modelStatus(current)).isEqualTo("PUBLISHED");
        assertThat(lifecycleReleaseCount(current)).isEqualTo(1);
        assertThat(catalogDatasetCount(current)).isEqualTo(1);
        assertThat(servingProjectionCount(current)).isEqualTo(1);
        assertThat(planBindingCount(current)).isEqualTo(1);
        assertThat(candidateStatus(current)).isEqualTo("PUBLISHED");
        assertThat(candidateVersion(current)).isEqualTo(publishedVersion);
        assertThat(businessOutboxCount(current)).isGreaterThanOrEqualTo(1);
    }

    private void assertPublicationFactsRolledBack(
        Scope current,
        int publishingVersion
    ) {
        assertThat(modelStatus(current)).isEqualTo("DRAFT");
        assertThat(lifecycleReleaseCount(current)).isZero();
        assertThat(catalogDatasetCount(current)).isZero();
        assertThat(servingProjectionCount(current)).isZero();
        assertThat(planBindingCount(current)).isZero();
        assertThat(candidateStatus(current)).isEqualTo("PUBLISHING");
        assertThat(candidateVersion(current)).isEqualTo(publishingVersion);
        assertThat(businessOutboxCount(current)).isZero();
        assertThat(auditOutboxCount(current.auditEventId())).isZero();
        assertThat(
            jdbc.queryForObject(
                """
                select count(*) from modeling_dbt_artifact
                 where model_spec_id = ? and physical_asset_ref is not null
                """,
                Integer.class,
                current.modelId()
            )
        ).isZero();
    }

    private void enqueueAuditThenVerifyVisible(String eventId) {
        auditOutbox.enqueue(
            new EnqueueCommand(
                TENANT,
                eventId,
                NOW,
                AUDIT_PAYLOAD_HASH,
                "{}"
            )
        );
        assertThat(auditOutboxCount(eventId)).isEqualTo(1);
    }

    private String modelStatus(Scope current) {
        return jdbc.queryForObject(
            "select status from modeling_model_spec where tenant_id = ? and id = ?",
            String.class,
            TENANT,
            current.modelId()
        );
    }

    private String candidateStatus(Scope current) {
        return jdbc.queryForObject(
            "select status from modeling_model_release_candidate where tenant_id = ? and id = ?",
            String.class,
            TENANT,
            current.candidateId()
        );
    }

    private int candidateVersion(Scope current) {
        return jdbc.queryForObject(
            "select version from modeling_model_release_candidate where tenant_id = ? and id = ?",
            Integer.class,
            TENANT,
            current.candidateId()
        );
    }

    private String pipelineStatus(UUID groupId) {
        return jdbc.queryForObject(
            "select status from modeling_pipeline_run where pipeline_run_group_id = ?",
            String.class,
            groupId
        );
    }

    private int observationCount(UUID groupId) {
        return count(
            "select count(*) from modeling_physical_relation_observation where pipeline_run_group_id = ?",
            groupId
        );
    }

    private int lifecycleReleaseCount(Scope current) {
        return count(
            "select count(*) from modeling_model_lifecycle_event where tenant_id = ? and model_spec_id = ? and event_type = 'RELEASE'",
            TENANT,
            current.modelId()
        );
    }

    private int catalogDatasetCount(Scope current) {
        return count(
            "select count(*) from catalog_dataset where cast(tags as jsonb) ->> 'modelSpecId' = ?",
            current.modelId().toString()
        );
    }

    private int servingProjectionCount(Scope current) {
        return count(
            "select count(*) from modeling_catalog_model_serving_projection where tenant_id = ? and model_spec_id = ?",
            TENANT,
            current.modelId()
        );
    }

    private int planBindingCount(Scope current) {
        return count(
            "select count(*) from modeling_plan_execution_binding where tenant_id = ? and plan_id = ?",
            TENANT,
            current.planId()
        );
    }

    private int businessOutboxCount(Scope current) {
        return count(
            "select count(*) from platform_event_outbox where aggregate_id = ?",
            current.candidateId().toString()
        );
    }

    private int auditOutboxCount(String eventId) {
        return count(
            "select count(*) from platform_audit_outbox where event_id = ?",
            eventId
        );
    }

    private int count(String sql, Object... arguments) {
        Integer value = jdbc.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }

    private CandidateView candidate(
        Scope current,
        DeliveryStatus status,
        int version
    ) {
        return new CandidateView(
            current.candidateId(),
            TENANT,
            current.planId(),
            "PROD",
            status,
            version,
            "candidate-" + current.candidateId(),
            "d".repeat(64),
            status == DeliveryStatus.PUBLISHING
                ? new DeliveryAuditView(
                    ACTOR,
                    NOW,
                    "release-submitter",
                    NOW,
                    "release-approver",
                    NOW,
                    null,
                    null
                )
                : new DeliveryAuditView(
                    ACTOR,
                    NOW,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                ),
            ACTOR,
            NOW,
            List.of(
                new EntryView(
                    current.entryId(),
                    TENANT,
                    current.candidateId(),
                    current.planId(),
                    current.modelId(),
                    1,
                    model(current).checksum(),
                    current.implementationId(),
                    ImplementationMode.DESIGNER_GENERATED,
                    status,
                    0,
                    "strict audit proof"
                )
            ),
            CandidateOrigin.BATCH_WORKBENCH,
            status == DeliveryStatus.PUBLISHING
                ? "postgres:warehouse/prod"
                : null,
            status == DeliveryStatus.PUBLISHING ? "postgres" : null,
            status == DeliveryStatus.PUBLISHING ? "warehouse" : null,
            status == DeliveryStatus.PUBLISHING ? "finance" : null
        );
    }

    private ModelSpecView model(Scope current) {
        ModelSpecView template = model(current, MODEL_CHECKSUM);
        return model(current, codec.contentChecksum(template));
    }

    private ModelSpecView model(Scope current, String checksum) {
        return new ModelSpecView(
            2,
            current.modelId(),
            current.planId(),
            current.domainId(),
            ModelType.FACT,
            Layer.DWD,
            "f4_strict_audit_" + current.modelId().toString().replace("-", ""),
            null,
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            null,
            null,
            new Grain("one row per project", List.of("project_id")),
            null,
            null,
            List.of(
                new ModelField(
                    "project_id",
                    "uuid",
                    false,
                    null,
                    FieldRole.KEY,
                    null
                )
            ),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            ModelStatus.DRAFT,
            1,
            checksum,
            NOW,
            NOW,
            CompatibilityMode.CANONICAL,
            null
        );
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                .formatHex(
                    MessageDigest.getInstance("SHA-256")
                        .digest(value.getBytes(StandardCharsets.UTF_8))
                );
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record Scope(
        UUID planId,
        UUID domainId,
        UUID modelId,
        UUID implementationId,
        UUID candidateId,
        UUID entryId,
        UUID sourceId,
        String dbtUniqueId,
        String targetIdentifier,
        String auditEventId
    ) {
        private static Scope create() {
            UUID modelId = UUID.randomUUID();
            UUID candidateId = UUID.randomUUID();
            String compact = modelId.toString().replace("-", "");
            return new Scope(
                UUID.randomUUID(),
                UUID.randomUUID(),
                modelId,
                UUID.randomUUID(),
                candidateId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "model.dts_test.f4_" + compact,
                "f4_" + compact,
                "f4-strict-audit-" + candidateId
            );
        }
    }
}
