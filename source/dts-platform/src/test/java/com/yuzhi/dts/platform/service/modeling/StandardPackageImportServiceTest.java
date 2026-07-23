package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.modeling.StandardPackageImportRun;
import com.yuzhi.dts.platform.repository.governance.StdCodeDirectoryRepository;
import com.yuzhi.dts.platform.repository.governance.StdCodeValueRepository;
import com.yuzhi.dts.platform.repository.modeling.MetadataStandardRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingGlossaryTermRepository;
import com.yuzhi.dts.platform.repository.modeling.StandardPackageImportRunRepository;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class StandardPackageImportServiceTest {

    @Mock
    private MetadataStandardRepository metadataStandardRepository;

    @Mock
    private ModelingGlossaryTermRepository glossaryTermRepository;

    @Mock
    private StdCodeDirectoryRepository codeDirectoryRepository;

    @Mock
    private StdCodeValueRepository codeValueRepository;

    @Mock
    private StandardPackageImportRunRepository runRepository;

    private StandardPackageImportService service;

    @BeforeEach
    void setUp() {
        service = new StandardPackageImportService(
            metadataStandardRepository,
            glossaryTermRepository,
            codeDirectoryRepository,
            codeValueRepository,
            runRepository,
            new ObjectMapper(),
            new StandardPackageManifestContract(new ObjectMapper())
        );
        lenient().when(glossaryTermRepository.findByCodeLowerIn(anyCollection())).thenReturn(List.of());
        lenient().when(codeDirectoryRepository.findByCodeTypeCodeIgnoreCase(anyString())).thenReturn(Optional.empty());
        lenient()
            .when(metadataStandardRepository.findByFieldNameEnIgnoreCaseAndDomainIgnoreCase(anyString(), anyString()))
            .thenReturn(Optional.empty());
        lenient()
            .when(runRepository.save(any(StandardPackageImportRun.class)))
            .thenAnswer(invocation -> {
                StandardPackageImportRun run = invocation.getArgument(0);
                run.setId(UUID.randomUUID());
                return run;
            });
        lenient().when(runRepository.findByStatusOrderByCreatedDateDesc("APPLIED")).thenReturn(List.of());
    }

    @Test
    @DisplayName("合法包：五文件解析、跨文件 code_set 解析、run 记录 PREVIEWED")
    void preview_validPackage_reportsCreatesAndPersistsRun() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put(
            "01-business-terms.csv",
            "term_code,term_name,aliases,definition,domain,owner_dept,owner,tags,version,status,version_notes\n" +
            "TERM_GENDER,性别,性别术语,人的性别,人口,DEPT_A,alice,基础,1.0,PUBLISHED,首版\n"
        );
        files.put(
            "02-data-elements.csv",
            "field_name_cn,field_name_en,data_type,data_length,data_precision,data_scale,nullable,domain,description,source_system,code_set,default_value,is_pk,security_level\n" +
            "性别代码,gender_code,VARCHAR,1,,,N,人口,人的性别代码,MDM,GENDER,,N,INTERNAL\n"
        );
        files.put(
            "03-reference-code-directories.csv",
            "code_type_id,code_type_code,code_type_name,std_level,biz_catalog,data_type,status,owner_dept,version\n" +
            ",GENDER,性别代码,NATIONAL,人口,VARCHAR,1,DEPT_A,GB/T 2261.1\n"
        );
        files.put(
            "04-reference-code-items.csv",
            "code_type_code,code_value,code_name,description,sort_num,parent_code,is_default\n" +
            "GENDER,1,男,,1,,N\nGENDER,2,女,,2,,N\n"
        );
        files.put(
            "05-reference-code-mappings.csv",
            "code_type_code,source_system,source_code,standard_code\nGENDER,LEGACY,M,1\n"
        );

        Map<String, Object> preview = service.previewZip(zipOf(files), "tester");

        assertThat(preview.get("blocking")).isEqualTo(false);
        assertThat(preview.get("totalErrors")).isEqualTo(0);
        assertThat(preview.get("runId")).isNotNull();

        List<Map<String, Object>> reports = castReports(preview);
        assertThat(reports).hasSize(5);
        assertThat(reportFor(reports, "04-reference-code-items.csv").get("toCreate")).isEqualTo(2);
        assertThat(reportFor(reports, "02-data-elements.csv").get("toCreate")).isEqualTo(1);

        ArgumentCaptor<StandardPackageImportRun> captor = ArgumentCaptor.forClass(StandardPackageImportRun.class);
        verify(runRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PREVIEWED");
        assertThat(captor.getValue().getPayloadJson()).contains("gender_code");
    }

    @Test
    @DisplayName("code_set 悬空：数据元引用的码表在本包与库中均不存在时报错")
    void preview_danglingCodeSet_reportsError() throws Exception {
        Map<String, String> files = Map.of(
            "02-data-elements.csv",
            "field_name_cn,field_name_en,data_type,data_length,data_precision,data_scale,nullable,domain,description,source_system,code_set,default_value,is_pk,security_level\n" +
            "性别代码,gender_code,VARCHAR,1,,,N,人口,人的性别代码,MDM,MISSING_SET,,N,INTERNAL\n"
        );

        Map<String, Object> preview = service.previewZip(zipOf(files), "tester");

        assertThat(preview.get("blocking")).isEqualTo(true);
        Map<String, Object> report = reportFor(castReports(preview), "02-data-elements.csv");
        assertThat(report.get("errorCount")).isEqualTo(1);
        assertThat(String.valueOf(report.get("errors"))).contains("MISSING_SET");
    }

    @Test
    @DisplayName("码值目录悬空 + 文件内重复：逐行报错且行号正确")
    void preview_itemErrors_reportRowNumbers() throws Exception {
        Map<String, String> files = Map.of(
            "04-reference-code-items.csv",
            "code_type_code,code_value,code_name,description,sort_num,parent_code,is_default\n" +
            "NOT_EXIST,1,男,,1,,N\n"
        );

        Map<String, Object> preview = service.previewZip(zipOf(files), "tester");

        Map<String, Object> report = reportFor(castReports(preview), "04-reference-code-items.csv");
        assertThat(report.get("errorCount")).isEqualTo(1);
        assertThat(String.valueOf(report.get("errors"))).contains("NOT_EXIST").contains("row=2");
    }

    @Test
    @DisplayName("术语 term_code 文件内重复报错")
    void preview_duplicateTermCode_reportsError() throws Exception {
        Map<String, String> files = Map.of(
            "01-business-terms.csv",
            "term_code,term_name\nT1,术语一\nT1,术语一重复\n"
        );

        Map<String, Object> preview = service.previewZip(zipOf(files), "tester");

        Map<String, Object> report = reportFor(castReports(preview), "01-business-terms.csv");
        assertThat(report.get("errorCount")).isEqualTo(1);
        assertThat(String.valueOf(report.get("errors"))).contains("重复");
    }

    @Test
    @DisplayName("压缩包中无标准包 CSV 时拒绝")
    void preview_zipWithoutKnownCsv_throws() throws Exception {
        Map<String, String> files = Map.of("readme.txt", "hello");

        MockMultipartFile zip = zipOf(files);
        assertThatThrownBy(() -> service.previewZip(zip, "tester"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("未找到标准包 CSV");
    }

    @Test
    void preview_v2Package_returnsManifestSummaryAndPersistsIt() {
        byte[] terms = "term_code,term_name\nBT_PERSON,人员\n".getBytes(StandardCharsets.UTF_8);
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("manifest.json", manifestJson("dts-core-person", "1.0.0", "01-business-terms.csv", sha256(terms)));
        files.put("01-business-terms.csv", terms);

        Map<String, Object> preview = service.preview(files, "person.zip", "UPLOAD", "tester");

        assertThat(preview)
            .containsEntry("packageCode", "dts-core-person")
            .containsEntry("packageVersion", "1.0.0")
            .containsEntry("compatibilityMode", "V2")
            .containsKey("contentChecksum");
        verify(runRepository).save(
            org.mockito.ArgumentMatchers.argThat(run ->
                run.getPayloadJson().contains("\"packageCode\":\"dts-core-person\"") &&
                run.getPayloadJson().contains("\"compatibilityMode\":\"V2\"")
            )
        );
    }

    @Test
    void preview_legacyPackage_remainsSupportedAndIsExplicit() {
        Map<String, byte[]> files = Map.of(
            "01-business-terms.csv",
            "term_code,term_name\nBT_PERSON,人员\n".getBytes(StandardCharsets.UTF_8)
        );

        Map<String, Object> preview = service.preview(files, "legacy-upload.zip", "UPLOAD", "tester");

        assertThat(preview)
            .containsEntry("packageCode", "legacy-upload")
            .containsEntry("packageVersion", "1.0.0")
            .containsEntry("compatibilityMode", "LEGACY_V1");
    }

    @Test
    void preview_v2Package_rejectsChecksumMismatchBeforeRunPersistence() {
        byte[] terms = "term_code,term_name\nBT_PERSON,人员\n".getBytes(StandardCharsets.UTF_8);
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("manifest.json", manifestJson("dts-core-person", "1.0.0", "01-business-terms.csv", sha256("different")));
        files.put("01-business-terms.csv", terms);

        assertThatThrownBy(() -> service.preview(files, "person.zip", "UPLOAD", "tester"))
            .isInstanceOfSatisfying(
                StandardPackageContractException.class,
                error -> assertThat(error.code()).isEqualTo("MANIFEST_CHECKSUM_MISMATCH")
            );
        verify(runRepository, never()).save(any());
    }

    @Test
    void previewZip_v2Package_rejectsUndeclaredFile() throws Exception {
        byte[] terms = "term_code,term_name\nBT_PERSON,人员\n".getBytes(StandardCharsets.UTF_8);
        Map<String, String> files = new LinkedHashMap<>();
        files.put(
            "manifest.json",
            new String(manifestJson("dts-core-person", "1.0.0", "01-business-terms.csv", sha256(terms)), StandardCharsets.UTF_8)
        );
        files.put("01-business-terms.csv", new String(terms, StandardCharsets.UTF_8));
        files.put("unexpected.txt", "undeclared");

        assertThatThrownBy(() -> service.previewZip(zipOf(files), "tester"))
            .isInstanceOfSatisfying(
                StandardPackageContractException.class,
                error -> assertThat(error.code()).isEqualTo("MANIFEST_FILE_UNDECLARED")
            );
        verify(runRepository, never()).save(any());
    }

    @Test
    void preview_v2Package_rejectsMissingInstalledDependencyBeforeRunPersistence() {
        byte[] terms = "term_code,term_name\nBT_PERSON,人员\n".getBytes(StandardCharsets.UTF_8);
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(
            "manifest.json",
            manifestJson(
                "dts-core-person",
                "1.0.0",
                "01-business-terms.csv",
                sha256(terms),
                """
                [{"packageCode":"gbt-2261-gender","minimumVersion":"1.0.0"}]
                """
            )
        );
        files.put("01-business-terms.csv", terms);

        assertThatThrownBy(() -> service.preview(files, "person.zip", "UPLOAD", "tester"))
            .isInstanceOfSatisfying(
                StandardPackageContractException.class,
                error -> assertThat(error.code()).isEqualTo("MANIFEST_DEPENDENCY_MISSING")
            );
        verify(runRepository, never()).save(any());
    }

    @Test
    void preview_v2Package_rejectsVersionRollback() {
        when(runRepository.findByStatusOrderByCreatedDateDesc("APPLIED"))
            .thenReturn(List.of(appliedRun("dts-core-person", "2.0.0", "a".repeat(64))));
        byte[] terms = "term_code,term_name\nBT_PERSON,人员\n".getBytes(StandardCharsets.UTF_8);
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("manifest.json", manifestJson("dts-core-person", "1.0.0", "01-business-terms.csv", sha256(terms)));
        files.put("01-business-terms.csv", terms);

        assertThatThrownBy(() -> service.preview(files, "person.zip", "UPLOAD", "tester"))
            .isInstanceOfSatisfying(
                StandardPackageContractException.class,
                error -> assertThat(error.code()).isEqualTo("MANIFEST_VERSION_ROLLBACK")
            );
        verify(runRepository, never()).save(any());
    }

    @Test
    void preview_v2Package_rejectsCorruptedInstalledState() {
        StandardPackageImportRun corrupted = new StandardPackageImportRun();
        corrupted.setId(UUID.randomUUID());
        corrupted.setPackageName("dts-core-person");
        corrupted.setStatus("APPLIED");
        corrupted.setPreviewJson("{not-json");
        when(runRepository.findByStatusOrderByCreatedDateDesc("APPLIED")).thenReturn(List.of(corrupted));
        byte[] terms = "term_code,term_name\nBT_PERSON,人员\n".getBytes(StandardCharsets.UTF_8);
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("manifest.json", manifestJson("dts-core-person", "2.0.0", "01-business-terms.csv", sha256(terms)));
        files.put("01-business-terms.csv", terms);

        assertThatThrownBy(() -> service.preview(files, "person.zip", "UPLOAD", "tester"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("已安装标准包状态解析失败");
        verify(runRepository, never()).save(any());
    }

    private byte[] manifestJson(String packageCode, String version, String fileName, String checksum) {
        return manifestJson(packageCode, version, fileName, checksum, "[]");
    }

    private byte[] manifestJson(String packageCode, String version, String fileName, String checksum, String dependencies) {
        return """
        {
          "schemaVersion": "2.0",
          "packageCode": "%s",
          "packageName": "人员基础标准",
          "packageVersion": "%s",
          "category": "通用基础",
          "industry": "COMMON",
          "releasedAt": "2026-07-23T00:00:00Z",
          "effectiveFrom": "2026-07-23",
          "dependencies": %s,
          "replaces": [],
          "deprecated": false,
          "sourceRegisterRef": "SOURCE-REGISTER.json#%s",
          "licenseConclusion": "REVIEW_REQUIRED",
          "files": {"%s": "%s"}
        }
        """.formatted(packageCode, version, dependencies, packageCode, fileName, checksum)
            .getBytes(StandardCharsets.UTF_8);
    }

    private StandardPackageImportRun appliedRun(String packageCode, String packageVersion, String contentChecksum) {
        StandardPackageImportRun run = new StandardPackageImportRun();
        run.setPackageName(packageCode);
        run.setStatus("APPLIED");
        run.setPreviewJson(
            """
            {"packageCode":"%s","packageVersion":"%s","contentChecksum":"%s"}
            """.formatted(packageCode, packageVersion, contentChecksum)
        );
        return run;
    }

    private String sha256(String content) {
        return sha256(content.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private MockMultipartFile zipOf(Map<String, String> files) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return new MockMultipartFile("file", "test-package.zip", "application/zip", buffer.toByteArray());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castReports(Map<String, Object> preview) {
        return (List<Map<String, Object>>) preview.get("files");
    }

    private Map<String, Object> reportFor(List<Map<String, Object>> reports, String file) {
        return reports.stream().filter(report -> file.equals(report.get("file"))).findFirst().orElseThrow();
    }
}
