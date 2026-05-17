package com.yuzhi.dts.platform.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.modeling.DataStandard;
import com.yuzhi.dts.platform.domain.modeling.DataStandardStatus;
import com.yuzhi.dts.platform.repository.modeling.DataStandardRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DataStandardInternalResourceTest {

    @Test
    void resolvesActiveDataStandardsAndReportsMissingOrInactiveRefs() {
        DataStandardRepository repository = mock(DataStandardRepository.class);
        DataStandard active = standard("contract_amount", "合同金额", DataStandardStatus.ACTIVE);
        DataStandard draft = standard("draft_amount", "草稿金额", DataStandardStatus.DRAFT);
        when(repository.findByCodeLowerIn(anyCollection())).thenReturn(List.of(active, draft));

        DataStandardInternalResource resource = new DataStandardInternalResource(repository);
        DataStandardInternalResource.ResolveResponse response = resource
            .resolve(new DataStandardInternalResource.ResolveRequest(List.of(
                "data_standard:contract_amount",
                "data_standard:draft_amount",
                "data_standard:missing"
            )))
            .getBody();

        assertThat(response).isNotNull();
        assertThat(response.standards()).extracting(DataStandardInternalResource.StandardContract::ref)
            .containsExactly("data_standard:contract_amount", "data_standard:draft_amount");
        assertThat(response.standards()).extracting(DataStandardInternalResource.StandardContract::active)
            .containsExactly(true, false);
        assertThat(response.missing()).containsExactly("data_standard:missing");
        assertThat(response.inactive()).containsExactly("data_standard:draft_amount");

        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(repository).findByCodeLowerIn(captor.capture());
        assertThat(captor.getValue()).contains("data_standard:contract_amount", "contract_amount", "draft_amount", "missing");
    }

    @Test
    void reportsAmbiguousStandardAliasRefs() {
        DataStandardRepository repository = mock(DataStandardRepository.class);
        DataStandard qualified = standard("data_standard.contract_amount", "合同金额", DataStandardStatus.ACTIVE);
        DataStandard shortCode = standard("contract_amount", "合同额", DataStandardStatus.ACTIVE);
        when(repository.findByCodeLowerIn(anyCollection())).thenReturn(List.of(qualified, shortCode));

        DataStandardInternalResource resource = new DataStandardInternalResource(repository);
        DataStandardInternalResource.ResolveResponse response = resource
            .resolve(new DataStandardInternalResource.ResolveRequest(List.of("data_standard:contract_amount")))
            .getBody();

        assertThat(response).isNotNull();
        assertThat(response.standards()).isEmpty();
        assertThat(response.missing()).isEmpty();
        assertThat(response.ambiguous()).containsExactly("data_standard:contract_amount");
    }

    private static DataStandard standard(String code, String name, DataStandardStatus status) {
        DataStandard standard = new DataStandard();
        standard.setId(UUID.randomUUID());
        standard.setCode(code);
        standard.setName(name);
        standard.setDomain("flower_rental");
        standard.setDataType("DECIMAL");
        standard.setNullable(false);
        standard.setStatus(status);
        return standard;
    }
}
