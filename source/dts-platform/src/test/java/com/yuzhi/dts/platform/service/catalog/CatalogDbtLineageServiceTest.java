package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetLineageRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogLineageJobRepository;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class CatalogDbtLineageServiceTest {

    @Mock
    private CatalogDatasetRepository datasetRepository;

    @Mock
    private CatalogDatasetLineageRepository lineageRepository;

    @Mock
    private CatalogLineageJobRepository lineageJobRepository;

    @Mock
    private CatalogClassificationPropagationJobService propagationJobService;

    @Test
    void manifestImportWritesDbtLineageAndEnqueuesIdempotentClassificationPropagation() throws Exception {
        UUID upstreamId = UUID.randomUUID();
        UUID downstreamId = UUID.randomUUID();
        when(datasetRepository.findSchemaTableAndIdProjection())
            .thenReturn(
                List.of(
                    new Object[] { "ods", "source_orders", upstreamId },
                    new Object[] { "dwd", "dwd_orders", downstreamId }
                )
            );
        when(lineageRepository.findCurrentDbtPairs()).thenReturn(List.of());
        when(lineageJobRepository.findByJobKey("DBT:model.s72.dwd_orders"))
            .thenReturn(Optional.empty());
        when(lineageJobRepository.save(any(CatalogLineageJob.class))).thenAnswer(invocation -> {
            CatalogLineageJob job = invocation.getArgument(0);
            job.setId(UUID.randomUUID());
            return job;
        });
        when(propagationJobService.enqueue(
                org.mockito.ArgumentMatchers.eq(downstreamId),
                org.mockito.ArgumentMatchers.eq("DBT"),
                org.mockito.ArgumentMatchers.startsWith("dbt-manifest:")
            ))
            .thenReturn(true);
        MockMultipartFile manifest = new MockMultipartFile(
            "file",
            "manifest.json",
            "application/json",
            """
            {
              "nodes":{
                "model.s72.dwd_orders":{
                  "name":"dwd_orders",
                  "schema":"dwd",
                  "original_file_path":"models/dwd_orders.sql",
                  "depends_on":{"nodes":["source.s72.source_orders"]}
                }
              }
            }
            """.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        var result = service().importManifest(manifest);

        assertThat(result)
            .containsEntry("created", 1)
            .containsEntry("propagationEnqueued", 1)
            .containsEntry("truncated", false);
        @SuppressWarnings("unchecked")
        Map<String, Object> reasons = (Map<String, Object>) result.get("skippedReasons");
        assertThat(reasons)
            .containsEntry("notModel", 0)
            .containsEntry("malformedNode", 0)
            .containsEntry("unmatchedModel", 0)
            .containsEntry("unmatchedParent", 0);
        ArgumentCaptor<List<com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage>> lineages =
            ArgumentCaptor.forClass(List.class);
        verify(lineageRepository).saveAll(lineages.capture());
        assertThat(lineages.getValue()).singleElement().satisfies(lineage -> {
            assertThat(lineage.getUpstreamDatasetId()).isEqualTo(upstreamId);
            assertThat(lineage.getDownstreamDatasetId()).isEqualTo(downstreamId);
            assertThat(lineage.getRelationType()).isEqualTo("DBT");
        });
        verify(propagationJobService).enqueue(
            org.mockito.ArgumentMatchers.eq(downstreamId),
            org.mockito.ArgumentMatchers.eq("DBT"),
            org.mockito.ArgumentMatchers.startsWith("dbt-manifest:")
        );
    }

    @Test
    void manifestImportBucketsSkipReasonsAndExposesUnmatchedDetails() throws Exception {
        when(datasetRepository.findSchemaTableAndIdProjection())
            .thenReturn(Collections.singletonList(new Object[] { "dwd", "dwd_orders", UUID.randomUUID() }));
        when(lineageRepository.findCurrentDbtPairs()).thenReturn(List.of());
        MockMultipartFile manifest = new MockMultipartFile(
            "file",
            "manifest.json",
            "application/json",
            """
            {
              "nodes":{
                "seed.s72.seed_customers":{"name":"seed_customers","schema":"ods"},
                "model.s72.broken":{"name":"broken","schema":"dwd","depends_on":"oops"},
                "model.s72.unknown_model":{"name":"unknown_model","schema":"dwd","depends_on":{"nodes":[]}},
                "model.s72.dwd_orders":{"name":"dwd_orders","schema":"dwd","depends_on":{"nodes":["model.s72.missing_parent"]}},
                "source.s72.raw_orders":{"name":"raw_orders","schema":"ods"}
              }
            }
            """.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        var result = service().importManifest(manifest);

        @SuppressWarnings("unchecked")
        Map<String, Object> reasons = (Map<String, Object>) result.get("skippedReasons");
        assertThat(reasons)
            .containsEntry("notModel", 2) // seed + source
            .containsEntry("malformedNode", 1) // depends_on 非 Map
            .containsEntry("unmatchedModel", 1) // unknown_model
            .containsEntry("unmatchedParent", 1); // missing_parent
        assertThat(result.get("truncated")).isEqualTo(false);
        assertThat(result.get("unmatched"))
            .isInstanceOfSatisfying(List.class, detail -> {
                assertThat(detail).hasSize(2);
                Map<?, ?> first = (Map<?, ?>) ((List<?>) detail).get(0);
                assertThat(first.get("uniqueId")).isEqualTo("model.s72.unknown_model");
                assertThat(first.get("reason")).asString().contains("未匹配");
            });
        // dwd_orders 命中目录资产但唯一父节点未匹配，边未建成
        assertThat(result).containsEntry("created", 0);
    }

    @Test
    void manifestImportMatchesBySchemaAndTableInsteadOfBareTableName() throws Exception {
        UUID upstreamA = UUID.randomUUID();
        UUID upstreamB = UUID.randomUUID();
        UUID downstream = UUID.randomUUID();
        // 两个库都有同表名 orders：ods.orders / dwd.orders —— 必须按 schema 区分
        when(datasetRepository.findSchemaTableAndIdProjection())
            .thenReturn(
                List.of(
                    new Object[] { "ods", "orders", upstreamA },
                    new Object[] { "dwd", "orders", upstreamB },
                    new Object[] { "dwd", "orders_agg", downstream }
                )
            );
        when(lineageRepository.findCurrentDbtPairs()).thenReturn(List.of());
        MockMultipartFile manifest = new MockMultipartFile(
            "file",
            "manifest.json",
            "application/json",
            """
            {
              "nodes":{
                "model.s72.orders_agg":{
                  "name":"orders_agg",
                  "schema":"dwd",
                  "depends_on":{"nodes":["source.s72.ods.orders","model.s72.dwd.orders"]}
                }
              }
            }
            """.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        var result = service().importManifest(manifest);

        assertThat(result).containsEntry("created", 2);
        ArgumentCaptor<List<com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage>> lineages =
            ArgumentCaptor.forClass(List.class);
        verify(lineageRepository).saveAll(lineages.capture());
        assertThat(lineages.getValue())
            .extracting(lineage -> lineage.getUpstreamDatasetId())
            .containsExactlyInAnyOrder(upstreamA, upstreamB);
    }

    private CatalogDbtLineageService service() {
        return new CatalogDbtLineageService(
            datasetRepository,
            lineageRepository,
            lineageJobRepository,
            new ObjectMapper(),
            propagationJobService
        );
    }
}
