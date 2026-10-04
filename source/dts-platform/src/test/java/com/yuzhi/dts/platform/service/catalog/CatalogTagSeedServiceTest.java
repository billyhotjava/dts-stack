package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.catalog.CatalogTag;
import com.yuzhi.dts.platform.domain.catalog.CatalogTagCategory;
import com.yuzhi.dts.platform.domain.modeling.StandardPackageImportRun;
import com.yuzhi.dts.platform.repository.catalog.CatalogTagCategoryRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTagInstallLockRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTagRepository;
import com.yuzhi.dts.platform.repository.modeling.StandardPackageImportRunRepository;
import com.yuzhi.dts.platform.service.modeling.StandardPackageInstallLedgerService;
import com.yuzhi.dts.platform.service.modeling.StandardPackageContractException;
import com.yuzhi.dts.platform.service.modeling.StandardPackageManifestContract;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

@ExtendWith(MockitoExtension.class)
class CatalogTagSeedServiceTest {

    @Mock
    private CatalogTagCategoryRepository categoryRepository;

    @Mock
    private CatalogTagRepository tagRepository;

    @Mock
    private CatalogTagInstallLockRepository installLockRepository;

    @Mock
    private StandardPackageImportRunRepository runRepository;

    private ObjectMapper objectMapper;
    private StandardPackageManifestContract manifestContract;
    private CatalogTagSeedService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        manifestContract = new StandardPackageManifestContract(objectMapper);
        service = new CatalogTagSeedService(
            categoryRepository,
            tagRepository,
            installLockRepository,
            new StandardPackageInstallLedgerService(runRepository),
            objectMapper,
            manifestContract
        );
        lenient().when(categoryRepository.save(any(CatalogTagCategory.class))).thenAnswer(invocation -> {
            CatalogTagCategory category = invocation.getArgument(0);
            category.setId(UUID.randomUUID());
            return category;
        });
        lenient().when(tagRepository.save(any(CatalogTag.class))).thenAnswer(invocation -> {
            CatalogTag tag = invocation.getArgument(0);
            tag.setId(UUID.randomUUID());
            return tag;
        });
    }

    @Test
    void acquiresTransactionLockBeforeReadingInstalledStateAndCatalogRows() {
        service.install();

        InOrder order = inOrder(installLockRepository, runRepository, categoryRepository);
        order.verify(installLockRepository).acquirePackageLock("dts-common-catalog-tags");
        order.verify(runRepository).findByStatusOrderByCreatedDateDesc("APPLIED");
        order.verify(categoryRepository).findByCode("BUSINESS-DOMAIN");
    }

    @Test
    void builtinManifestUsesV2AndVerifiesTheDeclaredCatalogChecksum() throws Exception {
        byte[] manifestBytes = readResource("manifest.json");
        byte[] catalogBytes = readResource("catalog.json");

        var manifest = manifestContract.parsePackage(manifestBytes);
        manifestContract.verifyFiles(
            manifest,
            Map.of("manifest.json", manifestBytes, "catalog.json", catalogBytes)
        );

        assertThat(manifest.schemaVersion()).isEqualTo(StandardPackageManifestContract.SCHEMA_V2);
        assertThat(manifest.files()).containsOnlyKeys("catalog.json");
        assertThat(manifest.contentChecksum()).hasSize(64);
    }

    @Test
    void firstInstallCreatesVersionedBuiltinCatalogWithoutClassificationSemantics() {
        var report = service.install();
        JsonNode json = objectMapper.valueToTree(report);

        assertThat(report.packageCode()).isEqualTo("dts-common-catalog-tags");
        assertThat(report.packageVersion()).isEqualTo("1.0.0");
        assertThat(json.has("applied")).isTrue();
        assertThat(json.path("applied").asBoolean()).isTrue();
        assertThat(json.path("conflicts")).isEmpty();
        assertThat(report.categoriesCreated()).isEqualTo(5);
        assertThat(report.tagsCreated()).isEqualTo(20);
        assertThat(report.categoriesSkipped()).isZero();
        assertThat(report.tagsSkipped()).isZero();
        assertThat(report.installedCodes())
            .allSatisfy(code -> assertThat(code).doesNotContainIgnoringCase("SECRET").doesNotContainIgnoringCase("CLASSIFICATION"));
    }

    @Test
    void reinstallSkipsExistingCodesAndPreservesCustomerRenameAndDisabledState() {
        CatalogTagCategory existingCategory = new CatalogTagCategory();
        existingCategory.setId(UUID.randomUUID());
        existingCategory.setCode("BUSINESS-DOMAIN");
        existingCategory.setName("客户业务域");
        existingCategory.setEnabled(false);
        existingCategory.setBuiltin(true);
        CatalogTag existingTag = new CatalogTag();
        existingTag.setId(UUID.randomUUID());
        existingTag.setCategoryId(existingCategory.getId());
        existingTag.setCode("BUSINESS-FINANCE");
        existingTag.setName("客户财务域");
        existingTag.setEnabled(false);
        existingTag.setBuiltin(true);
        when(categoryRepository.findByCode("BUSINESS-DOMAIN")).thenReturn(Optional.of(existingCategory));
        when(tagRepository.findByCode("BUSINESS-FINANCE")).thenReturn(Optional.of(existingTag));

        var report = service.install();
        JsonNode json = objectMapper.valueToTree(report);

        assertThat(json.path("applied").asBoolean()).isTrue();
        assertThat(json.path("conflicts")).isEmpty();
        assertThat(report.categoriesSkipped()).isEqualTo(1);
        assertThat(report.tagsSkipped()).isEqualTo(1);
        assertThat(existingCategory.getName()).isEqualTo("客户业务域");
        assertThat(existingCategory.isEnabled()).isFalse();
        assertThat(existingTag.getName()).isEqualTo("客户财务域");
        assertThat(existingTag.isEnabled()).isFalse();
    }

    @Test
    void customerCategoryCodeConflictReturnsStableReportWithoutCatalogOrAppliedRunWrites() {
        CatalogTagCategory custom = new CatalogTagCategory();
        custom.setId(UUID.randomUUID());
        custom.setCode("BUSINESS-DOMAIN");
        custom.setName("客户自定义业务域");
        custom.setBuiltin(false);
        custom.setEnabled(true);
        when(categoryRepository.findByCode("BUSINESS-DOMAIN")).thenReturn(Optional.of(custom));

        JsonNode report = objectMapper.valueToTree(service.install());

        assertThat(report.path("applied").asBoolean()).isFalse();
        assertThat(report.path("categoriesCreated").asInt()).isZero();
        assertThat(report.path("tagsCreated").asInt()).isZero();
        assertThat(report.path("conflicts")).hasSize(1);
        assertThat(report.path("conflicts").get(0).path("itemType").asText()).isEqualTo("CATEGORY");
        assertThat(report.path("conflicts").get(0).path("code").asText()).isEqualTo("BUSINESS-DOMAIN");
        assertThat(report.path("conflicts").get(0).path("reason").asText())
            .isEqualTo("CUSTOM_CATEGORY_CODE_CONFLICT");
        assertThat(report.path("conflicts").get(0).path("expectedCategoryCode").isNull()).isTrue();
        assertThat(report.path("conflicts").get(0).path("actualCategoryCode").isNull()).isTrue();
        verify(categoryRepository, times(0)).save(any(CatalogTagCategory.class));
        verify(tagRepository, times(0)).save(any(CatalogTag.class));
        verify(runRepository, times(0)).save(any(StandardPackageImportRun.class));
    }

    @Test
    void customerTagCodeConflictReturnsStableReportWithoutCatalogOrAppliedRunWrites() {
        CatalogTag custom = new CatalogTag();
        custom.setId(UUID.randomUUID());
        custom.setCategoryId(UUID.randomUUID());
        custom.setCode("BUSINESS-FINANCE");
        custom.setName("客户自定义财务标签");
        custom.setBuiltin(false);
        custom.setEnabled(true);
        when(tagRepository.findByCode("BUSINESS-FINANCE")).thenReturn(Optional.of(custom));

        JsonNode report = objectMapper.valueToTree(service.install());

        assertThat(report.path("applied").asBoolean()).isFalse();
        assertThat(report.path("conflicts")).hasSize(1);
        assertThat(report.path("conflicts").get(0).path("itemType").asText()).isEqualTo("TAG");
        assertThat(report.path("conflicts").get(0).path("code").asText()).isEqualTo("BUSINESS-FINANCE");
        assertThat(report.path("conflicts").get(0).path("reason").asText())
            .isEqualTo("CUSTOM_TAG_CODE_CONFLICT");
        assertThat(report.path("conflicts").get(0).path("expectedCategoryCode").asText())
            .isEqualTo("BUSINESS-DOMAIN");
        verify(categoryRepository, times(0)).save(any(CatalogTagCategory.class));
        verify(tagRepository, times(0)).save(any(CatalogTag.class));
        verify(runRepository, times(0)).save(any(StandardPackageImportRun.class));
    }

    @Test
    void builtinTagInTheWrongCategoryIsAConflictAndReportsBothCategoryCodes() {
        CatalogTagCategory expected = new CatalogTagCategory();
        expected.setId(UUID.randomUUID());
        expected.setCode("BUSINESS-DOMAIN");
        expected.setName("业务域");
        expected.setBuiltin(true);
        expected.setEnabled(true);
        CatalogTagCategory actual = new CatalogTagCategory();
        actual.setId(UUID.randomUUID());
        actual.setCode("OTHER");
        actual.setName("其他分类");
        actual.setBuiltin(true);
        actual.setEnabled(true);
        CatalogTag misplaced = new CatalogTag();
        misplaced.setId(UUID.randomUUID());
        misplaced.setCategoryId(actual.getId());
        misplaced.setCode("BUSINESS-FINANCE");
        misplaced.setName("财务");
        misplaced.setBuiltin(true);
        misplaced.setEnabled(true);
        when(categoryRepository.findByCode("BUSINESS-DOMAIN")).thenReturn(Optional.of(expected));
        when(categoryRepository.findById(actual.getId())).thenReturn(Optional.of(actual));
        when(tagRepository.findByCode("BUSINESS-FINANCE")).thenReturn(Optional.of(misplaced));

        JsonNode report = objectMapper.valueToTree(service.install());

        assertThat(report.path("applied").asBoolean()).isFalse();
        assertThat(report.path("conflicts")).hasSize(1);
        assertThat(report.path("conflicts").get(0).path("reason").asText())
            .isEqualTo("BUILTIN_TAG_CATEGORY_MISMATCH");
        assertThat(report.path("conflicts").get(0).path("expectedCategoryCode").asText())
            .isEqualTo("BUSINESS-DOMAIN");
        assertThat(report.path("conflicts").get(0).path("actualCategoryCode").asText())
            .isEqualTo("OTHER");
        verify(categoryRepository, times(0)).save(any(CatalogTagCategory.class));
        verify(tagRepository, times(0)).save(any(CatalogTag.class));
        verify(runRepository, times(0)).save(any(StandardPackageImportRun.class));
    }

    @Test
    void installPersistsOneAppliedRunForTheSameVersionAndContentDigest() throws Exception {
        AtomicReference<StandardPackageImportRun> installedRun = new AtomicReference<>();
        when(runRepository.findByStatusOrderByCreatedDateDesc("APPLIED"))
            .thenAnswer(invocation -> installedRun.get() == null ? List.of() : List.of(installedRun.get()));
        when(runRepository.save(any(StandardPackageImportRun.class))).thenAnswer(invocation -> {
            StandardPackageImportRun run = invocation.getArgument(0);
            run.setId(UUID.randomUUID());
            installedRun.set(run);
            return run;
        });

        var first = service.install();
        var second = service.install();

        verify(runRepository, times(1)).save(any(StandardPackageImportRun.class));
        JsonNode summary = objectMapper.readTree(installedRun.get().getPreviewJson());
        assertThat(installedRun.get().getStatus()).isEqualTo("APPLIED");
        assertThat(summary.path("packageCode").asText()).isEqualTo(first.packageCode());
        assertThat(summary.path("packageVersion").asText()).isEqualTo(first.packageVersion());
        assertThat(summary.path("contentChecksum").asText()).hasSize(64);
        assertThat(second.packageVersion()).isEqualTo(first.packageVersion());
    }

    @Test
    void installRejectsDowngradeAndSameVersionContentDriftBeforeWritingCatalogRows() {
        when(runRepository.findByStatusOrderByCreatedDateDesc("APPLIED"))
            .thenReturn(List.of(installedRun("2.0.0", "0".repeat(64))));
        assertThatThrownBy(service::install)
            .isInstanceOf(StandardPackageContractException.class)
            .extracting(exception -> ((StandardPackageContractException) exception).code())
            .isEqualTo("MANIFEST_VERSION_ROLLBACK");

        when(runRepository.findByStatusOrderByCreatedDateDesc("APPLIED"))
            .thenReturn(List.of(installedRun("1.0.0", "f".repeat(64))));
        assertThatThrownBy(service::install)
            .isInstanceOf(StandardPackageContractException.class)
            .extracting(exception -> ((StandardPackageContractException) exception).code())
            .isEqualTo("MANIFEST_VERSION_CONTENT_MISMATCH");
        verify(categoryRepository, times(0)).save(any(CatalogTagCategory.class));
        verify(tagRepository, times(0)).save(any(CatalogTag.class));
    }

    private StandardPackageImportRun installedRun(String version, String checksum) {
        StandardPackageImportRun run = new StandardPackageImportRun();
        run.setId(UUID.randomUUID());
        run.setPackageName("dts-common-catalog-tags");
        run.setSource("CATALOG_TAG_BUILTIN");
        run.setStatus("APPLIED");
        run.setPreviewJson(
            """
            {"packageCode":"dts-common-catalog-tags","packageVersion":"%s","contentChecksum":"%s"}
            """.formatted(version, checksum)
        );
        return run;
    }

    private byte[] readResource(String fileName) throws Exception {
        try (
            InputStream input = new ClassPathResource(
                "catalog-tags/dts-common-catalog-tags/" + fileName
            ).getInputStream()
        ) {
            return input.readAllBytes();
        }
    }
}
