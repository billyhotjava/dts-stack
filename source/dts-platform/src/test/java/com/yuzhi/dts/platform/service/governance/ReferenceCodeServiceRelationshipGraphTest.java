package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.governance.StdCodeDirectory;
import com.yuzhi.dts.platform.repository.governance.GovReferenceImportRunRepository;
import com.yuzhi.dts.platform.repository.governance.StdCodeDirectoryRepository;
import com.yuzhi.dts.platform.repository.governance.StdCodeMappingRepository;
import com.yuzhi.dts.platform.repository.governance.StdCodeValueRepository;
import com.yuzhi.dts.platform.service.modeling.ModelingAssetReferenceService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReferenceCodeServiceRelationshipGraphTest {

    @Test
    void loadsAuthorizedReferenceCodeOwnersInOneBoundedBatchWithoutItemCounts() {
        StdCodeDirectoryRepository directories = mock(StdCodeDirectoryRepository.class);
        StdCodeValueRepository values = mock(StdCodeValueRepository.class);
        ReferenceCodeSecurity security = mock(ReferenceCodeSecurity.class);
        ReferenceCodeService service = new ReferenceCodeService(
            directories,
            values,
            mock(StdCodeMappingRepository.class),
            mock(GovReferenceImportRunRepository.class),
            security,
            new ObjectMapper(),
            mock(ModelingAssetReferenceService.class)
        );
        StdCodeDirectory visible = directory("CUSTOMER_TYPE", "Customer type", "dept-a", "v3");
        StdCodeDirectory hidden = directory("SECRET_TYPE", "Secret type", "dept-b", "v1");
        when(directories.findAllById(List.of("CUSTOMER_TYPE", "SECRET_TYPE")))
            .thenReturn(List.of(hidden, visible));
        when(security.canAccessDept("dept-a", "dept-a")).thenReturn(true);
        when(security.canAccessDept("dept-b", "dept-a")).thenReturn(false);

        assertThat(service.listForRelationshipGraph(Set.of("SECRET_TYPE", "CUSTOMER_TYPE"), "dept-a", 500))
            .containsExactly(
                new ReferenceCodeService.RelationshipGraphReferenceCode(
                    "CUSTOMER_TYPE",
                    "CUSTOMER_TYPE",
                    "Customer type",
                    1,
                    "v3"
                )
            );

        verify(directories).findAllById(List.of("CUSTOMER_TYPE", "SECRET_TYPE"));
        verify(values, org.mockito.Mockito.never()).countByCodeTypeId(org.mockito.ArgumentMatchers.anyString());
    }

    private static StdCodeDirectory directory(String code, String name, String ownerDept, String version) {
        StdCodeDirectory value = new StdCodeDirectory();
        value.setCodeTypeId(code);
        value.setCodeTypeCode(code);
        value.setCodeTypeName(name);
        value.setOwnerDept(ownerDept);
        value.setStatus(1);
        value.setVersion(version);
        return value;
    }
}
