package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.StandardPackageImportRunRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StandardPackageBuiltinServiceTest {

    @Mock
    private StandardPackageImportService importService;

    @Mock
    private StandardPackageApplyService applyService;

    @Mock
    private StandardPackageImportRunRepository runRepository;

    private StandardPackageBuiltinService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        service = new StandardPackageBuiltinService(
            importService,
            applyService,
            runRepository,
            objectMapper,
            new StandardPackageManifestContract(objectMapper)
        );
        lenient().when(runRepository.findByStatusOrderByCreatedDateDesc("APPLIED")).thenReturn(List.of());
    }

    @Test
    @DisplayName("listBuiltin：清单含 4 个国标包且条目计数与资源一致")
    void listBuiltin_returnsManifestWithCounts() {
        List<Map<String, Object>> packages = service.listBuiltin();

        assertThat(packages).hasSize(4);
        Map<String, Object> gender = packages.stream().filter(p -> "gbt-2261-gender".equals(p.get("code"))).findFirst().orElseThrow();
        assertThat(gender.get("standardNo")).isEqualTo("GB/T 2261.1-2003");
        assertThat(gender.get("packageVersion")).isEqualTo("1.0.0");
        assertThat(gender.get("contentChecksum")).isNotNull();
        assertThat(gender.get("installed")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        Map<String, Integer> counts = (Map<String, Integer>) gender.get("entryCounts");
        assertThat(counts.get("04-reference-code-items.csv")).isEqualTo(4);

        Map<String, Object> region = packages.stream().filter(p -> "gbt-2260-region".equals(p.get("code"))).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Integer> regionCounts = (Map<String, Integer>) region.get("entryCounts");
        assertThat(regionCounts.get("04-reference-code-items.csv")).isEqualTo(34);

        Map<String, Object> common = packages.stream().filter(p -> "common-data-elements".equals(p.get("code"))).findFirst().orElseThrow();
        assertThat(common).containsEntry("dependencyReady", false).containsKey("installedVersion");
        assertThat(packages.get(packages.size() - 1).get("code")).isEqualTo("common-data-elements");
    }

    @Test
    @DisplayName("install：preview 无阻塞错误时自动 apply")
    void install_appliesWhenPreviewClean() {
        UUID runId = UUID.randomUUID();
        when(importService.preview(anyMap(), eq("gbt-2261-gender"), eq("BUILTIN"), anyString()))
            .thenReturn(Map.of("blocking", false, "runId", runId.toString()));
        when(applyService.apply(eq(runId), anyString())).thenReturn(Map.of("status", "APPLIED", "totalCreated", 5));

        Map<String, Object> result = service.install("gbt-2261-gender", "tester");

        assertThat(result.get("applied")).isEqualTo(true);
        assertThat(result.get("status")).isEqualTo("APPLIED");
        assertThat(result.get("packageName")).isEqualTo("性别代码");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, byte[]>> entries = ArgumentCaptor.forClass(Map.class);
        verify(importService).preview(entries.capture(), eq("gbt-2261-gender"), eq("BUILTIN"), eq("tester"));
        assertThat(entries.getValue()).containsKey("manifest.json");
        StandardPackageManifestContract.Manifest manifest = new StandardPackageManifestContract(new ObjectMapper())
            .parsePackage(entries.getValue().get("manifest.json"));
        assertThat(manifest.packageCode()).isEqualTo("gbt-2261-gender");
        assertThat(manifest.files()).containsKeys("03-reference-code-directories.csv", "04-reference-code-items.csv");
    }

    @Test
    @DisplayName("install：未知包代码拒绝（防路径遍历）")
    void install_unknownCode_throws() {
        assertThatThrownBy(() -> service.install("../etc/passwd", "tester"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("内置标准包不存在");
    }

    @Test
    @DisplayName("install：依赖包未安装时在 preview 前拒绝")
    void install_missingDependencies_rejectsBeforePreview() {
        assertThatThrownBy(() -> service.install("common-data-elements", "tester"))
            .isInstanceOfSatisfying(
                StandardPackageContractException.class,
                error -> assertThat(error.code()).isEqualTo("MANIFEST_DEPENDENCY_MISSING")
            );
        verify(importService, never()).preview(anyMap(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("install：preview 有错误时不入库并返回报告")
    void install_blockingPreview_returnsReportWithoutApply() {
        when(importService.preview(anyMap(), eq("gbt-4658-education"), eq("BUILTIN"), anyString()))
            .thenReturn(Map.of("blocking", true, "runId", UUID.randomUUID().toString(), "totalErrors", 2));

        Map<String, Object> result = service.install("gbt-4658-education", "tester");

        assertThat(result.get("applied")).isEqualTo(false);
        assertThat(result).containsKey("preview");
        org.mockito.Mockito.verify(applyService, org.mockito.Mockito.never()).apply(any(UUID.class), anyString());
    }
}
