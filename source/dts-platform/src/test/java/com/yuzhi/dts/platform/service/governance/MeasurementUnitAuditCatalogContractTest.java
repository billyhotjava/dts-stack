package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class MeasurementUnitAuditCatalogContractTest {

    @Test
    void registersTheOwnerActionsInCanonicalAndFallbackCatalogsWithoutDroppingExistingActions() throws Exception {
        List<Path> catalogs = List.of(
            Path.of("../dts-common/src/main/resources/config/audit-action-catalog.json"),
            Path.of("src/main/docker/dts-common-fallback/src/main/resources/config/audit-action-catalog.json")
        );
        for (Path catalog : catalogs) {
            String json = Files.readString(catalog);
            new ObjectMapper().readTree(json);
            assertThat(json)
                .contains("\"key\": \"governance.measurementUnits\"")
                .contains("\"code\": \"GOV_MEASUREMENT_UNIT_LIST\"")
                .contains("\"code\": \"GOV_MEASUREMENT_UNIT_VIEW\"")
                .contains("\"code\": \"GOV_MEASUREMENT_UNIT_VERSION_VIEW\"")
                .contains("\"code\": \"GOV_MEASUREMENT_UNIT_REFERENCE_VIEW\"")
                .contains("\"code\": \"GOV_MEASUREMENT_UNIT_CREATE\"")
                .contains("\"code\": \"GOV_MEASUREMENT_UNIT_UPDATE\"")
                .contains("\"code\": \"GOV_MEASUREMENT_UNIT_DEACTIVATE\"")
                .contains("\"code\": \"MODELING_WAREHOUSE_SOURCE_INVENTORY_SAVE\"");
        }
    }
}
