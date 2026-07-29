package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.domain.modeling.MetadataStandard;
import jakarta.persistence.Column;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;

class MetadataStandardSourceSystemPersistenceTest {

    @Test
    void packageDataElementMayLeaveCustomerOwnedSourceSystemUnset() throws Exception {
        Column mapping = MetadataStandard.class.getDeclaredField("sourceSystem").getAnnotation(Column.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.nullable()).isTrue();
    }

    @Test
    void interactiveMaintenanceStillRequiresCustomerSourceSystem() throws Exception {
        NotBlank validation = MetadataStandardUpsertRequest.class
            .getDeclaredField("sourceSystem")
            .getAnnotation(NotBlank.class);

        assertThat(validation).isNotNull();
    }
}
