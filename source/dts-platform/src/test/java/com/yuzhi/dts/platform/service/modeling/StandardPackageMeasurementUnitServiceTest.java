package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.governance.MeasurementUnitApplicationService;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.ExpectedVersion;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.MeasurementUnitCommand;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.MeasurementUnitStatus;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.MeasurementUnitView;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StandardPackageMeasurementUnitServiceTest {

    @Mock
    private MeasurementUnitApplicationService units;

    private StandardPackageMeasurementUnitService service;

    @BeforeEach
    void setUp() {
        service = new StandardPackageMeasurementUnitService(units);
    }

    @Test
    void preview_validUnits_resolvesStableBaseCodes() {
        when(units.list()).thenReturn(List.of());
        StandardPackageMeasurementUnitService.Preview preview = service.preview(
            csv(
                """
                code,name,symbol,quantity_kind,conversion_factor,base_unit_code,precision,status
                M,米,m,LENGTH,1,,3,ACTIVE
                CM,厘米,cm,LENGTH,0.01,M,3,ACTIVE
                """
            )
        );

        assertThat(preview.report())
            .containsEntry("present", true)
            .containsEntry("total", 2)
            .containsEntry("toCreate", 2)
            .containsEntry("errorCount", 0);
        assertThat(preview.payload()).extracting(row -> row.get("code")).containsExactly("M", "CM");
        assertThat(preview.payload().get(1)).containsEntry("baseUnitCode", "M");
    }

    @Test
    void preview_invalidGraph_reportsMissingBaseDimensionAndCycle() {
        when(units.list()).thenReturn(List.of());
        StandardPackageMeasurementUnitService.Preview preview = service.preview(
            csv(
                """
                code,name,symbol,quantity_kind,conversion_factor,base_unit_code,precision,status
                M,米,m,LENGTH,1,CM,3,ACTIVE
                CM,厘米,cm,LENGTH,0.01,M,3,ACTIVE
                KG,千克,kg,MASS,1,M,3,ACTIVE
                S,秒,s,TIME,1,MISSING,3,ACTIVE
                """
            )
        );

        assertThat(preview.report().get("errorCount")).isEqualTo(3);
        assertThat(String.valueOf(preview.report().get("errors")))
            .contains("循环引用")
            .contains("量纲不一致")
            .contains("无法解析");
    }

    @Test
    void apply_createsBaseBeforeDependentAndReturnsRollbackEvidence() {
        UUID meterId = UUID.fromString("60000000-0000-0000-0000-000000000001");
        UUID centimeterId = UUID.fromString("60000000-0000-0000-0000-000000000002");
        List<String> order = new ArrayList<>();
        when(units.list()).thenReturn(List.of());
        when(units.create(eq("tester"), any(MeasurementUnitCommand.class)))
            .thenAnswer(invocation -> {
                MeasurementUnitCommand command = invocation.getArgument(1);
                order.add(command.code());
                UUID id = "M".equals(command.code()) ? meterId : centimeterId;
                return view(id, command, 1, "checksum-" + command.code());
            });
        List<Map<String, Object>> payload = List.of(
            row("CM", "厘米", "cm", "LENGTH", "0.01", "M"),
            row("M", "米", "m", "LENGTH", "1", null)
        );

        List<StandardPackageMeasurementUnitService.Change> changes = service.apply(payload, "tester");

        assertThat(order).containsExactly("M", "CM");
        assertThat(changes).extracting(StandardPackageMeasurementUnitService.Change::entityId)
            .containsExactly(meterId.toString(), centimeterId.toString());
        assertThat(changes).allSatisfy(change -> {
            assertThat(change.action()).isEqualTo("CREATE");
            assertThat(change.rollbackImage()).containsKeys("_appliedVersion", "_appliedChecksum");
        });
    }

    @Test
    void rollbackUpdate_restoresPriorCommandThroughVersionedImportPath() {
        UUID unitId = UUID.fromString("60000000-0000-0000-0000-000000000003");
        Map<String, Object> image = row("CNY", "人民币", "¥", "CURRENCY", "1", null);
        image.put("baseUnitRef", null);
        image.put("status", "ACTIVE");
        image.put("_appliedVersion", 4);
        image.put("_appliedChecksum", "after-checksum");

        boolean handled = service.rollbackUpdate(unitId, image, "tester");

        assertThat(handled).isTrue();
        ArgumentCaptor<MeasurementUnitCommand> command = ArgumentCaptor.forClass(MeasurementUnitCommand.class);
        verify(units).replaceForImport(
            eq("tester"),
            eq(unitId),
            eq(new ExpectedVersion(unitId, 4, "after-checksum")),
            command.capture(),
            eq(MeasurementUnitStatus.ACTIVE)
        );
        assertThat(command.getValue().code()).isEqualTo("CNY");
        assertThat(command.getValue().conversionFactor()).isEqualByComparingTo(BigDecimal.ONE);
    }

    private static byte[] csv(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, Object> row(
        String code,
        String name,
        String symbol,
        String quantityKind,
        String factor,
        String baseCode
    ) {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("code", code);
        row.put("name", name);
        row.put("symbol", symbol);
        row.put("quantityKind", quantityKind);
        row.put("conversionFactor", factor);
        row.put("baseUnitCode", baseCode);
        row.put("precision", 2);
        row.put("status", "ACTIVE");
        return row;
    }

    private static MeasurementUnitView view(UUID id, MeasurementUnitCommand command, int version, String checksum) {
        return new MeasurementUnitView(
            id,
            command.code(),
            command.name(),
            command.symbol(),
            command.quantityKind(),
            command.conversionFactor(),
            command.baseUnitRef(),
            command.precision(),
            MeasurementUnitStatus.ACTIVE,
            version,
            checksum,
            Instant.parse("2026-07-29T00:00:00Z"),
            Instant.parse("2026-07-29T00:00:00Z")
        );
    }
}
