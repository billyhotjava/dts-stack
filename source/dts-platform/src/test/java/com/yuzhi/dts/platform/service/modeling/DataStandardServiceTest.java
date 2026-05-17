package com.yuzhi.dts.platform.service.modeling;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yuzhi.dts.platform.domain.modeling.DataSecurityLevel;
import com.yuzhi.dts.platform.domain.modeling.DataStandard;
import com.yuzhi.dts.platform.domain.modeling.DataStandardStatus;
import com.yuzhi.dts.platform.domain.modeling.DataStandardVersion;
import com.yuzhi.dts.platform.repository.modeling.DataStandardRepository;
import com.yuzhi.dts.platform.repository.modeling.DataStandardVersionRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetIdentity;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.catalog.CodeAssetGrantWriter;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataStandardServiceTest {

    @Mock
    private DataStandardRepository repository;

    @Mock
    private DataStandardVersionRepository versionRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private DataStandardSecurity security;

    @Mock
    private CodeAssetGrantWriter codeAssetGrantWriter;

    @Test
    void createSyncsDataStandardAsCodeAssetForActiveDept() {
        DataStandardService service = new DataStandardService(
            repository,
            versionRepository,
            new ObjectMapper().registerModule(new JavaTimeModule()),
            auditService,
            security,
            codeAssetGrantWriter
        );
        when(security.enforceUpsertDomain("finance", "D01")).thenReturn("finance");
        when(security.resolveActiveDept("D01")).thenReturn("D01");
        when(repository.save(any(DataStandard.class))).thenAnswer(invocation -> {
            DataStandard standard = invocation.getArgument(0);
            standard.setId(UUID.fromString("11111111-2222-3333-4444-555555555555"));
            return standard;
        });
        when(versionRepository.findByStandardAndVersion(any(DataStandard.class), eq("v1"))).thenReturn(Optional.empty());
        when(versionRepository.save(any(DataStandardVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DataStandardUpsertRequest request = new DataStandardUpsertRequest();
        request.setCode("contract_amount");
        request.setName("合同金额");
        request.setDomain("finance");
        request.setStatus(DataStandardStatus.ACTIVE);
        request.setSecurityLevel(DataSecurityLevel.INTERNAL);

        service.create(request, "D01");

        ArgumentCaptor<CatalogAssetIdentity> identityCaptor = ArgumentCaptor.forClass(CatalogAssetIdentity.class);
        verify(codeAssetGrantWriter).upsertCodeAsset(identityCaptor.capture(), eq("D01"), eq("dts-platform"), eq("INTERNAL"), eq("ACTIVE"));
        CatalogAssetIdentity identity = identityCaptor.getValue();
        Assertions.assertThat(identity.type()).isEqualTo(CatalogAssetType.DATA_STANDARD);
        Assertions.assertThat(identity.assetId()).isEqualTo("11111111-2222-3333-4444-555555555555");
    }
}
