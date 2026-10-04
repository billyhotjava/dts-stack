package com.yuzhi.dts.platform.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitApplicationService;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.ExpectedVersion;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.MeasurementUnitCommand;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.MeasurementUnitStatus;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.MeasurementUnitView;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GovernanceMeasurementUnitResourceTest {

    private static final UUID UNIT_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final String CHECKSUM = "a".repeat(64);
    private static final String ETAG = "\"measurement-unit:" + UNIT_ID + ":1:" + CHECKSUM + "\"";

    private MeasurementUnitApplicationService service;
    private AuditService audit;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = org.mockito.Mockito.mock(MeasurementUnitApplicationService.class);
        audit = org.mockito.Mockito.mock(AuditService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new GovernanceMeasurementUnitResource(service, audit)).build();
    }

    @Test
    void exposesCreateReadHistoryUpdateAndDeactivateWithStrongEtags() throws Exception {
        MeasurementUnitView view = view();
        when(service.create(eq("system"), any(MeasurementUnitCommand.class))).thenReturn(view);
        when(service.get(UNIT_ID)).thenReturn(view);
        when(service.versions(UNIT_ID)).thenReturn(List.of(view));
        when(service.update(eq("system"), eq(UNIT_ID), any(ExpectedVersion.class), any(MeasurementUnitCommand.class)))
            .thenReturn(view);
        when(service.deactivate(eq("system"), eq(UNIT_ID), any(ExpectedVersion.class))).thenReturn(view);

        mockMvc.perform(post("/api/governance/measurement-units").contentType(MediaType.APPLICATION_JSON).content(json()))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/governance/measurement-units/" + UNIT_ID))
            .andExpect(header().string("ETag", ETAG));
        mockMvc.perform(get("/api/governance/measurement-units/{id}", UNIT_ID))
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", ETAG));
        mockMvc.perform(get("/api/governance/measurement-units/{id}/versions", UNIT_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].version").value(1));
        mockMvc.perform(put("/api/governance/measurement-units/{id}", UNIT_ID)
                .header("If-Match", ETAG)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json()))
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", ETAG));
        mockMvc.perform(delete("/api/governance/measurement-units/{id}", UNIT_ID).header("If-Match", ETAG))
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", ETAG));

        verify(audit).auditAction(eq("GOV_MEASUREMENT_UNIT_CREATE"), eq(AuditStage.SUCCESS), eq(UNIT_ID.toString()), any());
        verify(audit).auditAction(eq("GOV_MEASUREMENT_UNIT_UPDATE"), eq(AuditStage.SUCCESS), eq(UNIT_ID.toString()), any());
        verify(audit).auditAction(eq("GOV_MEASUREMENT_UNIT_DEACTIVATE"), eq(AuditStage.SUCCESS), eq(UNIT_ID.toString()), any());
    }

    @Test
    void rejectsMissingOrWeakPutPreconditionsAndDeclaresGovernanceWriteAuthority() throws Exception {
        mockMvc.perform(put("/api/governance/measurement-units/{id}", UNIT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json()))
            .andExpect(status().isPreconditionRequired())
            .andExpect(jsonPath("$.code").value("MEASUREMENT_UNIT_IF_MATCH_REQUIRED"));
        mockMvc.perform(put("/api/governance/measurement-units/{id}", UNIT_ID)
                .header("If-Match", "W/" + ETAG)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MEASUREMENT_UNIT_IF_MATCH_INVALID"));

        for (Method method : List.of(
            GovernanceMeasurementUnitResource.class.getDeclaredMethod("create", MeasurementUnitCommand.class),
            GovernanceMeasurementUnitResource.class.getDeclaredMethod(
                "update",
                UUID.class,
                String.class,
                MeasurementUnitCommand.class
            ),
            GovernanceMeasurementUnitResource.class.getDeclaredMethod("deactivate", UUID.class, String.class)
        )) {
            org.assertj.core.api.Assertions.assertThat(method.getAnnotation(PreAuthorize.class).value())
                .contains("GOVERNANCE_MAINTAINERS");
        }
    }

    private static MeasurementUnitView view() {
        return new MeasurementUnitView(
            UNIT_ID,
            "KG",
            "千克",
            "kg",
            "MASS",
            BigDecimal.ONE,
            null,
            3,
            MeasurementUnitStatus.ACTIVE,
            1,
            CHECKSUM,
            Instant.EPOCH,
            Instant.EPOCH
        );
    }

    private static String json() {
        return """
            {"code":"kg","name":"千克","symbol":"kg","quantityKind":"MASS",
             "conversionFactor":1,"precision":3}
            """;
    }
}
