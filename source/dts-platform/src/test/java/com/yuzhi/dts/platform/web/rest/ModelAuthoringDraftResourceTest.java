package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.AuthoringDraftView;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.AuthoringProjection;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.CreateAuthoringDraftRequest;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.DraftIntent;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.SaveAuthoringDraftRequest;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.SaveAuthoringDraftView;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.ActiveView;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringDraftService;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.FileInput;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.DraftState;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.DraftView;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftRejectionAudit;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ModelAuthoringDraftResourceTest {

    private static final UUID MODEL_ID = UUID.fromString("20000000-0000-0000-0000-000000000092");
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000092");
    private static final UUID DRAFT_ID = UUID.fromString("30000000-0000-0000-0000-000000000092");

    @Mock
    private ModelAuthoringDraftService service;

    @Mock
    private WarehousePlanActorProvider actorProvider;

    @Mock
    private DbtImplementationDraftRejectionAudit rejectionAudit;

    @Test
    void createsOneSourceNeutralDraftAndReturnsItsStrongEtag() {
        CreateAuthoringDraftRequest request = new CreateAuthoringDraftRequest(
            DraftIntent.EDIT_DRAFT,
            3,
            "a".repeat(64),
            null,
            null,
            "dim_orders",
            "authoring-create-92"
        );
        DraftView draft = new DraftView(
            DRAFT_ID,
            PLAN_ID,
            MODEL_ID,
            3,
            "a".repeat(64),
            null,
            null,
            DraftState.DRAFT,
            "authoring-etag-92",
            Instant.parse("2026-08-19T00:00:00Z"),
            null
        );
        AuthoringDraftView created = new AuthoringDraftView(null, draft, null, null, List.of());
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("xiezm", "institute"));
        when(service.create("tenant-92", "xiezm", MODEL_ID, request)).thenReturn(created);

        var response = resource().create(MODEL_ID, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"authoring-etag-92\"");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isSameAs(created);
        verify(service).create("tenant-92", "xiezm", MODEL_ID, request);
    }

    @Test
    void returnsTheCanonicalPersistedFilesAndStrongEtagAfterSavingEitherView() {
        var snapshot = new ObjectMapper().createObjectNode().put("schemaVersion", 1);
        List<FileInput> files = List.of(new FileInput("models/dim_orders.sql", "select 1 as order_id"));
        SaveAuthoringDraftRequest request = new SaveAuthoringDraftRequest(
            "authoring-etag-92",
            snapshot,
            files,
            ActiveView.CODE
        );
        SaveAuthoringDraftView saved = new SaveAuthoringDraftView(
            DRAFT_ID,
            "authoring-etag-93",
            snapshot,
            AuthoringProjection.unknown("TEST"),
            1,
            20,
            files
        );
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("xiezm", "institute"));
        when(service.save("tenant-92", "xiezm", MODEL_ID, DRAFT_ID, request)).thenReturn(saved);

        var response = resource().save(MODEL_ID, DRAFT_ID, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"authoring-etag-93\"");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().files()).containsExactlyElementsOf(files);
        verify(service).save("tenant-92", "xiezm", MODEL_ID, DRAFT_ID, request);
    }

    private ModelAuthoringDraftResource resource() {
        return new ModelAuthoringDraftResource(service, actorProvider, rejectionAudit, "tenant-92");
    }
}
