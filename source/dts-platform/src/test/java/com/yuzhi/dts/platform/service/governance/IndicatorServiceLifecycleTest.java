package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorReference;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorVersion;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorReferenceRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorVersionRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.policy.DataLevel;
import com.yuzhi.dts.platform.service.catalog.CodeAssetGrantWriter;
import com.yuzhi.dts.platform.service.catalog.CatalogDomainDictionaryReadPort;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorDto;
import com.yuzhi.dts.platform.service.governance.request.IndicatorUpsertRequest;
import com.yuzhi.dts.platform.service.query.QueryGateway;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import com.yuzhi.dts.platform.service.security.SecuritySqlRewriter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class IndicatorServiceLifecycleTest {

    private static final Instant BASELINE = Instant.parse("2026-07-25T01:02:03Z");

    private final GovIndicatorDefinitionRepository indicatorRepository = mock(GovIndicatorDefinitionRepository.class);
    private final GovIndicatorVersionRepository versionRepository = mock(GovIndicatorVersionRepository.class);
    private final GovIndicatorReferenceRepository referenceRepository = mock(GovIndicatorReferenceRepository.class);
    private final CatalogDomainDictionaryReadPort catalogDomains = mock(CatalogDomainDictionaryReadPort.class);
    private final CodeAssetGrantWriter codeAssetGrantWriter = mock(CodeAssetGrantWriter.class);
    private final AccessChecker accessChecker = mock(AccessChecker.class);
    private final OrganizationVisibilityService organizationVisibilityService = mock(OrganizationVisibilityService.class);
    private final IndicatorDerivationValidationService derivationValidationService = mock(
        IndicatorDerivationValidationService.class
    );
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final IndicatorService service = new IndicatorService(
        indicatorRepository,
        versionRepository,
        referenceRepository,
        mock(CatalogDatasetRepository.class),
        accessChecker,
        organizationVisibilityService,
        mock(QueryGateway.class),
        mock(SecuritySqlRewriter.class),
        objectMapper,
        catalogDomains,
        codeAssetGrantWriter,
        derivationValidationService
    );

    @BeforeEach
    void setUp() {
        when(indicatorRepository.save(any(GovIndicatorDefinition.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(versionRepository.save(any(GovIndicatorVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(referenceRepository.save(any(GovIndicatorReference.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accessChecker.resolveHighestDataLevel()).thenReturn(DataLevel.DATA_CONFIDENTIAL);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsOrdinaryPutForPublishedIndicatorWithoutMutatingIt() {
        GovIndicatorDefinition published = indicator(UUID.randomUUID(), "GMV", "GMV", "PUBLISHED", "v1");
        when(indicatorRepository.findByIdForUpdate(published.getId())).thenReturn(Optional.of(published));

        IndicatorUpsertRequest request = request("GMV", "Changed", "DRAFT", "v1", null);

        assertThatThrownBy(() -> service.update(published.getId(), request, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("发布新版本");
        assertThat(published.getName()).isEqualTo("GMV");
        assertThat(published.getStatus()).isEqualTo("PUBLISHED");
        verify(indicatorRepository, never()).save(any(GovIndicatorDefinition.class));
    }

    @Test
    void resolvesReferencedIndicatorsInOneBatchAndAppliesExistingReadPolicy() {
        UUID visibleId = UUID.fromString("71000000-0000-0000-0000-000000000001");
        UUID hiddenId = UUID.fromString("71000000-0000-0000-0000-000000000002");
        GovIndicatorDefinition visible = indicator(visibleId, "VISIBLE", "Visible", "PUBLISHED", "v1");
        visible.setOwnerDept("dept-a");
        visible.setDataLevel("DATA_INTERNAL");
        GovIndicatorDefinition hidden = indicator(hiddenId, "HIDDEN", "Hidden", "PUBLISHED", "v1");
        hidden.setOwnerDept("dept-b");
        hidden.setDataLevel("DATA_INTERNAL");
        authenticate("alice", "dept-a");
        when(indicatorRepository.findAllById(List.of(visibleId, hiddenId))).thenReturn(List.of(hidden, visible));

        assertThat(service.listForRelationshipGraph(Set.of(hiddenId, visibleId), "dept-a", 500))
            .extracting(IndicatorDto::getId)
            .containsExactly(visibleId);

        verify(indicatorRepository).findAllById(List.of(visibleId, hiddenId));
    }

    @Test
    void projectsOneHopIndicatorDependenciesInBatchesAndAuthorizesBothEndpoints() {
        UUID sourceId = UUID.fromString("72000000-0000-0000-0000-000000000001");
        UUID visibleTargetId = UUID.fromString("72000000-0000-0000-0000-000000000002");
        UUID hiddenTargetId = UUID.fromString("72000000-0000-0000-0000-000000000003");
        GovIndicatorDefinition source = indicator(sourceId, "SOURCE", "Source", "PUBLISHED", "v2");
        GovIndicatorDefinition visibleTarget = indicator(visibleTargetId, "VISIBLE", "Visible", "PUBLISHED", "v4");
        GovIndicatorDefinition hiddenTarget = indicator(hiddenTargetId, "HIDDEN", "Hidden", "PUBLISHED", "v1");
        source.setOwnerDept("dept-a");
        visibleTarget.setOwnerDept("dept-a");
        hiddenTarget.setOwnerDept("dept-b");
        source.setDataLevel("DATA_INTERNAL");
        visibleTarget.setDataLevel("DATA_INTERNAL");
        hiddenTarget.setDataLevel("DATA_INTERNAL");
        GovIndicatorReference visibleReference = reference(source, "INDICATOR", visibleTargetId.toString());
        GovIndicatorReference hiddenReference = reference(source, "INDICATOR", hiddenTargetId.toString());
        authenticate("alice", "dept-a");
        when(indicatorRepository.findAllById(List.of(sourceId))).thenReturn(List.of(source));
        when(referenceRepository.findIndicatorDependenciesForRelationshipGraph(
                List.of(sourceId),
                PageRequest.of(0, 1001)
            ))
            .thenReturn(List.of(visibleReference, hiddenReference));
        when(indicatorRepository.findAllById(List.of(visibleTargetId, hiddenTargetId)))
            .thenReturn(List.of(hiddenTarget, visibleTarget));

        IndicatorService.RelationshipGraphProjection projection = service.projectForRelationshipGraph(
            Set.of(sourceId),
            "dept-a",
            500,
            1000
        );

        assertThat(projection.indicators())
            .extracting(IndicatorDto::getId)
            .containsExactly(sourceId, visibleTargetId);
        assertThat(projection.dependencies())
            .containsExactly(new IndicatorService.IndicatorDependency(sourceId, visibleTargetId));
        assertThat(projection.truncated()).isFalse();
        verify(indicatorRepository).findAllById(List.of(sourceId));
        verify(indicatorRepository).findAllById(List.of(visibleTargetId, hiddenTargetId));
    }

    @Test
    void hiddenIndicatorTargetsDoNotConsumeTheVisibleNodeBudget() {
        UUID sourceId = UUID.fromString("72100000-0000-0000-0000-000000000001");
        UUID hiddenTargetId = UUID.fromString("72100000-0000-0000-0000-000000000002");
        UUID visibleTargetId = UUID.fromString("72100000-0000-0000-0000-000000000003");
        GovIndicatorDefinition source = indicator(sourceId, "SOURCE", "Source", "PUBLISHED", "v1");
        GovIndicatorDefinition hiddenTarget = indicator(hiddenTargetId, "HIDDEN", "Hidden", "PUBLISHED", "v1");
        GovIndicatorDefinition visibleTarget = indicator(visibleTargetId, "VISIBLE", "Visible", "PUBLISHED", "v1");
        source.setOwnerDept("dept-a");
        visibleTarget.setOwnerDept("dept-a");
        hiddenTarget.setOwnerDept("dept-b");
        source.setDataLevel("DATA_INTERNAL");
        visibleTarget.setDataLevel("DATA_INTERNAL");
        hiddenTarget.setDataLevel("DATA_INTERNAL");
        authenticate("alice", "dept-a");
        when(indicatorRepository.findAllById(List.of(sourceId))).thenReturn(List.of(source));
        when(
            referenceRepository.findIndicatorDependenciesForRelationshipGraph(
                List.of(sourceId),
                PageRequest.of(0, 1001)
            )
        )
            .thenReturn(
                List.of(
                    reference(source, "INDICATOR", hiddenTargetId.toString()),
                    reference(source, "INDICATOR", visibleTargetId.toString())
                )
            );
        when(indicatorRepository.findAllById(List.of(hiddenTargetId, visibleTargetId)))
            .thenReturn(List.of(hiddenTarget, visibleTarget));

        IndicatorService.RelationshipGraphProjection projection = service.projectForRelationshipGraph(
            Set.of(sourceId),
            "dept-a",
            2,
            1000
        );

        assertThat(projection.indicators())
            .extracting(IndicatorDto::getId)
            .containsExactly(sourceId, visibleTargetId);
        assertThat(projection.dependencies())
            .containsExactly(new IndicatorService.IndicatorDependency(sourceId, visibleTargetId));
        assertThat(projection.truncated()).isFalse();
        verify(indicatorRepository).findAllById(List.of(hiddenTargetId, visibleTargetId));
    }

    @Test
    void createIgnoresClientSuppliedPublishedStatusAndVersion() {
        when(indicatorRepository.save(any(GovIndicatorDefinition.class))).thenAnswer(invocation -> {
            GovIndicatorDefinition value = invocation.getArgument(0);
            value.setId(UUID.randomUUID());
            return value;
        });
        when(referenceRepository.findByIndicatorOrderByCreatedDateAsc(any(GovIndicatorDefinition.class))).thenReturn(List.of());

        IndicatorDto created = service.create(request("GMV", "GMV", "PUBLISHED", "v99", null), null);

        assertThat(created.getStatus()).isEqualTo("DRAFT");
        assertThat(created.getVersion()).isEqualTo("v1");
    }

    @Test
    void rejectsMalformedStructuredConfigurationBeforePersistingDraft() {
        IndicatorUpsertRequest malformed = request("GMV", "GMV", "DRAFT", "v1", null);
        malformed.setJoinConfig("{\"type\":\"LEFT\"} trailing");

        assertThatThrownBy(() -> service.create(malformed, null))
            .isInstanceOf(IndicatorRequestException.class)
            .hasMessageContaining("配置不合法");
        verify(indicatorRepository, never()).save(any(GovIndicatorDefinition.class));
    }

    @Test
    void treatsMalformedPersistedConfigurationAsLifecycleConflictDuringValidation() {
        GovIndicatorDefinition malformed = indicator(UUID.randomUUID(), "GMV", "GMV", "DRAFT", "v1");
        malformed.setJoinConfig("{\"type\":\"LEFT\"} trailing");
        when(indicatorRepository.findByIdForUpdate(malformed.getId())).thenReturn(Optional.of(malformed));

        assertThatThrownBy(() -> service.validateComputeRule(malformed.getId(), null))
            .isInstanceOf(IndicatorConflictException.class)
            .hasMessageContaining("配置不合法");
    }

    @Test
    void draftUpdateCreatesMissingIndicatorReferencesBeforeDeletingObsoleteOnes() {
        GovIndicatorDefinition draft = indicator(UUID.randomUUID(), "AVG_ORDER", "Average order", "DRAFT", "v1");
        GovIndicatorDefinition gmv = indicator(UUID.randomUUID(), "GMV", "GMV", "PUBLISHED", "v1");
        GovIndicatorDefinition orders = indicator(UUID.randomUUID(), "ORDER_COUNT", "Orders", "PUBLISHED", "v1");
        GovIndicatorReference datasetRef = reference(draft, "DATASET", UUID.randomUUID().toString());
        GovIndicatorReference existingGmv = reference(draft, "INDICATOR", gmv.getId().toString());
        GovIndicatorReference obsolete = reference(draft, "INDICATOR", UUID.randomUUID().toString());

        when(indicatorRepository.findByIdForUpdate(draft.getId())).thenReturn(Optional.of(draft));
        when(indicatorRepository.findFirstByCodeIgnoreCase(anyString())).thenAnswer(invocation -> {
            String code = invocation.getArgument(0);
            if ("AVG_ORDER".equalsIgnoreCase(code)) return Optional.of(draft);
            if ("GMV".equalsIgnoreCase(code)) return Optional.of(gmv);
            if ("ORDER_COUNT".equalsIgnoreCase(code)) return Optional.of(orders);
            return Optional.empty();
        });
        when(referenceRepository.findByIndicatorOrderByCreatedDateAsc(draft))
            .thenReturn(List.of(datasetRef, existingGmv, obsolete));

        IndicatorDto saved = service.update(
            draft.getId(),
            request("AVG_ORDER", "Average order v2", "DRAFT", "v1", "[\"GMV\",\"ORDER_COUNT\"]"),
            null
        );

        assertThat(saved.getStatus()).isEqualTo("DRAFT");
        assertThat(saved.getVersion()).isEqualTo("v1");
        ArgumentCaptor<GovIndicatorReference> created = ArgumentCaptor.forClass(GovIndicatorReference.class);
        InOrder order = inOrder(referenceRepository);
        order.verify(referenceRepository).save(created.capture());
        order.verify(referenceRepository).delete(obsolete);
        assertThat(created.getValue().getRefType()).isEqualTo("INDICATOR");
        assertThat(created.getValue().getRefTarget()).isEqualTo(orders.getId().toString());
        verify(referenceRepository, never()).delete(datasetRef);
        verify(referenceRepository, never()).delete(existingGmv);
    }

    @Test
    void updateKeepsStableCodeAndIgnoresClientLifecycleFields() {
        GovIndicatorDefinition draft = indicator(UUID.randomUUID(), "GMV", "GMV", "DRAFT", "v3");
        when(indicatorRepository.findByIdForUpdate(draft.getId())).thenReturn(Optional.of(draft));

        assertThatThrownBy(() ->
            service.update(draft.getId(), request("RENAMED", "Changed", "PUBLISHED", "v99", null), null)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("编码");

        IndicatorDto updated = service.update(
            draft.getId(),
            request("GMV", "Changed", "PUBLISHED", "v99", null),
            null
        );
        assertThat(updated.getCode()).isEqualTo("GMV");
        assertThat(updated.getStatus()).isEqualTo("DRAFT");
        assertThat(updated.getVersion()).isEqualTo("v3");
    }

    @Test
    void stagesPublishedIndicatorAsNextDraftVersionAndKeepsCodeImmutable() {
        GovIndicatorDefinition published = indicator(UUID.randomUUID(), "AVG_ORDER", "Average order", "PUBLISHED", "v1");
        published.setLastValidationStatus("SUCCESS");
        published.setLastValidationSignature("old");
        GovIndicatorDefinition gmv = indicator(UUID.randomUUID(), "GMV", "GMV", "PUBLISHED", "v3");
        GovIndicatorVersion v1 = version(published, "v1", "PUBLISHED");

        when(indicatorRepository.findByIdForUpdate(published.getId())).thenReturn(Optional.of(published));
        when(indicatorRepository.findFirstByCodeIgnoreCase(anyString())).thenAnswer(invocation -> {
            String code = invocation.getArgument(0);
            if ("AVG_ORDER".equalsIgnoreCase(code)) return Optional.of(published);
            if ("GMV".equalsIgnoreCase(code)) return Optional.of(gmv);
            return Optional.empty();
        });
        when(versionRepository.findByIndicatorOrderByCreatedDateDesc(published)).thenReturn(List.of(v1));
        when(referenceRepository.findByIndicatorOrderByCreatedDateAsc(published)).thenReturn(List.of());

        IndicatorDto staged = service.stageRevision(
            published.getId(),
            request("AVG_ORDER", "Average order revision", "PUBLISHED", "v1", "[\"GMV\"]"),
            null
        );

        assertThat(staged.getCode()).isEqualTo("AVG_ORDER");
        assertThat(staged.getStatus()).isEqualTo("DRAFT");
        assertThat(staged.getVersion()).isEqualTo("v2");
        assertThat(staged.getLastValidationStatus()).isNull();
        assertThat(staged.getLastValidationSignature()).isNull();

        ArgumentCaptor<GovIndicatorVersion> snapshot = ArgumentCaptor.forClass(GovIndicatorVersion.class);
        verify(versionRepository).save(snapshot.capture());
        assertThat(snapshot.getValue().getVersion()).isEqualTo("v2");
        assertThat(snapshot.getValue().getStatus()).isEqualTo("DRAFT");

        published.setStatus("PUBLISHED");
        assertThatThrownBy(() ->
            service.stageRevision(
                published.getId(),
                request("RENAMED", "Bad revision", "PUBLISHED", "v1", "[\"GMV\"]"),
                null
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("编码");
    }

    @Test
    void createSynchronizesDependencyIndicatorsAsUuidReferences() {
        UUID createdId = UUID.randomUUID();
        GovIndicatorDefinition dependency = indicator(UUID.randomUUID(), "GMV", "GMV", "PUBLISHED", "v1");
        when(indicatorRepository.save(any(GovIndicatorDefinition.class))).thenAnswer(invocation -> {
            GovIndicatorDefinition value = invocation.getArgument(0);
            value.setId(createdId);
            return value;
        });
        when(indicatorRepository.findFirstByCodeIgnoreCase(anyString())).thenAnswer(invocation ->
            "GMV".equalsIgnoreCase(invocation.getArgument(0)) ? Optional.of(dependency) : Optional.empty()
        );
        when(referenceRepository.findByIndicatorOrderByCreatedDateAsc(any(GovIndicatorDefinition.class))).thenReturn(List.of());

        service.create(request("AVG_ORDER", "Average order", "DRAFT", "v1", "[\"GMV\"]"), null);

        ArgumentCaptor<GovIndicatorReference> created = ArgumentCaptor.forClass(GovIndicatorReference.class);
        verify(referenceRepository).save(created.capture());
        assertThat(created.getValue().getIndicator().getId()).isEqualTo(createdId);
        assertThat(created.getValue().getRefTarget()).isEqualTo(dependency.getId().toString());
    }

    @Test
    void archiveAdvancesVersionAndPreservesPublishedSnapshot() {
        GovIndicatorDefinition published = indicator(UUID.randomUUID(), "GMV", "GMV", "PUBLISHED", "v1");
        GovIndicatorVersion v1 = version(published, "v1", "PUBLISHED");
        v1.setReleasedAt(Instant.parse("2026-07-01T00:00:00Z"));
        v1.setSnapshotJson("{\"status\":\"PUBLISHED\"}");
        when(indicatorRepository.findByIdForUpdate(published.getId())).thenReturn(Optional.of(published));
        when(versionRepository.findByIndicatorOrderByCreatedDateDesc(published)).thenReturn(List.of(v1));
        when(versionRepository.findByIndicatorAndVersion(published, "v2")).thenReturn(Optional.empty());

        IndicatorDto archived = service.archive(published.getId(), null);

        assertThat(archived.getStatus()).isEqualTo("ARCHIVED");
        assertThat(archived.getVersion()).isEqualTo("v2");
        assertThat(v1.getStatus()).isEqualTo("PUBLISHED");
        assertThat(v1.getSnapshotJson()).isEqualTo("{\"status\":\"PUBLISHED\"}");
        ArgumentCaptor<GovIndicatorVersion> archivedSnapshot = ArgumentCaptor.forClass(GovIndicatorVersion.class);
        verify(versionRepository).save(archivedSnapshot.capture());
        assertThat(archivedSnapshot.getValue().getVersion()).isEqualTo("v2");
        assertThat(archivedSnapshot.getValue().getStatus()).isEqualTo("ARCHIVED");
    }

    @Test
    void publishRejectsArchivedIndicatorWithoutOverwritingArchivedSnapshot() {
        GovIndicatorDefinition archived = indicator(UUID.randomUUID(), "GMV", "GMV", "ARCHIVED", "v2");
        when(indicatorRepository.findByIdForUpdate(archived.getId())).thenReturn(Optional.of(archived));

        assertThatThrownBy(() -> service.publish(archived.getId(), null))
            .isInstanceOf(IndicatorConflictException.class)
            .hasMessageContaining("草稿");

        verify(indicatorRepository, never()).save(any(GovIndicatorDefinition.class));
        verify(versionRepository, never()).save(any(GovIndicatorVersion.class));
        verify(indicatorRepository).findByIdForUpdate(archived.getId());
        assertThat(archived.getStatus()).isEqualTo("ARCHIVED");
    }

    @Test
    void updateRequiresMatchingLastModifiedPreconditionUnderRowLock() {
        GovIndicatorDefinition draft = indicator(UUID.randomUUID(), "GMV", "GMV", "DRAFT", "v1");
        when(indicatorRepository.findByIdForUpdate(draft.getId())).thenReturn(Optional.of(draft));

        IndicatorUpsertRequest missing = request("GMV", "Changed", "DRAFT", "v1", null);
        missing.setExpectedLastModifiedDate(null);
        assertThatThrownBy(() -> service.update(draft.getId(), missing, null))
            .isInstanceOf(OptimisticLockingFailureException.class)
            .hasMessageContaining("刷新");

        IndicatorUpsertRequest stale = request("GMV", "Changed", "DRAFT", "v1", null);
        stale.setExpectedLastModifiedDate(BASELINE.minusSeconds(1));
        assertThatThrownBy(() -> service.update(draft.getId(), stale, null))
            .isInstanceOf(OptimisticLockingFailureException.class)
            .hasMessageContaining("刷新");

        verify(indicatorRepository, times(2)).findByIdForUpdate(draft.getId());
        verify(indicatorRepository, never()).save(any(GovIndicatorDefinition.class));
    }

    @Test
    void rollbackPreservesCodeAppliesDefaultsAndSynchronizesDependenciesBeforeSnapshot() throws Exception {
        GovIndicatorDefinition current = indicator(UUID.randomUUID(), "STABLE_CODE", "Current", "PUBLISHED", "v3");
        GovIndicatorDefinition dependency = indicator(UUID.randomUUID(), "GMV", "GMV", "PUBLISHED", "v2");
        GovIndicatorVersion source = version(current, "v3", "PUBLISHED");
        IndicatorDto snapshot = new IndicatorDto();
        snapshot.setCode("HISTORICAL_CODE");
        snapshot.setName("Restored");
        snapshot.setDomain("sales");
        snapshot.setIsDerived(true);
        snapshot.setDependencyIndicators("[\"GMV\"]");
        source.setSnapshotJson(objectMapper.writeValueAsString(snapshot));

        when(indicatorRepository.findByIdForUpdate(current.getId())).thenReturn(Optional.of(current));
        when(versionRepository.findByIndicatorAndVersion(current, "v3")).thenReturn(Optional.of(source));
        when(versionRepository.findByIndicatorOrderByCreatedDateDesc(current)).thenReturn(List.of(source));
        when(versionRepository.findByIndicatorAndVersion(current, "v4")).thenReturn(Optional.empty());
        when(catalogDomains.existsByCode("sales")).thenReturn(true);
        when(indicatorRepository.findFirstByCodeIgnoreCase(anyString())).thenAnswer(invocation -> {
            String code = invocation.getArgument(0);
            if ("STABLE_CODE".equalsIgnoreCase(code)) return Optional.of(current);
            if ("GMV".equalsIgnoreCase(code)) return Optional.of(dependency);
            return Optional.empty();
        });
        when(referenceRepository.findByIndicatorOrderByCreatedDateAsc(current)).thenReturn(List.of());

        Map<String, Object> result = service.rollbackToVersion(current.getId(), "v3", null, "restore", false);

        IndicatorDto restored = (IndicatorDto) result.get("indicator");
        assertThat(restored.getCode()).isEqualTo("STABLE_CODE");
        assertThat(restored.getName()).isEqualTo("Restored");
        assertThat(restored.getStatus()).isEqualTo("DRAFT");
        assertThat(restored.getVersion()).isEqualTo("v4");
        assertThat(restored.getDataLevel()).isEqualTo("DATA_INTERNAL");

        InOrder order = inOrder(referenceRepository, codeAssetGrantWriter, versionRepository);
        order.verify(referenceRepository).save(any(GovIndicatorReference.class));
        order.verify(codeAssetGrantWriter).upsertCodeAsset(any(), any(), any(), any(), any());
        order.verify(versionRepository).save(any(GovIndicatorVersion.class));
    }

    @Test
    void rejectsForgedDepartmentHeaderForOrdinaryActor() {
        authenticate(AuthoritiesConstants.DEPT_DATA_OWNER, "DEPT_A");

        assertThatThrownBy(() -> service.get(UUID.randomUUID(), "DEPT_B"))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("department");

        verify(indicatorRepository, never()).findById(any(UUID.class));
    }

    @Test
    void rejectsMutationThatEscalatesAboveActorDataLevel() {
        authenticate(AuthoritiesConstants.DEPT_DATA_OWNER, "DEPT_A");
        when(accessChecker.resolveHighestDataLevel()).thenReturn(DataLevel.DATA_INTERNAL);
        GovIndicatorDefinition draft = indicator(UUID.randomUUID(), "GMV", "GMV", "DRAFT", "v1");
        draft.setOwnerDept("DEPT_A");
        draft.setDataLevel("DATA_INTERNAL");
        when(indicatorRepository.findByIdForUpdate(draft.getId())).thenReturn(Optional.of(draft));
        IndicatorUpsertRequest request = request("GMV", "GMV", "DRAFT", "v1", null);
        request.setOwnerDept("DEPT_A");
        request.setDataLevel("DATA_CONFIDENTIAL");

        assertThatThrownBy(() -> service.update(draft.getId(), request, "DEPT_A"))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("Access denied");

        verify(indicatorRepository, never()).save(any(GovIndicatorDefinition.class));
    }

    @Test
    void generationRejectsDraftArchivedAndStaleValidatedTargets() {
        for (String status : List.of("DRAFT", "ARCHIVED")) {
            GovIndicatorDefinition target = indicator(UUID.randomUUID(), status + "_CODE", status, status, "v1");
            when(indicatorRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
            assertThatThrownBy(() -> service.validateGenerationAccess(List.of(target.getId()), null))
                .isInstanceOf(IndicatorConflictException.class)
                .hasMessageContaining("已发布");
        }

        GovIndicatorDefinition stale = indicator(UUID.randomUUID(), "STALE", "Stale", "PUBLISHED", "v1");
        stale.setLastValidationStatus("SUCCESS");
        stale.setLastValidationSignature("stale");
        when(indicatorRepository.findByIdForUpdate(stale.getId())).thenReturn(Optional.of(stale));
        assertThatThrownBy(() -> service.validateGenerationAccess(List.of(stale.getId()), null))
            .isInstanceOf(IndicatorConflictException.class)
            .hasMessageContaining("校验");
    }

    @Test
    void generationLocksAndRejectsArchivedOrInvisibleDerivedDependency() {
        GovIndicatorDefinition target = indicator(UUID.randomUUID(), "DERIVED", "Derived", "PUBLISHED", "v2");
        target.setIsDerived(true);
        target.setDependencyIndicators("[\"BASE\"]");
        GovIndicatorDefinition dependency = indicator(UUID.randomUUID(), "BASE", "Base", "ARCHIVED", "v3");
        when(indicatorRepository.findFirstByCodeIgnoreCase("BASE")).thenReturn(Optional.of(dependency));
        markCurrentValidation(target);
        when(indicatorRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(indicatorRepository.findByIdForUpdate(dependency.getId())).thenReturn(Optional.of(dependency));

        assertThatThrownBy(() -> service.validateGenerationAccess(List.of(target.getId()), null))
            .isInstanceOf(IndicatorConflictException.class)
            .hasMessageContaining("依赖指标")
            .hasMessageContaining("已发布");
        verify(indicatorRepository).findByIdForUpdate(dependency.getId());

        dependency.setStatus("PUBLISHED");
        dependency.setOwnerDept("DEPT_B");
        markCurrentValidation(dependency);
        markCurrentValidation(target);
        assertThatThrownBy(() -> service.validateGenerationAccess(List.of(target.getId()), null))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("依赖指标");
    }

    @Test
    void blocksArchiveAndDeleteWhenPublishedDerivedIndicatorDependsOnCode() {
        GovIndicatorDefinition base = indicator(UUID.randomUUID(), "GMV", "GMV", "PUBLISHED", "v1");
        GovIndicatorDefinition derived = indicator(UUID.randomUUID(), "AVG_ORDER", "Average order", "PUBLISHED", "v1");
        derived.setIsDerived(true);
        derived.setDependencyIndicators("[\"GMV\"]");
        when(indicatorRepository.findByIdForUpdate(base.getId())).thenReturn(Optional.of(base));
        when(indicatorRepository.findByIsDerivedTrue()).thenReturn(List.of(derived));

        assertThatThrownBy(() -> service.archive(base.getId(), null))
            .isInstanceOf(IndicatorConflictException.class)
            .hasMessageContaining("AVG_ORDER");
        assertThatThrownBy(() -> service.delete(base.getId(), null))
            .isInstanceOf(IndicatorConflictException.class)
            .hasMessageContaining("AVG_ORDER");
        verify(indicatorRepository, never()).delete(base);
    }

    @Test
    void validationUsesTargetWriteLockBeforePersistingValidationMetadata() {
        GovIndicatorDefinition target = indicator(UUID.randomUUID(), "GMV", "GMV", "DRAFT", "v1");
        when(indicatorRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));

        assertThat(service.validateComputeRule(target.getId(), null).getStatus()).isEqualTo("FAILED");

        verify(indicatorRepository).findByIdForUpdate(target.getId());
        verify(indicatorRepository, never()).findById(target.getId());
        verify(indicatorRepository).save(target);
    }

    @Test
    void lifecycleMutationsAcquireTheSameDeterministicFullGraphLockFirst() {
        GovIndicatorDefinition archived = indicator(UUID.randomUUID(), "OLD", "Old", "ARCHIVED", "v2");
        GovIndicatorDefinition base = indicator(UUID.randomUUID(), "BASE", "Base", "PUBLISHED", "v1");
        when(indicatorRepository.findAllForLifecycleUpdate()).thenReturn(List.of(archived, base));
        when(indicatorRepository.findByIdForUpdate(archived.getId())).thenReturn(Optional.of(archived));
        when(indicatorRepository.findByIdForUpdate(base.getId())).thenReturn(Optional.of(base));
        when(versionRepository.findByIndicatorOrderByCreatedDateDesc(base)).thenReturn(List.of());
        when(versionRepository.findByIndicatorAndVersion(base, "v2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publish(archived.getId(), null))
            .isInstanceOf(IndicatorConflictException.class);
        service.archive(base.getId(), null);

        InOrder order = inOrder(indicatorRepository);
        order.verify(indicatorRepository).findAllForLifecycleUpdate();
        order.verify(indicatorRepository).findByIdForUpdate(archived.getId());
        order.verify(indicatorRepository).findAllForLifecycleUpdate();
        order.verify(indicatorRepository).findByIdForUpdate(base.getId());
    }

    @Test
    void lifecycleGraphRepositoryLockIsPessimisticAndUuidOrdered() throws Exception {
        java.lang.reflect.Method method = GovIndicatorDefinitionRepository.class.getMethod(
            "findAllForLifecycleUpdate"
        );

        assertThat(method.getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(method.getAnnotation(Query.class).value())
            .containsIgnoringCase("ORDER BY g.id");
    }

    @Test
    void derivedValidationRejectsDependencyOutsideActiveDepartment() {
        authenticate(AuthoritiesConstants.DEPT_DATA_OWNER, "DEPT_A");
        GovIndicatorDefinition target = indicator(UUID.randomUUID(), "AVG_ORDER", "Average order", "DRAFT", "v2");
        target.setOwnerDept("DEPT_A");
        target.setDataLevel("DATA_INTERNAL");
        GovIndicatorDefinition dependency = indicator(UUID.randomUUID(), "GMV", "GMV", "PUBLISHED", "v1");
        dependency.setOwnerDept("DEPT_B");
        dependency.setDataLevel("DATA_INTERNAL");
        when(indicatorRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(indicatorRepository.findFirstByCodeIgnoreCase("GMV")).thenReturn(Optional.of(dependency));
        when(accessChecker.resolveHighestDataLevel()).thenReturn(DataLevel.DATA_CONFIDENTIAL);
        when(organizationVisibilityService.isRoot(anyString())).thenReturn(false);
        when(derivationValidationService.validate(target.getId()))
            .thenReturn(new IndicatorDerivationValidationResult(true, "\"GMV\"", List.of(), List.of("GMV")));

        IndicatorDerivationValidationResult result = service.validateDerivation(target.getId(), "DEPT_A");

        assertThat(result.valid()).isFalse();
        assertThat(result.issueCodes()).contains("DERIVATION_DEPENDENCY_ACCESS_DENIED");
    }

    @Test
    void validationSignatureTracksDerivedGrainAndDependencyLifecycle() {
        GovIndicatorDefinition target = indicator(UUID.randomUUID(), "AVG_ORDER", "Average order", "DRAFT", "v2");
        target.setIsDerived(true);
        target.setExpressionSql("{{metric:GMV}}");
        target.setDependencyIndicators("[\"gmv\"]");
        target.setDimensionFields("[\"region_code\"]");
        target.setTimeGrain("MONTH");
        target.setWindowFunction("SUM");
        GovIndicatorDefinition dependency = indicator(UUID.randomUUID(), "GMV", "GMV", "PUBLISHED", "v1");
        dependency.setDataLevel("DATA_INTERNAL");

        when(indicatorRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(indicatorRepository.findFirstByCodeIgnoreCase("GMV")).thenReturn(Optional.of(dependency));
        when(indicatorRepository.findFirstByCodeIgnoreCase("gmv")).thenReturn(Optional.of(dependency));
        when(accessChecker.resolveHighestDataLevel()).thenReturn(DataLevel.DATA_CONFIDENTIAL);
        when(derivationValidationService.validate(target.getId()))
            .thenReturn(new IndicatorDerivationValidationResult(true, "\"GMV\"", List.of(), List.of("GMV")));

        service.validateDerivation(target.getId(), null);
        String publishedDependencySignature = target.getLastValidationSignature();

        dependency.setStatus("ARCHIVED");
        service.validateDerivation(target.getId(), null);
        String archivedDependencySignature = target.getLastValidationSignature();

        target.setDimensionFields("[\"store_code\"]");
        target.setTimeGrain("DAY");
        target.setWindowFunction("AVG");
        service.validateDerivation(target.getId(), null);

        assertThat(archivedDependencySignature).isNotEqualTo(publishedDependencySignature);
        assertThat(target.getLastValidationSignature()).isNotEqualTo(archivedDependencySignature);
    }

    private static IndicatorUpsertRequest request(
        String code,
        String name,
        String status,
        String version,
        String dependencies
    ) {
        IndicatorUpsertRequest request = new IndicatorUpsertRequest();
        request.setCode(code);
        request.setName(name);
        request.setStatus(status);
        request.setVersion(version);
        request.setDependencyIndicators(dependencies);
        request.setIsDerived(dependencies != null);
        request.setExpectedLastModifiedDate(BASELINE);
        return request;
    }

    private static GovIndicatorDefinition indicator(UUID id, String code, String name, String status, String version) {
        GovIndicatorDefinition value = new GovIndicatorDefinition();
        value.setId(id);
        value.setCode(code);
        value.setName(name);
        value.setStatus(status);
        value.setVersion(version);
        value.setLastModifiedDate(BASELINE);
        return value;
    }

    private void markCurrentValidation(GovIndicatorDefinition indicator) {
        indicator.setLastValidationStatus("SUCCESS");
        indicator.setLastValidationSignature(IndicatorValidationSignature.compute(indicator, objectMapper, indicatorRepository));
    }

    private static void authenticate(String role, String department) {
        Jwt jwt = Jwt
            .withTokenValue("test-token")
            .header("alg", "none")
            .subject("actor")
            .claim("roles", List.of(role))
            .claim("dept_code", department)
            .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private static GovIndicatorReference reference(GovIndicatorDefinition indicator, String type, String target) {
        GovIndicatorReference value = new GovIndicatorReference();
        value.setId(UUID.randomUUID());
        value.setIndicator(indicator);
        value.setRefType(type);
        value.setRefTarget(target);
        return value;
    }

    private static GovIndicatorVersion version(GovIndicatorDefinition indicator, String version, String status) {
        GovIndicatorVersion value = new GovIndicatorVersion();
        value.setId(UUID.randomUUID());
        value.setIndicator(indicator);
        value.setVersion(version);
        value.setStatus(status);
        return value;
    }
}
