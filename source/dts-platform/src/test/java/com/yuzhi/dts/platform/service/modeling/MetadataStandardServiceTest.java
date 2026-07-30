package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.modeling.MetadataStandard;
import com.yuzhi.dts.platform.repository.modeling.MetadataStandardRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    @Test
    void resolvesOnlyTheBoundedReferencedStandardElements() {
        UUID firstId = UUID.fromString("70000000-0000-0000-0000-000000000001");
        UUID secondId = UUID.fromString("70000000-0000-0000-0000-000000000002");
        MetadataStandard first = standard(firstId, "z_customer_id");
        MetadataStandard second = standard(secondId, "a_customer_name");
        MetadataStandardRepository repository = mock(MetadataStandardRepository.class);
        when(repository.findAllById(List.of(firstId))).thenReturn(List.of(first));

        var result = new MetadataStandardService(repository, mock(ModelingAssetReferenceService.class))
            .listForRelationshipGraph(Set.of(secondId, firstId), 1);

        assertThat(result).singleElement().satisfies(value -> assertThat(value.getId()).isEqualTo(firstId));
        verify(repository).findAllById(List.of(firstId));
    }

    private static MetadataStandard standard(UUID id, String name) {
        MetadataStandard value = new MetadataStandard();
        value.setId(id);
        value.setFieldNameEn(name);
        value.setFieldNameCn(name);
        value.setDataType("VARCHAR");
        value.setDomain("customer");
        value.setDescription(name);
        value.setVersion(1);
        return value;
    }
}
