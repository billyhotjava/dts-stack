package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.modeling.ModelingWordRoot;
import com.yuzhi.dts.platform.repository.modeling.ModelingWordRootRepository;
import com.yuzhi.dts.platform.service.modeling.WordRootContract.UpsertRequest;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WordRootServiceTest {

    @Test
    void createsAnActiveDepartmentOwnedWordRoot() {
        ModelingWordRootRepository repository = mock(ModelingWordRootRepository.class);
        DataStandardSecurity security = mock(DataStandardSecurity.class);
        OrganizationVisibilityService organizationVisibility = mock(OrganizationVisibilityService.class);
        when(security.resolveActiveDept("D01")).thenReturn("D01");
        when(repository.save(any(ModelingWordRoot.class))).thenAnswer(invocation -> {
            ModelingWordRoot root = invocation.getArgument(0);
            root.setId(UUID.fromString("11111111-2222-3333-4444-555555555555"));
            return root;
        });
        WordRootService service = new WordRootService(repository, security, organizationVisibility);

        var created = service.create(new UpsertRequest(" amount ", " 金额 ", " Amount ", " amt ", " 财务 ", " v1 "), "D01");

        assertThat(created.code()).isEqualTo("AMOUNT");
        assertThat(created.abbreviation()).isEqualTo("AMT");
        assertThat(created.status()).isEqualTo("ACTIVE");
        assertThat(created.ownerDept()).isEqualTo("D01");
    }

    @Test
    void keepsTheStableCodeWhenUpdating() {
        ModelingWordRootRepository repository = mock(ModelingWordRootRepository.class);
        DataStandardSecurity security = mock(DataStandardSecurity.class);
        OrganizationVisibilityService organizationVisibility = mock(OrganizationVisibilityService.class);
        UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");
        ModelingWordRoot existing = new ModelingWordRoot();
        existing.setId(id);
        existing.setCode("AMOUNT");
        existing.setNameCn("金额");
        existing.setNameEn("Amount");
        existing.setAbbreviation("AMT");
        existing.setOwnerDept("D01");
        existing.setStatus("ACTIVE");
        existing.setVersion("v1");
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(any(ModelingWordRoot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(security.resolveActiveDept("D01")).thenReturn("D01");
        WordRootService service = new WordRootService(repository, security, organizationVisibility);

        service.update(id, new UpsertRequest("OTHER", "业务金额", "Business amount", "BAMT", "财务", "v2"), "D01");

        ArgumentCaptor<ModelingWordRoot> captor = ArgumentCaptor.forClass(ModelingWordRoot.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("AMOUNT");
        assertThat(captor.getValue().getNameCn()).isEqualTo("业务金额");
        assertThat(captor.getValue().getVersion()).isEqualTo("v2");
    }
}
