package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.MeasurementUnitCommand;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MeasurementUnitContractTest {

    @Test
    void acceptsAReusableUnitDefinitionAndRejectsUnsafeNumericSemantics() {
        MeasurementUnitCommand valid = new MeasurementUnitCommand(
            "kg",
            "千克",
            "kg",
            "MASS",
            new BigDecimal("1"),
            null,
            3
        );
        MeasurementUnitCommand invalid = new MeasurementUnitCommand(
            "m",
            "米",
            "m",
            "LENGTH",
            BigDecimal.ZERO,
            null,
            19
        );

        assertThat(MeasurementUnitContract.validate(valid)).isEmpty();
        assertThat(MeasurementUnitContract.validate(invalid))
            .extracting(MeasurementUnitContract.FieldIssue::code)
            .containsExactlyInAnyOrder(
                "MEASUREMENT_UNIT_CONVERSION_FACTOR_INVALID",
                "MEASUREMENT_UNIT_PRECISION_INVALID"
            );
    }
}
