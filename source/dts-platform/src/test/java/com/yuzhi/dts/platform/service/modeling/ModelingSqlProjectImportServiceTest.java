package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.modeling.ModelingSqlModel;
import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelRequest;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlProjectImportService.SqlModelProjectImportRequest;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlProjectImportService.SqlModelProjectImportResult;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelingSqlProjectImportServiceTest {

    @Mock
    private ModelingSqlModelService sqlModelService;

    @Mock
    private ModelingSqlModelRepository sqlModelRepository;

    @InjectMocks
    private ModelingSqlProjectImportService projectImportService;

    private UUID planId;

    @BeforeEach
    void setUp() {
        planId = UUID.randomUUID();
    }

    @Test
    void importProjectZip_shouldCreateFromManifest_whenModelNotExists() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put(
            "manifest/models.tsv",
            "name\tlayer\tsql_path\n" +
            "dwd_patent_info\tDWD\tmodels/dwd/dwd_patent_info.sql\n"
        );
        entries.put("models/dwd/dwd_patent_info.sql", "select * from {{ source('ods', 'ods_patent_info') }}");
        byte[] zipBytes = buildZip(entries);

        when(sqlModelRepository.findFirstByPlanIdAndNameIgnoreCase(planId, "dwd_patent_info")).thenReturn(Optional.empty());

        SqlModelProjectImportRequest request = new SqlModelProjectImportRequest(
            planId,
            UUID.randomUUID(),
            "skip",
            "table",
            "patent",
            "DRAFT",
            Boolean.TRUE,
            "D1",
            Boolean.FALSE
        );

        SqlModelProjectImportResult result = projectImportService.importProjectZip(request, zipBytes, "patent.zip", "D1");

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(result.skipped()).isZero();
        assertThat(result.dryRun()).isFalse();
        assertThat(result.packageFingerprint()).isNotBlank();

        ArgumentCaptor<SqlModelRequest> captor = ArgumentCaptor.forClass(SqlModelRequest.class);
        verify(sqlModelService).create(captor.capture(), anyString());
        assertThat(captor.getValue().planId()).isEqualTo(planId);
        assertThat(captor.getValue().name()).isEqualTo("dwd_patent_info");
        assertThat(captor.getValue().layer()).isEqualTo("DWD");
    }

    @Test
    void importProjectZip_shouldSkipOnConflict_whenStrategyIsSkip() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("02-dwd/order.sql", "select 1 as id");
        byte[] zipBytes = buildZip(entries);

        ModelingSqlModel existing = new ModelingSqlModel();
        existing.setId(UUID.randomUUID());
        existing.setPlanId(planId);
        existing.setName("dwd_order");
        when(sqlModelRepository.findFirstByPlanIdAndNameIgnoreCase(planId, "dwd_order")).thenReturn(Optional.of(existing));

        SqlModelProjectImportRequest request = new SqlModelProjectImportRequest(
            planId,
            UUID.randomUUID(),
            "skip",
            "table",
            null,
            "DRAFT",
            Boolean.TRUE,
            "D1",
            Boolean.FALSE
        );

        SqlModelProjectImportResult result = projectImportService.importProjectZip(request, zipBytes, "project.zip", "D1");

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.created()).isZero();
        assertThat(result.updated()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        verify(sqlModelService, never()).create(any(SqlModelRequest.class), anyString());
        verify(sqlModelService, never()).update(any(UUID.class), any(SqlModelRequest.class), anyString());
    }



    @Test
    void importProjectZip_shouldNotWrite_whenDryRunEnabled() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("02-dwd/customer.sql", "select 1 as id");
        byte[] zipBytes = buildZip(entries);

        when(sqlModelRepository.findFirstByPlanIdAndNameIgnoreCase(planId, "dwd_customer")).thenReturn(Optional.empty());

        SqlModelProjectImportRequest request = new SqlModelProjectImportRequest(
            planId,
            UUID.randomUUID(),
            "skip",
            "table",
            null,
            "DRAFT",
            Boolean.TRUE,
            "D1",
            Boolean.TRUE
        );

        SqlModelProjectImportResult result = projectImportService.importProjectZip(request, zipBytes, "project.zip", "D1");

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.dryRun()).isTrue();
        verify(sqlModelService, never()).create(any(SqlModelRequest.class), anyString());
        verify(sqlModelService, never()).update(any(UUID.class), any(SqlModelRequest.class), anyString());
    }


    @Test
    void importProjectZip_shouldImportIndicatorManifestAsAdsModel() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put(
            "manifest/indicators.tsv",
            "code\tindicator_name\texpression_sql\n" +
            "patent_total\t专利总量\tselect count(*) as metric_value from {{ ref('dws_patent_base') }}\n"
        );
        byte[] zipBytes = buildZip(entries);

        when(sqlModelRepository.findFirstByPlanIdAndNameIgnoreCase(planId, "ads_patent_total")).thenReturn(Optional.empty());

        SqlModelProjectImportRequest request = new SqlModelProjectImportRequest(
            planId,
            UUID.randomUUID(),
            "skip",
            "table",
            "patent",
            "DRAFT",
            Boolean.TRUE,
            "D1",
            Boolean.FALSE
        );

        SqlModelProjectImportResult result = projectImportService.importProjectZip(request, zipBytes, "indicator.zip", "D1");

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.failed()).isZero();

        ArgumentCaptor<SqlModelRequest> captor = ArgumentCaptor.forClass(SqlModelRequest.class);
        verify(sqlModelService).create(captor.capture(), anyString());
        assertThat(captor.getValue().name()).isEqualTo("ads_patent_total");
        assertThat(captor.getValue().layer()).isEqualTo("ADS");
        assertThat(captor.getValue().sql()).contains("metric_value");
    }
    private byte[] buildZip(Map<String, String> entries) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, String> entry : entries.entrySet()) {
                    zos.putNextEntry(new ZipEntry(entry.getKey()));
                    byte[] content = entry.getValue().getBytes(StandardCharsets.UTF_8);
                    zos.write(content);
                    zos.closeEntry();
                }
            }
            return bos.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
