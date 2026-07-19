package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.modeling.MetadataStandard;
import com.yuzhi.dts.platform.repository.modeling.MetadataStandardRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MetadataStandardServiceTest {

    @Test
    void incrementsTheOwnerVersionWhenAnExistingDataElementChanges() {
        UUID id = UUID.fromString("70000000-0000-0000-0000-000000000001");
        MetadataStandardRepository repository = mock(MetadataStandardRepository.class);
        MetadataStandard entity = new MetadataStandard();
        entity.setId(id);
        entity.setVersion(3);
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(repository.save(any(MetadataStandard.class))).thenAnswer(invocation -> invocation.getArgument(0));
        MetadataStandardUpsertRequest request = mock(MetadataStandardUpsertRequest.class);

        var result = new MetadataStandardService(repository, mock(ModelingAssetReferenceService.class)).update(id, request);

        assertThat(result.getVersion()).isEqualTo(4);
        assertThat(entity.getVersion()).isEqualTo(4);
    }
}
