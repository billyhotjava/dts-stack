package com.yuzhi.dts.platform.service.modeling.warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.modeling.WarehouseLayerRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.ArchitectureDictionaryWriteGuard;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerContract.CreateWarehouseLayerCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerContract.ResolvedWarehouseLayer;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerContract.StoredWarehouseLayer;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerContract.WarehouseLayerView;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WarehouseLayerApplicationServiceTest {

    private WarehouseLayerRepository repository;
    private AuditService audit;
    private ArchitectureDictionaryWriteGuard writeGuard;
    private WarehouseLayerApplicationService service;

    @BeforeEach
    void setUp() {
        repository = mock(WarehouseLayerRepository.class);
        audit = mock(AuditService.class);
        writeGuard = mock(ArchitectureDictionaryWriteGuard.class);
        service = new WarehouseLayerApplicationService(repository, audit, writeGuard);
    }

    @Test
    void listsBuiltInsFirstThenCustomRowsInStableOrder() {
        StoredWarehouseLayer customDwd = active("FIN_DETAIL", "DWD", "fin_dwd_");
        StoredWarehouseLayer customAds = active("BI_APP", "ADS", "bi_ads_");
        when(repository.findAllActive()).thenReturn(List.of(customAds, customDwd));

        List<WarehouseLayerView> views = service.list();

        assertThat(views).extracting(WarehouseLayerView::code)
            .containsExactly("ODS_RAW", "ODS_STANDARDIZED", "STG", "DWD", "DWS", "ADS", "FIN_DETAIL", "BI_APP");
        assertThat(views.get(0).builtin()).isTrue();
        assertThat(views.get(0).deletable()).isFalse();
        assertThat(views.get(0).disabledReason()).isEqualTo("平台内置分层不可删除");
        assertThat(views.get(6).builtin()).isFalse();
        assertThat(views.get(6).deletable()).isTrue();
        assertThat(views.get(6).disabledReason()).isNull();
        assertThat(views.get(0).layerGroup()).isEqualTo(WarehouseLayerContract.LayerGroup.STAGING);
        assertThat(views.get(3).layerGroup()).isEqualTo(WarehouseLayerContract.LayerGroup.COMMON);
        assertThat(views.get(5).layerGroup()).isEqualTo(WarehouseLayerContract.LayerGroup.APPLICATION);
        assertThat(views.get(3).modelTypes())
            .containsExactly(ModelSpecContract.ModelType.DIMENSION, ModelSpecContract.ModelType.FACT);
        assertThat(views.get(0).modelTypes()).isEmpty();
    }

    @Test
    void createsCustomLayerAndEmitsStrictAudit() {
        when(repository.codeExists("FIN_DETAIL")).thenReturn(false);
        CreateWarehouseLayerCommand command = new CreateWarehouseLayerCommand(
            "fin_detail", "财务明细层", "dwd", "财务域明细", "FIN_DWD_"
        );

        WarehouseLayerView view = service.create("alice", command);

        assertThat(view.code()).isEqualTo("FIN_DETAIL");
        assertThat(view.systemLayerCode()).isEqualTo("DWD");
        assertThat(view.builtin()).isFalse();
        assertThat(view.layerGroup()).isEqualTo(WarehouseLayerContract.LayerGroup.COMMON);
        assertThat(view.modelTypes())
            .containsExactly(ModelSpecContract.ModelType.DIMENSION, ModelSpecContract.ModelType.FACT);
        verify(repository).insert(any(StoredWarehouseLayer.class));
        verify(audit).auditActionStrict(eq("MODELING_WAREHOUSE_LAYER_CREATE"), eq(AuditStage.SUCCESS), eq("FIN_DETAIL"), any());
    }

    @Test
    void createsCustomLayerWithoutNamingPrefixWithoutFailing() {
        when(repository.codeExists("FIN_DETAIL")).thenReturn(false);

        WarehouseLayerView view = service.create("alice", new CreateWarehouseLayerCommand("FIN_DETAIL", "财务明细层", "DWD", null, null));

        assertThat(view.code()).isEqualTo("FIN_DETAIL");
        assertThat(view.layerGroup()).isEqualTo(WarehouseLayerContract.LayerGroup.COMMON);
        verify(repository).insert(any(StoredWarehouseLayer.class));
        verify(audit).auditActionStrict(eq("MODELING_WAREHOUSE_LAYER_CREATE"), eq(AuditStage.SUCCESS), eq("FIN_DETAIL"), any());
    }

    @Test
    void rejectsCreationWhenCodeCollidesWithBuiltIn() {
        assertThatThrownBy(() -> service.create("alice", new CreateWarehouseLayerCommand("DWD", "内置", "DWD", null, null)))
            .isInstanceOf(WarehouseLayerException.class)
            .extracting(ex -> ((WarehouseLayerException) ex).code())
            .isEqualTo("WAREHOUSE_LAYER_CODE_CONFLICT");
        verify(repository, never()).insert(any());
    }

    @Test
    void rejectsCreationWhenCodeWasUsedByDeletedCustomLayer() {
        when(repository.codeExists("FIN_DETAIL")).thenReturn(true);
        assertThatThrownBy(() -> service.create("alice", new CreateWarehouseLayerCommand("FIN_DETAIL", "重名", "DWD", null, null)))
            .isInstanceOf(WarehouseLayerException.class)
            .extracting(ex -> ((WarehouseLayerException) ex).code())
            .isEqualTo("WAREHOUSE_LAYER_CODE_CONFLICT");
        verify(repository, never()).insert(any());
    }

    @Test
    void rejectsMalformedCodeNameSystemTypeAndPrefix() {
        assertThatThrownBy(() -> service.create("alice", new CreateWarehouseLayerCommand("9BAD", "x", "DWD", null, null)))
            .isInstanceOf(WarehouseLayerException.class)
            .extracting(ex -> ((WarehouseLayerException) ex).code())
            .isEqualTo("WAREHOUSE_LAYER_CODE_INVALID");
        assertThatThrownBy(() -> service.create("alice", new CreateWarehouseLayerCommand("OK_CODE", " ", "DWD", null, null)))
            .isInstanceOf(WarehouseLayerException.class)
            .extracting(ex -> ((WarehouseLayerException) ex).code())
            .isEqualTo("WAREHOUSE_LAYER_NAME_REQUIRED");
        assertThatThrownBy(() -> service.create("alice", new CreateWarehouseLayerCommand("OK_CODE", "x", "BOGUS", null, null)))
            .isInstanceOf(WarehouseLayerException.class)
            .extracting(ex -> ((WarehouseLayerException) ex).code())
            .isEqualTo("WAREHOUSE_LAYER_SYSTEM_TYPE_INVALID");
        assertThatThrownBy(() -> service.create("alice", new CreateWarehouseLayerCommand("OK_CODE", "x", "DWD", null, "9BAD")))
            .isInstanceOf(WarehouseLayerException.class)
            .extracting(ex -> ((WarehouseLayerException) ex).code())
            .isEqualTo("WAREHOUSE_LAYER_PREFIX_INVALID");
    }

    @Test
    void rejectsBlankActorBeforeAnyWrite() {
        assertThatThrownBy(() -> service.create("  ", new CreateWarehouseLayerCommand("FIN_DETAIL", "财务明细层", "DWD", null, null)))
            .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).insert(any());
        assertThatThrownBy(() -> service.delete("", "FIN_DETAIL")).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).softDelete(anyString(), anyInt(), anyString(), any());
    }

    @Test
    void resolvesSourceModelsThroughExistingOdsPlanningLayers() {
        assertThat(service.resolveSelection(null, Layer.ODS).code()).isEqualTo("ODS_RAW");
        assertThat(service.resolveSelection("ODS", Layer.ODS).code()).isEqualTo("ODS_RAW");
        for (String code : List.of("ODS_RAW", "ODS_STANDARDIZED")) {
            assertThat(service.resolveSelection(code, Layer.ODS).canonicalLayer()).isEqualTo(Layer.ODS);
            assertThat(WarehouseLayerContract.modelTypesOf(code)).containsExactly(com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType.SOURCE);
        }
        assertThatThrownBy(() -> service.resolveSelection("STG", Layer.ODS)).isInstanceOf(WarehouseLayerException.class);
        when(repository.findByCode("SOURCE_CUSTOM")).thenReturn(Optional.of(active("SOURCE_CUSTOM", "ODS_RAW", null)));
        assertThat(service.resolveSelection("SOURCE_CUSTOM", Layer.ODS).code()).isEqualTo("SOURCE_CUSTOM");
    }

    @Test
    void resolvesCustomSelectionToItsImmutableSystemLayer() {
        when(repository.findByCode("FIN_DETAIL")).thenReturn(Optional.of(active("FIN_DETAIL", "DWD", null)));

        ResolvedWarehouseLayer resolved = service.resolveSelection("FIN_DETAIL", Layer.DWD);

        assertThat(resolved.code()).isEqualTo("FIN_DETAIL");
        assertThat(resolved.canonicalLayer()).isEqualTo(Layer.DWD);
        assertThat(resolved.builtin()).isFalse();
    }

    @Test
    void resolvesBlankSelectionToCanonicalTargetLayer() {
        ResolvedWarehouseLayer resolved = service.resolveSelection(null, Layer.DWS);

        assertThat(resolved.code()).isEqualTo("DWS");
        assertThat(resolved.canonicalLayer()).isEqualTo(Layer.DWS);
        assertThat(resolved.builtin()).isTrue();
    }

    @Test
    void rejectsASelectionWhoseSystemTypeDoesNotMatchTheModelTarget() {
        when(repository.findByCode("FIN_SUMMARY")).thenReturn(Optional.of(active("FIN_SUMMARY", "DWS", null)));

        assertThatThrownBy(() -> service.resolveSelection("FIN_SUMMARY", Layer.DWD))
            .isInstanceOf(WarehouseLayerException.class)
            .extracting(ex -> ((WarehouseLayerException) ex).code())
            .isEqualTo("MODEL_SPEC_WAREHOUSE_LAYER_TYPE_MISMATCH");
    }

    @Test
    void rejectsMissingAndInactiveSelections() {
        when(repository.findByCode("MISSING")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.resolveSelection("MISSING", Layer.DWD))
            .isInstanceOf(WarehouseLayerException.class)
            .extracting(ex -> ((WarehouseLayerException) ex).code())
            .isEqualTo("MODEL_SPEC_WAREHOUSE_LAYER_NOT_FOUND");

        StoredWarehouseLayer deleted = new StoredWarehouseLayer(
            UUID.randomUUID(), "GONE_LAYER", "已删除", "DWD", WarehouseLayerContract.groupOf("DWD"), null, null, "DELETED", 2, "alice",
            Instant.now(), "alice", Instant.now()
        );
        when(repository.findByCode("GONE_LAYER")).thenReturn(Optional.of(deleted));
        assertThatThrownBy(() -> service.resolveSelection("GONE_LAYER", Layer.DWD))
            .isInstanceOf(WarehouseLayerException.class)
            .extracting(ex -> ((WarehouseLayerException) ex).code())
            .isEqualTo("MODEL_SPEC_WAREHOUSE_LAYER_INACTIVE");
    }

    @Test
    void auditsAndRejectsDeleteWhileAnActiveModelReferencesTheLayer() {
        when(repository.findByCode("FIN_DETAIL")).thenReturn(Optional.of(active("FIN_DETAIL", "DWD", null)));
        when(repository.countActiveModelReferences("FIN_DETAIL")).thenReturn(2L);

        assertThatThrownBy(() -> service.delete("alice", "FIN_DETAIL"))
            .isInstanceOf(WarehouseLayerException.class)
            .extracting(ex -> ((WarehouseLayerException) ex).code())
            .isEqualTo("WAREHOUSE_LAYER_IN_USE");
        verify(audit).auditActionStrict(
            eq("MODELING_WAREHOUSE_LAYER_DELETE"), eq(AuditStage.FAIL), eq("FIN_DETAIL"),
            eq(Map.of("referenceCount", 2L, "reason", "IN_USE"))
        );
        verify(repository, never()).softDelete(anyString(), anyInt(), anyString(), any());
    }

    @Test
    void rejectsDeletingBuiltInLayer() {
        assertThatThrownBy(() -> service.delete("alice", "DWD"))
            .isInstanceOf(WarehouseLayerException.class)
            .extracting(ex -> ((WarehouseLayerException) ex).code())
            .isEqualTo("WAREHOUSE_LAYER_BUILTIN_PROTECTED");
        verify(repository, never()).softDelete(anyString(), anyInt(), anyString(), any());
    }

    @Test
    void rejectsDeletingUnknownOrAlreadyDeletedLayer() {
        when(repository.findByCode("MISSING")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete("alice", "MISSING"))
            .isInstanceOf(WarehouseLayerException.class)
            .extracting(ex -> ((WarehouseLayerException) ex).code())
            .isEqualTo("WAREHOUSE_LAYER_NOT_FOUND");

        StoredWarehouseLayer deleted = new StoredWarehouseLayer(
            UUID.randomUUID(), "GONE_LAYER", "已删除", "DWD", WarehouseLayerContract.groupOf("DWD"), null, null, "DELETED", 2, "alice",
            Instant.now(), "alice", Instant.now()
        );
        when(repository.findByCode("GONE_LAYER")).thenReturn(Optional.of(deleted));
        assertThatThrownBy(() -> service.delete("alice", "GONE_LAYER"))
            .isInstanceOf(WarehouseLayerException.class)
            .extracting(ex -> ((WarehouseLayerException) ex).code())
            .isEqualTo("WAREHOUSE_LAYER_NOT_FOUND");
    }

    @Test
    void softDeletesCleanLayerAndEmitsStrictSuccessAudit() {
        StoredWarehouseLayer row = active("FIN_DETAIL", "DWD", null);
        when(repository.findByCode("FIN_DETAIL")).thenReturn(Optional.of(row));
        when(repository.countActiveModelReferences("FIN_DETAIL")).thenReturn(0L);
        when(repository.softDelete(eq("FIN_DETAIL"), eq(1), eq("alice"), any())).thenReturn(1);

        service.delete("alice", "FIN_DETAIL");

        verify(repository).softDelete(eq("FIN_DETAIL"), eq(1), eq("alice"), any());
        verify(audit).auditActionStrict(
            eq("MODELING_WAREHOUSE_LAYER_DELETE"), eq(AuditStage.SUCCESS), eq("FIN_DETAIL"), any()
        );
    }

    @Test
    void rejectsDeleteWhenOptimisticUpdateLosesRace() {
        StoredWarehouseLayer row = active("FIN_DETAIL", "DWD", null);
        when(repository.findByCode("FIN_DETAIL")).thenReturn(Optional.of(row));
        when(repository.countActiveModelReferences("FIN_DETAIL")).thenReturn(0L);
        when(repository.softDelete(eq("FIN_DETAIL"), eq(1), eq("alice"), any())).thenReturn(0);

        assertThatThrownBy(() -> service.delete("alice", "FIN_DETAIL"))
            .isInstanceOf(WarehouseLayerException.class)
            .extracting(ex -> ((WarehouseLayerException) ex).code())
            .isEqualTo("WAREHOUSE_LAYER_DELETE_RACE");
    }

    private static StoredWarehouseLayer active(String code, String systemLayerCode, String prefix) {
        return new StoredWarehouseLayer(
            UUID.randomUUID(), code, "层-" + code, systemLayerCode, WarehouseLayerContract.groupOf(systemLayerCode), "说明", prefix, "ACTIVE", 1, "alice",
            Instant.now(), "alice", Instant.now()
        );
    }
}
