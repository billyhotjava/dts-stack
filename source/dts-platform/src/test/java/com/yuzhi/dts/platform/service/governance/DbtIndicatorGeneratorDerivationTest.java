package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.DbtProperties;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class DbtIndicatorGeneratorDerivationTest {

    private final GovIndicatorDefinitionRepository repository = mock(GovIndicatorDefinitionRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ControlledIndicatorDerivationCompiler compiler = new ControlledIndicatorDerivationCompiler();
    private final DbtIndicatorGenerator generator = new DbtIndicatorGenerator(
        repository,
        new DbtProperties(),
        objectMapper,
        compiler,
        new IndicatorDerivationValidationService(repository, compiler, objectMapper)
    );

    @Test
    void compilesTokensAndJoinsEveryDependencyAtTheDeclaredGrain() {
        UUID targetId = UUID.randomUUID();
        GovIndicatorDefinition target = indicator(targetId, "AVG_ORDER", "[{\"field\":\"region_code\"}]");
        target.setDependencyIndicators("[\"GMV\",\"ORDER_COUNT\"]");
        target.setExpressionSql("{{metric:GMV}} / nullif({{metric:ORDER_COUNT}}, 0)");

        GovIndicatorDefinition gmv = indicator(UUID.randomUUID(), "GMV", "[{\"field\":\"region_code\"}]");
        GovIndicatorDefinition count = indicator(UUID.randomUUID(), "ORDER_COUNT", "[{\"field\":\"region_code\"}]");

        when(repository.findById(targetId)).thenReturn(Optional.of(target));
        when(repository.findFirstByCodeIgnoreCase("GMV")).thenReturn(Optional.of(gmv));
        when(repository.findFirstByCodeIgnoreCase("ORDER_COUNT")).thenReturn(Optional.of(count));

        Map<String, String> preview = generator.previewSql(targetId);

        assertThat(preview.get("sql"))
            .contains("{{ ref('ind_GMV') }} AS dep_0")
            .contains("JOIN {{ ref('ind_ORDER_COUNT') }} AS dep_1")
            .contains("dep_1.report_period = dep_0.report_period")
            .contains("dep_1.region_code = dep_0.region_code")
            .contains("dep_0.GMV / nullif(dep_1.ORDER_COUNT, 0) AS AVG_ORDER")
            .doesNotContain("{{metric:");
    }

    @Test
    void stableMetricTypeDrivesDerivedPreviewWhenLegacyFlagIsStale() {
        UUID targetId = UUID.randomUUID();
        GovIndicatorDefinition target = indicator(targetId, "GMV_COPY", "[]");
        target.setMetricType("DERIVED");
        target.setIsDerived(false);
        target.setDependencyIndicators("[\"GMV\"]");
        target.setExpressionSql("{{metric:GMV}}");
        GovIndicatorDefinition gmv = indicator(UUID.randomUUID(), "GMV", "[]");

        when(repository.findById(targetId)).thenReturn(Optional.of(target));
        when(repository.findFirstByCodeIgnoreCase("GMV")).thenReturn(Optional.of(gmv));

        assertThat(generator.previewSql(targetId).get("sql"))
            .contains("{{ ref('ind_GMV') }} AS dep_0")
            .contains("dep_0.GMV AS GMV_COPY");
    }

    @Test
    void refusesToJoinDependenciesWhoseDimensionsDoNotMatchTheTarget() {
        UUID targetId = UUID.randomUUID();
        GovIndicatorDefinition target = indicator(targetId, "AVG_ORDER", "[\"region_code\"]");
        target.setDependencyIndicators("[\"GMV\",\"ORDER_COUNT\"]");
        target.setExpressionSql("{{metric:GMV}} / nullif({{metric:ORDER_COUNT}}, 0)");

        GovIndicatorDefinition gmv = indicator(UUID.randomUUID(), "GMV", "[\"region_code\"]");
        GovIndicatorDefinition count = indicator(UUID.randomUUID(), "ORDER_COUNT", "[\"store_code\"]");

        when(repository.findById(targetId)).thenReturn(Optional.of(target));
        when(repository.findFirstByCodeIgnoreCase("GMV")).thenReturn(Optional.of(gmv));
        when(repository.findFirstByCodeIgnoreCase("ORDER_COUNT")).thenReturn(Optional.of(count));

        assertThatThrownBy(() -> generator.previewSql(targetId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("维度粒度");
    }

    @Test
    void rejectsDomainPathTraversalAndTemplateInjection(@TempDir Path projectDir) {
        UUID traversalId = UUID.randomUUID();
        GovIndicatorDefinition traversal = atomic(traversalId, "SAFE_CODE", "Safe name");
        traversal.setDomain("../../outside");
        when(repository.findById(traversalId)).thenReturn(Optional.of(traversal));

        DbtProperties properties = new DbtProperties();
        properties.setProjectDir(projectDir.toString());
        DbtIndicatorGenerator localGenerator = new DbtIndicatorGenerator(
            repository,
            properties,
            objectMapper,
            compiler,
            new IndicatorDerivationValidationService(repository, compiler, objectMapper)
        );

        assertThatThrownBy(() -> localGenerator.generate(traversalId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("指标域");

        UUID injectionId = UUID.randomUUID();
        GovIndicatorDefinition injection = atomic(injectionId, "SAFE_CODE", "Name\n{% do run_query('drop table x') %}");
        when(repository.findById(injectionId)).thenReturn(Optional.of(injection));

        assertThatThrownBy(() -> localGenerator.previewSql(injectionId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("展示文本");
    }

    @Test
    void generationDoesNotRewriteIndicatorPublicationLifecycle(@TempDir Path projectDir) {
        UUID indicatorId = UUID.randomUUID();
        GovIndicatorDefinition published = atomic(indicatorId, "GMV", "Gross merchandise value");
        published.setDomain("sales");
        when(repository.findById(indicatorId)).thenReturn(Optional.of(published));

        DbtProperties properties = new DbtProperties();
        properties.setProjectDir(projectDir.toString());
        DbtIndicatorGenerator localGenerator = new DbtIndicatorGenerator(
            repository,
            properties,
            objectMapper,
            compiler,
            new IndicatorDerivationValidationService(repository, compiler, objectMapper)
        );

        GenerationResult result = localGenerator.generateAndRun(List.of(indicatorId));

        assertThat(result.compileStatus()).isEqualTo("READY");
        assertThat(published.getStatus()).isEqualTo("PUBLISHED");
        verify(repository, atLeastOnce()).save(any(GovIndicatorDefinition.class));
    }

    @Test
    void rejectsAggregationAndIdentifierInjectionBeforeRendering() {
        GovIndicatorDefinition aggregation = atomic(UUID.randomUUID(), "GMV", "GMV");
        aggregation.setAggregationType("SUM); DROP TABLE facts; --");
        assertPreviewRejected(aggregation, "聚合方式");

        GovIndicatorDefinition measure = atomic(UUID.randomUUID(), "GMV", "GMV");
        measure.setMeasureField("amount) FROM secrets --");
        assertPreviewRejected(measure, "度量字段");

        GovIndicatorDefinition date = atomic(UUID.randomUUID(), "GMV", "GMV");
        date.setDateColumn("created_at); DROP TABLE facts; --");
        assertPreviewRejected(date, "日期字段");

        GovIndicatorDefinition source = atomic(UUID.randomUUID(), "GMV", "GMV");
        source.setSourceTable("facts') }}; DROP TABLE facts; --");
        assertPreviewRejected(source, "来源表");

        GovIndicatorDefinition dimension = atomic(UUID.randomUUID(), "GMV", "GMV");
        dimension.setDimensionFields("[{\"field\":\"region_code, pg_sleep(10)\"}]");
        assertPreviewRejected(dimension, "维度字段");
    }

    @Test
    void ratioExpressionsUseControlledArithmeticDsl() {
        UUID id = UUID.randomUUID();
        GovIndicatorDefinition ratio = atomic(id, "ORDER_RATE", "Order rate");
        ratio.setAggregationType("RATIO");
        ratio.setNumeratorExpression("sum(completed_count) + 1");
        ratio.setDenominatorExpression("nullif(count(order_id), 0)");
        when(repository.findById(id)).thenReturn(Optional.of(ratio));

        assertThat(generator.previewSql(id).get("sql"))
            .contains("(sum(completed_count) + 1) AS _numerator")
            .contains("(nullif(count(order_id), 0)) AS _denominator");

        GovIndicatorDefinition injection = atomic(UUID.randomUUID(), "ORDER_RATE_BAD", "Order rate");
        injection.setAggregationType("RATIO");
        injection.setNumeratorExpression("sum(completed_count)); DROP TABLE facts; --");
        injection.setDenominatorExpression("count(order_id)");
        assertPreviewRejected(injection, "分子表达式");
    }

    @Test
    void staticFilterUsesControlledPredicateDsl() {
        UUID id = UUID.randomUUID();
        GovIndicatorDefinition safeFilter = atomic(id, "OVERDUE_COUNT", "Overdue count");
        safeFilter.setStaticFilter("status = 'OVERDUE' AND amount >= 10");
        when(repository.findById(id)).thenReturn(Optional.of(safeFilter));

        assertThat(generator.previewSql(id).get("sql"))
            .contains("AND status = 'OVERDUE' AND amount >= 10");

        GovIndicatorDefinition injection = atomic(UUID.randomUUID(), "OVERDUE_BAD", "Overdue count");
        injection.setStaticFilter("status = 'OVERDUE'; DROP TABLE facts; --");
        assertPreviewRejected(injection, "固定过滤条件");
    }

    @Test
    void joinConfigAcceptsOnlyNarrowJoinShapes() {
        UUID id = UUID.randomUUID();
        GovIndicatorDefinition safeJoin = atomic(id, "GMV", "GMV");
        safeJoin.setJoinConfig(
            """
            [{"type":"LEFT","table":"dim_customer","alias":"customer","on":"fact_orders.customer_id = customer.id"}]
            """
        );
        when(repository.findById(id)).thenReturn(Optional.of(safeJoin));

        assertThat(generator.previewSql(id).get("sql"))
            .contains("LEFT JOIN {{ ref('dim_customer') }} AS customer")
            .contains("ON fact_orders.customer_id = customer.id");

        GovIndicatorDefinition unsafeType = atomic(UUID.randomUUID(), "BAD_TYPE", "Bad join");
        unsafeType.setJoinConfig(
            """
            [{"type":"LEFT; DROP TABLE facts","table":"dim_customer","alias":"customer","on":"fact_orders.customer_id = customer.id"}]
            """
        );
        assertPreviewRejected(unsafeType, "关联类型");

        GovIndicatorDefinition unsafeOn = atomic(UUID.randomUUID(), "BAD_ON", "Bad join");
        unsafeOn.setJoinConfig(
            """
            [{"type":"LEFT","table":"dim_customer","alias":"customer","on":"fact_orders.customer_id = customer.id OR 1=1"}]
            """
        );
        assertPreviewRejected(unsafeOn, "关联条件");
    }

    @Test
    void malformedGeneratorJsonFailsClosed() {
        GovIndicatorDefinition dimensions = atomic(UUID.randomUUID(), "BAD_DIMS", "Bad dimensions");
        dimensions.setDimensionFields("[{\"field\":");
        assertPreviewRejected(dimensions, "dimensionFields");

        GovIndicatorDefinition joins = atomic(UUID.randomUUID(), "BAD_JOINS", "Bad joins");
        joins.setJoinConfig("[{\"type\":");
        assertPreviewRejected(joins, "joinConfig");

        GovIndicatorDefinition dependencies = atomic(UUID.randomUUID(), "BAD_DEPS", "Bad dependencies");
        dependencies.setIsDerived(true);
        dependencies.setDependencyIndicators("[\"GMV\"");
        when(repository.findById(dependencies.getId())).thenReturn(Optional.of(dependencies));
        assertThatThrownBy(() -> generator.topologicalSort(List.of(dependencies.getId())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dependencyIndicators");
    }

    @Test
    void jsonWithTrailingTokensFailsClosed() {
        GovIndicatorDefinition dimensions = atomic(UUID.randomUUID(), "TRAILING_DIMS", "Trailing dimensions");
        dimensions.setDimensionFields("[{\"field\":\"region_code\"}] trailing");
        assertPreviewRejected(dimensions, "dimensionFields");

        GovIndicatorDefinition joins = atomic(UUID.randomUUID(), "TRAILING_JOIN", "Trailing join");
        joins.setJoinConfig(
            """
            [{"type":"LEFT","table":"dim_customer","alias":"customer","on":"fact_orders.customer_id = customer.id"}] {}
            """
        );
        assertPreviewRejected(joins, "joinConfig");

        GovIndicatorDefinition dependencies = atomic(UUID.randomUUID(), "TRAILING_DEPS", "Trailing dependencies");
        dependencies.setIsDerived(true);
        dependencies.setDependencyIndicators("[\"GMV\"] trailing");
        when(repository.findById(dependencies.getId())).thenReturn(Optional.of(dependencies));

        assertThatThrownBy(() -> generator.topologicalSort(List.of(dependencies.getId())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dependencyIndicators");
    }

    @Test
    void customSqlRequiresCurrentValidationSignatureAndRejectsTemplateContent() {
        UUID id = UUID.randomUUID();
        GovIndicatorDefinition custom = atomic(id, "CUSTOM_GMV", "Custom GMV");
        custom.setAggregationType("CUSTOM");
        custom.setDatasetId(UUID.randomUUID().toString());
        custom.setExpressionSql("SELECT SUM(amount) AS CUSTOM_GMV FROM fact_orders");
        custom.setLastValidationStatus("SUCCESS");
        when(repository.findById(id)).thenReturn(Optional.of(custom));

        assertThatThrownBy(() -> generator.previewSql(id))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("当前版本")
            .hasMessageContaining("校验");

        custom.setLastValidationSignature(IndicatorValidationSignature.compute(custom, objectMapper, repository));
        assertThat(generator.previewSql(id).get("sql"))
            .contains("SELECT SUM(amount) AS CUSTOM_GMV FROM fact_orders");

        GovIndicatorDefinition jinja = atomic(UUID.randomUUID(), "CUSTOM_BAD", "Custom bad");
        jinja.setAggregationType("CUSTOM");
        jinja.setDatasetId(UUID.randomUUID().toString());
        jinja.setExpressionSql("SELECT {{ run_query('drop table facts') }}");
        jinja.setLastValidationStatus("SUCCESS");
        jinja.setLastValidationSignature(IndicatorValidationSignature.compute(jinja, objectMapper, repository));
        assertPreviewRejected(jinja, "Jinja");
    }

    @Test
    void finalArtifactValidationRejectsUnresolvedGeneratorPlaceholders() {
        GovIndicatorDefinition custom = atomic(UUID.randomUUID(), "CUSTOM_BAD", "Custom bad");
        custom.setAggregationType("CUSTOM");
        custom.setDatasetId(UUID.randomUUID().toString());
        custom.setExpressionSql("SELECT {unresolvedArtifactValue} AS CUSTOM_BAD");
        custom.setLastValidationStatus("SUCCESS");
        custom.setLastValidationSignature(IndicatorValidationSignature.compute(custom, objectMapper, repository));

        assertPreviewRejected(custom, "未解析");
    }

    @Test
    void yamlDisplayValuesAreEscapedAndTemplateContentIsRejected(@TempDir Path projectDir) throws IOException {
        UUID id = UUID.randomUUID();
        GovIndicatorDefinition escaped = atomic(id, "GMV", "Gross \"value\" \\ total");
        escaped.setDomain("sales");
        escaped.setUnit("元\"\\");
        escaped.setDimensionFields(
            """
            [{"field":"region_code","displayName":"Region \\"East\\" \\\\ total"}]
            """
        );
        when(repository.findById(id)).thenReturn(Optional.of(escaped));

        newGenerator(projectDir).generateSchemaYml(List.of(id));

        assertThat(Files.readString(projectDir.resolve("models/ads/sales/sales_indicators_schema.yml")))
            .contains("description: \"自动生成 - Gross \\\"value\\\" \\\\ total\"")
            .contains("description: \"Gross \\\"value\\\" \\\\ total (元\\\"\\\\)\"")
            .contains("description: \"Region \\\"East\\\" \\\\ total\"");

        GovIndicatorDefinition unsafeUnit = atomic(UUID.randomUUID(), "BAD_UNIT", "Bad unit");
        unsafeUnit.setDomain("sales");
        unsafeUnit.setUnit("{% do run_query('drop table facts') %}");
        when(repository.findById(unsafeUnit.getId())).thenReturn(Optional.of(unsafeUnit));
        assertThatThrownBy(() -> newGenerator(projectDir).generateSchemaYml(List.of(unsafeUnit.getId())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("计量单位");

        GovIndicatorDefinition unsafeDisplay = atomic(UUID.randomUUID(), "BAD_DISPLAY", "Bad display");
        unsafeDisplay.setDomain("sales");
        unsafeDisplay.setDimensionFields("[{\"field\":\"region_code\",\"displayName\":\"Region\\nDROP\"}]");
        when(repository.findById(unsafeDisplay.getId())).thenReturn(Optional.of(unsafeDisplay));
        assertThatThrownBy(() -> newGenerator(projectDir).generateSchemaYml(List.of(unsafeDisplay.getId())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("维度展示名");
    }

    @Test
    void batchLimitsIdsAndDependenciesBeforeRepositoryExpansion() {
        List<UUID> tooManyIds = new ArrayList<>();
        for (int index = 0; index < 101; index++) {
            tooManyIds.add(UUID.randomUUID());
        }
        assertThatThrownBy(() -> generator.generateBatch(tooManyIds))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("100");
        verifyNoInteractions(repository);

        GovIndicatorDefinition tooManyDependencies = atomic(UUID.randomUUID(), "TOO_MANY_DEPS", "Too many deps");
        tooManyDependencies.setIsDerived(true);
        List<String> dependencyCodes = new ArrayList<>();
        for (int index = 0; index < 33; index++) {
            dependencyCodes.add("\"DEP_" + index + "\"");
        }
        tooManyDependencies.setDependencyIndicators("[" + String.join(",", dependencyCodes) + "]");
        when(repository.findById(tooManyDependencies.getId())).thenReturn(Optional.of(tooManyDependencies));

        assertThatThrownBy(() -> generator.topologicalSort(List.of(tooManyDependencies.getId())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("32");
    }

    @Test
    void batchPromotionRollsBackPreviouslyReplacedArtifacts(@TempDir Path projectDir) throws IOException {
        UUID id = UUID.randomUUID();
        GovIndicatorDefinition indicator = atomic(id, "GMV", "GMV");
        indicator.setDomain("sales");
        indicator.setWindowFunction("MOM");
        when(repository.findById(id)).thenReturn(Optional.of(indicator));

        Path modelDir = projectDir.resolve("models/ads/sales");
        Files.createDirectories(modelDir);
        Path baseArtifact = modelDir.resolve("ind_GMV.sql");
        Files.writeString(baseArtifact, "previous artifact");
        Path blockedWindowArtifact = modelDir.resolve("ind_GMV_window.sql");
        Files.createDirectories(blockedWindowArtifact);
        Files.writeString(blockedWindowArtifact.resolve("keep.txt"), "block replacement");

        assertThatThrownBy(() -> newGenerator(projectDir).generateBatch(List.of(id)))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("dbt");

        assertThat(Files.readString(baseArtifact)).isEqualTo("previous artifact");
        assertThat(blockedWindowArtifact.resolve("keep.txt")).hasContent("block replacement");
        try (var paths = Files.walk(projectDir)) {
            assertThat(paths.map(path -> path.getFileName().toString()))
                .noneMatch(name -> name.startsWith(".indicator-stage-") || name.startsWith(".indicator-backup-"));
        }
        verify(repository, never()).save(indicator);
    }

    @Test
    void regenerationRemovesPreviouslyOwnedDomainAndWindowSql(@TempDir Path projectDir) {
        UUID id = UUID.randomUUID();
        GovIndicatorDefinition indicator = atomic(id, "GMV", "GMV");
        indicator.setDomain("sales");
        indicator.setWindowFunction("MOM");
        when(repository.findById(id)).thenReturn(Optional.of(indicator));
        DbtIndicatorGenerator localGenerator = newGenerator(projectDir);

        localGenerator.generate(id);

        Path oldModel = projectDir.resolve("models/ads/sales/ind_GMV.sql");
        Path oldWindow = projectDir.resolve("models/ads/sales/ind_GMV_window.sql");
        assertThat(oldModel).exists();
        assertThat(oldWindow).exists();

        indicator.setDomain("finance");
        indicator.setWindowFunction("NONE");
        localGenerator.generate(id);

        assertThat(oldModel).doesNotExist();
        assertThat(oldWindow).doesNotExist();
        assertThat(projectDir.resolve("models/ads/finance/ind_GMV.sql")).exists();
    }

    @Test
    void bootstrapsGeneratedStableCodeArtifactsWhenManifestDoesNotYetExist(@TempDir Path projectDir)
        throws IOException {
        UUID id = UUID.randomUUID();
        GovIndicatorDefinition indicator = atomic(id, "GMV", "GMV");
        indicator.setDomain("finance");
        when(repository.findById(id)).thenReturn(Optional.of(indicator));

        Path legacyDomain = projectDir.resolve("models/ads/sales");
        Path manualDomain = projectDir.resolve("models/ads/manual");
        Files.createDirectories(legacyDomain);
        Files.createDirectories(manualDomain);
        Files.writeString(
            legacyDomain.resolve("ind_GMV.sql"),
            "-- 自动生成: Legacy GMV (GMV)\nSELECT 1"
        );
        Files.writeString(
            legacyDomain.resolve("ind_GMV_window.sql"),
            "-- 窗口函数(环比): Legacy GMV (GMV)\nSELECT 1"
        );
        Files.writeString(manualDomain.resolve("ind_GMV.sql"), "SELECT 42 AS hand_written");

        newGenerator(projectDir).generateAndRun(List.of(id));

        assertThat(legacyDomain.resolve("ind_GMV.sql")).doesNotExist();
        assertThat(legacyDomain.resolve("ind_GMV_window.sql")).doesNotExist();
        assertThat(manualDomain.resolve("ind_GMV.sql")).hasContent("SELECT 42 AS hand_written");
        assertThat(projectDir.resolve("models/ads/finance/ind_GMV.sql")).exists();
        String manifest = Files.readString(
            projectDir.resolve("models/ads/.dts-indicator-artifacts.json")
        );
        assertThat(manifest)
            .contains("\"version\":2")
            .contains("\"domain\":\"finance\"")
            .contains(
                "\"schemaPath\":\"models/ads/finance/finance_indicators_schema.yml\""
            );
    }

    @Test
    void bootstrapsAnUnownedStableCodeAfterAnotherIndicatorCreatedTheManifest(@TempDir Path projectDir)
        throws Exception {
        UUID ownedId = UUID.randomUUID();
        UUID adoptingId = UUID.randomUUID();
        Path manifest = projectDir.resolve("models/ads/.dts-indicator-artifacts.json");
        Path legacy = projectDir.resolve("models/ads/sales/ind_GMV.sql");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "-- 自动生成: Legacy GMV (GMV)\nSELECT 1");
        Files.writeString(
            manifest,
            objectMapper.writeValueAsString(
                Map.of(
                    "version",
                    1,
                    "indicators",
                    Map.of(
                        ownedId.toString(),
                        Map.of(
                            "modelPath",
                            "models/ads/ops/ind_EXISTING.sql"
                        )
                    )
                )
            )
        );
        GovIndicatorDefinition indicator = atomic(adoptingId, "GMV", "GMV");
        indicator.setDomain("finance");
        when(repository.findById(adoptingId)).thenReturn(Optional.of(indicator));

        newGenerator(projectDir).generateAndRun(List.of(adoptingId));

        assertThat(legacy).doesNotExist();
        assertThat(Files.readString(manifest))
            .contains(ownedId.toString())
            .contains(adoptingId.toString())
            .contains("\"version\":2");
    }

    @Test
    void movingDomainsRebuildsAndRemovesThePreviouslyOwnedSchemaInOnePromotion(@TempDir Path projectDir)
        throws IOException {
        UUID id = UUID.randomUUID();
        GovIndicatorDefinition indicator = atomic(id, "GMV", "GMV");
        indicator.setDomain("sales");
        when(repository.findById(id)).thenReturn(Optional.of(indicator));
        when(repository.findByDomainIgnoreCase(any())).thenAnswer(invocation ->
            invocation.getArgument(0, String.class).equals(indicator.getDomain())
                ? List.of(indicator)
                : List.of()
        );
        DbtIndicatorGenerator localGenerator = newGenerator(projectDir);

        localGenerator.generateAndRun(List.of(id));
        Path oldSchema = projectDir.resolve("models/ads/sales/sales_indicators_schema.yml");
        assertThat(oldSchema).exists();

        indicator.setDomain("finance");
        localGenerator.generateAndRun(List.of(id));

        assertThat(projectDir.resolve("models/ads/sales/ind_GMV.sql")).doesNotExist();
        assertThat(oldSchema).doesNotExist();
        assertThat(projectDir.resolve("models/ads/finance/ind_GMV.sql")).exists();
        assertThat(
            Files.readString(
                projectDir.resolve("models/ads/finance/finance_indicators_schema.yml")
            )
        )
            .contains("- name: ind_GMV");
        assertThat(
            Files.readString(projectDir.resolve("models/ads/.dts-indicator-artifacts.json"))
        )
            .contains("\"domain\":\"finance\"")
            .contains(
                "\"schemaPath\":\"models/ads/finance/finance_indicators_schema.yml\""
            )
            .doesNotContain("\"domain\":\"sales\"");
    }

    @Test
    void schemaStageFailureRollsBackSqlAndManifestFromTheSamePromotion(@TempDir Path projectDir)
        throws IOException {
        UUID id = UUID.randomUUID();
        GovIndicatorDefinition indicator = atomic(id, "GMV", "GMV");
        indicator.setDomain("sales");
        when(repository.findById(id)).thenReturn(Optional.of(indicator));
        Path modelDir = projectDir.resolve("models/ads/sales");
        Files.createDirectories(modelDir);
        Path model = modelDir.resolve("ind_GMV.sql");
        Files.writeString(model, "-- 自动生成: Old GMV (GMV)\nSELECT 1");
        Path blockedSchema = modelDir.resolve("sales_indicators_schema.yml");
        Files.createDirectories(blockedSchema);
        Files.writeString(blockedSchema.resolve("keep.txt"), "do not replace");

        assertThatThrownBy(() -> newGenerator(projectDir).generateAndRun(List.of(id)))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("dbt");

        assertThat(model).hasContent("-- 自动生成: Old GMV (GMV)\nSELECT 1");
        assertThat(blockedSchema.resolve("keep.txt")).hasContent("do not replace");
        assertThat(projectDir.resolve("models/ads/.dts-indicator-artifacts.json")).doesNotExist();
        verify(repository, never()).save(indicator);
    }

    @Test
    void springDatabaseRollbackRestoresSqlSchemaAndManifestBeforeReleasingTheLock(@TempDir Path projectDir)
        throws IOException {
        UUID id = UUID.randomUUID();
        GovIndicatorDefinition indicator = atomic(id, "GMV", "Original GMV");
        indicator.setDomain("sales");
        when(repository.findById(id)).thenReturn(Optional.of(indicator));
        when(repository.findByDomainIgnoreCase(any())).thenAnswer(invocation ->
            invocation.getArgument(0, String.class).equals(indicator.getDomain())
                ? List.of(indicator)
                : List.of()
        );
        DbtIndicatorGenerator localGenerator = newGenerator(projectDir);
        localGenerator.generateAndRun(List.of(id));

        Path oldSql = projectDir.resolve("models/ads/sales/ind_GMV.sql");
        Path oldSchema = projectDir.resolve("models/ads/sales/sales_indicators_schema.yml");
        Path manifest = projectDir.resolve("models/ads/.dts-indicator-artifacts.json");
        String oldSqlContent = Files.readString(oldSql);
        String oldSchemaContent = Files.readString(oldSchema);
        String oldManifestContent = Files.readString(manifest);

        indicator.setName("Moved GMV");
        indicator.setDomain("finance");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            localGenerator.generateAndRun(List.of(id));
            assertThat(oldSql).doesNotExist();
            assertThat(projectDir.resolve("models/ads/finance/ind_GMV.sql")).exists();
            try (var paths = Files.list(projectDir)) {
                assertThat(paths.map(path -> path.getFileName().toString()))
                    .anyMatch(name -> name.startsWith(".indicator-stage-"));
            }

            completeTestTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);
        } finally {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                completeTestTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
        }

        assertThat(oldSql).hasContent(oldSqlContent);
        assertThat(oldSchema).hasContent(oldSchemaContent);
        assertThat(manifest).hasContent(oldManifestContent);
        assertThat(projectDir.resolve("models/ads/finance/ind_GMV.sql")).doesNotExist();
        assertThat(projectDir.resolve("models/ads/finance/finance_indicators_schema.yml"))
            .doesNotExist();
        try (var paths = Files.list(projectDir)) {
            assertThat(paths.map(path -> path.getFileName().toString()))
                .noneMatch(name -> name.startsWith(".indicator-stage-"));
        }
    }

    @Test
    void subsetSchemaGenerationRetainsOtherIndicatorsInTheSameDomain(@TempDir Path projectDir)
        throws IOException {
        GovIndicatorDefinition requested = atomic(UUID.randomUUID(), "GMV", "GMV");
        requested.setDomain("sales");
        GovIndicatorDefinition existing = atomic(UUID.randomUUID(), "ORDER_COUNT", "Order count");
        existing.setDomain("sales");
        when(repository.findById(requested.getId())).thenReturn(Optional.of(requested));
        when(repository.findByDomainIgnoreCase("sales")).thenReturn(List.of(requested, existing));

        newGenerator(projectDir).generateSchemaYml(List.of(requested.getId()));

        String schema = Files.readString(
            projectDir.resolve("models/ads/sales/sales_indicators_schema.yml")
        );
        assertThat(schema)
            .contains("- name: ind_GMV")
            .contains("- name: ind_ORDER_COUNT");
    }

    @Test
    void generalSchemaGenerationRetainsNullAndBlankDomainIndicators(@TempDir Path projectDir)
        throws IOException {
        GovIndicatorDefinition requested = atomic(UUID.randomUUID(), "GMV", "GMV");
        requested.setDomain(null);
        GovIndicatorDefinition blankDomain = atomic(UUID.randomUUID(), "ORDER_COUNT", "Order count");
        blankDomain.setDomain(" ");
        GovIndicatorDefinition explicitGeneral = atomic(UUID.randomUUID(), "CUSTOMER_COUNT", "Customer count");
        explicitGeneral.setDomain("general");
        GovIndicatorDefinition otherDomain = atomic(UUID.randomUUID(), "COST", "Cost");
        otherDomain.setDomain("finance");
        when(repository.findById(requested.getId())).thenReturn(Optional.of(requested));
        when(repository.findByDomainIgnoreCase("general")).thenReturn(List.of(explicitGeneral));
        when(repository.findAll()).thenReturn(List.of(requested, blankDomain, explicitGeneral, otherDomain));

        newGenerator(projectDir).generateSchemaYml(List.of(requested.getId()));

        String schema = Files.readString(
            projectDir.resolve("models/ads/general/general_indicators_schema.yml")
        );
        assertThat(schema)
            .contains("- name: ind_GMV")
            .contains("- name: ind_ORDER_COUNT")
            .contains("- name: ind_CUSTOMER_COUNT")
            .doesNotContain("- name: ind_COST");
    }

    @Test
    void refusesToWriteAnOwnershipManifestThatTheNextRunCannotRead(@TempDir Path projectDir)
        throws Exception {
        Path manifest = projectDir.resolve("models/ads/.dts-indicator-artifacts.json");
        Files.createDirectories(manifest.getParent());
        Map<String, Object> owned = new LinkedHashMap<>();
        for (int index = 0; index < 400; index++) {
            owned.put(
                UUID.randomUUID().toString(),
                Map.of(
                    "modelPath",
                    "models/ads/sales/ind_EXISTING_" + index + ".sql"
                )
            );
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        root.put("indicators", owned);
        String prefix = objectMapper.writeValueAsString(root);
        int targetLength = 65_520;
        int paddingLength = targetLength - prefix.length() - 120;
        assertThat(paddingLength).isPositive();
        String paddingId = UUID.randomUUID().toString();
        owned.put(
            paddingId,
            Map.of(
                "modelPath",
                "models/ads/sales/ind_PAD_" + "A".repeat(paddingLength) + ".sql"
            )
        );
        String seededManifest = objectMapper.writeValueAsString(root);
        int adjustment = targetLength - seededManifest.length();
        owned.put(
            paddingId,
            Map.of(
                "modelPath",
                "models/ads/sales/ind_PAD_" + "A".repeat(paddingLength + adjustment) + ".sql"
            )
        );
        seededManifest = objectMapper.writeValueAsString(root);
        assertThat(seededManifest.length()).isEqualTo(targetLength);
        Files.writeString(manifest, seededManifest);

        GovIndicatorDefinition indicator = atomic(UUID.randomUUID(), "GMV", "GMV");
        indicator.setDomain("sales");
        when(repository.findById(indicator.getId())).thenReturn(Optional.of(indicator));

        assertThatThrownBy(() -> newGenerator(projectDir).generate(indicator.getId()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("manifest")
            .hasMessageContaining("长度");

        assertThat(Files.readString(manifest)).isEqualTo(seededManifest);
        assertThat(projectDir.resolve("models/ads/sales/ind_GMV.sql")).doesNotExist();
        verify(repository, never()).save(indicator);
    }

    @Test
    void failedPromotionCannotRollbackAnotherGenerationCommittedConcurrently(@TempDir Path projectDir)
        throws Exception {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        GovIndicatorDefinition first = atomic(firstId, "FIRST", "First");
        GovIndicatorDefinition second = atomic(secondId, "SECOND", "Second");
        first.setDomain("sales");
        second.setDomain("sales");
        when(repository.findById(firstId)).thenReturn(Optional.of(first));
        when(repository.findById(secondId)).thenReturn(Optional.of(second));

        CountDownLatch firstSaveEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstSave = new CountDownLatch(1);
        CountDownLatch secondSaved = new CountDownLatch(1);
        when(repository.save(any(GovIndicatorDefinition.class))).thenAnswer(invocation -> {
            GovIndicatorDefinition definition = invocation.getArgument(0);
            if (firstId.equals(definition.getId())) {
                firstSaveEntered.countDown();
                if (!releaseFirstSave.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("timed out waiting to release first save");
                }
                throw new IllegalStateException("first save failed");
            }
            secondSaved.countDown();
            return definition;
        });

        DbtIndicatorGenerator localGenerator = newGenerator(projectDir);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> firstResult = null;
        Future<?> secondResult = null;
        boolean secondCommittedBeforeRelease;
        try {
            firstResult = executor.submit(() -> localGenerator.generate(firstId));
            assertThat(firstSaveEntered.await(5, TimeUnit.SECONDS)).isTrue();
            secondResult = executor.submit(() -> localGenerator.generate(secondId));
            secondCommittedBeforeRelease = secondSaved.await(300, TimeUnit.MILLISECONDS);
        } finally {
            releaseFirstSave.countDown();
        }
        try {
            Future<?> completedFirst = firstResult;
            Future<?> completedSecond = secondResult;
            assertThatThrownBy(() -> completedFirst.get(5, TimeUnit.SECONDS))
                .hasRootCauseMessage("first save failed");
            completedSecond.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(secondCommittedBeforeRelease).isFalse();
        String manifest = Files.readString(
            projectDir.resolve("models/ads/.dts-indicator-artifacts.json")
        );
        assertThat(manifest)
            .contains(secondId.toString())
            .doesNotContain(firstId.toString());
        assertThat(projectDir.resolve("models/ads/sales/ind_SECOND.sql")).exists();
        assertThat(projectDir.resolve("models/ads/sales/ind_FIRST.sql")).doesNotExist();
    }

    @Test
    void previewNeverCreatesProjectArtifacts(@TempDir Path projectDir) {
        UUID id = UUID.randomUUID();
        GovIndicatorDefinition indicator = atomic(id, "GMV", "GMV");
        when(repository.findById(id)).thenReturn(Optional.of(indicator));

        newGenerator(projectDir).previewSql(id);

        assertThat(projectDir).isEmptyDirectory();
    }

    private void assertPreviewRejected(GovIndicatorDefinition definition, String messagePart) {
        when(repository.findById(definition.getId())).thenReturn(Optional.of(definition));
        assertThatThrownBy(() -> generator.previewSql(definition.getId()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(messagePart);
    }

    private DbtIndicatorGenerator newGenerator(Path projectDir) {
        DbtProperties properties = new DbtProperties();
        properties.setProjectDir(projectDir.toString());
        return new DbtIndicatorGenerator(
            repository,
            properties,
            objectMapper,
            compiler,
            new IndicatorDerivationValidationService(repository, compiler, objectMapper)
        );
    }

    private static void completeTestTransaction(int status) {
        try {
            for (TransactionSynchronization synchronization :
                TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(status);
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private static GovIndicatorDefinition indicator(UUID id, String code, String dimensions) {
        GovIndicatorDefinition value = new GovIndicatorDefinition();
        value.setId(id);
        value.setCode(code);
        value.setName(code);
        value.setStatus("PUBLISHED");
        value.setIsDerived(true);
        value.setDimensionFields(dimensions);
        value.setTimeGrain("MONTH");
        return value;
    }

    private static GovIndicatorDefinition atomic(UUID id, String code, String name) {
        GovIndicatorDefinition value = new GovIndicatorDefinition();
        value.setId(id);
        value.setCode(code);
        value.setName(name);
        value.setStatus("PUBLISHED");
        value.setIsDerived(false);
        value.setSourceTable("fact_orders");
        value.setMeasureField("amount");
        value.setDateColumn("created_at");
        return value;
    }
}
