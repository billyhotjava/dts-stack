package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

import com.yuzhi.dts.platform.domain.catalog.CatalogColumnLineage;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage;
import com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnLineageRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetLineageRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogLineageJobRepository;
import com.yuzhi.dts.platform.repository.infra.InfraOdsTableMappingRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.lineage.IngestionLineageWriter;
import com.yuzhi.dts.platform.service.etl.OdsTableMappingSyncService;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 影响分析接口的首次契约测试（补账本#21 空白）。
 * 覆盖：字段血缘按快照时刻过滤（通过 repository 调用契约验证）、
 * columnLineageSnapshotAt 与 snapshotAt 同值、表级与字段级同一时刻语义。
 */
@ExtendWith(MockitoExtension.class)
class CatalogLineageResourceImpactTest {

    @Mock private CatalogDatasetRepository datasetRepository;
    @Mock private CatalogDatasetLineageRepository lineageRepository;
    @Mock private CatalogColumnLineageRepository columnLineageRepository;
    @Mock private CatalogLineageJobRepository lineageJobRepository;
    @Mock private InfraOdsTableMappingRepository mappingRepository;
    @Mock private InfraDataSourceRepository dataSourceRepository;
    @Mock private IngestionLineageWriter lineageWriter;
    @Mock private OdsTableMappingSyncService mappingSyncService;
    @Mock private AccessChecker accessChecker;
    @Mock private AuditService auditService;

    private CatalogLineageResource resource;

    private final UUID rootId = UUID.randomUUID();
    private final UUID upId = UUID.randomUUID();
    private final UUID downId = UUID.randomUUID();
    private final UUID edgeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        resource = new CatalogLineageResource(
            datasetRepository,
            lineageRepository,
            columnLineageRepository,
            lineageJobRepository,
            mappingRepository,
            dataSourceRepository,
            lineageWriter,
            mappingSyncService,
            accessChecker,
            auditService
        );
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("tester", null, List.of(new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN)))
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private CatalogDataset dataset(UUID id) {
        CatalogDataset ds = new CatalogDataset();
        ds.setId(id);
        return ds;
    }

    private CatalogDatasetLineage edge() {
        CatalogDatasetLineage edge = new CatalogDatasetLineage();
        edge.setId(edgeId);
        edge.setUpstreamDatasetId(upId);
        edge.setDownstreamDatasetId(rootId);
        edge.setRelationType("DBT");
        edge.setDirection("UPSTREAM_TO_DOWNSTREAM");
        edge.setValidFrom(Instant.parse("2026-01-01T00:00:00Z"));
        edge.setValidTo(Instant.parse("2030-01-01T00:00:00Z"));
        return edge;
    }

    private CatalogColumnLineage column(UUID id, String upColumn, String downColumn, Instant validTo) {
        CatalogColumnLineage col = new CatalogColumnLineage();
        col.setId(id);
        col.setDatasetLineageId(edgeId);
        col.setUpstreamDatasetId(upId);
        col.setDownstreamDatasetId(rootId);
        col.setUpstreamColumn(upColumn);
        col.setDownstreamColumn(downColumn);
        col.setRelationType("DBT");
        col.setLineageType("PARSED");
        col.setValidFrom(Instant.parse("2026-01-01T00:00:00Z"));
        col.setValidTo(validTo);
        return col;
    }

    @Test
    void impactColumnLineagesAreLoadedAtTheSameSnapshotAsTableEdges() {
        Instant at = Instant.parse("2026-08-01T00:00:00Z");
        CatalogColumnLineage valid = column(UUID.randomUUID(), "id", "id", Instant.parse("2030-01-01T00:00:00Z"));
        when(datasetRepository.findById(rootId)).thenReturn(Optional.of(dataset(rootId)));
        when(accessChecker.canRead(any())).thenReturn(true);
        when(accessChecker.departmentAllowed(any(), any())).thenReturn(true);
        when(lineageRepository.findByEitherSideAt(eq(rootId), eq(at))).thenReturn(List.of(edge()));
        when(columnLineageRepository.findByDatasetLineageIdInAt(any(), eq(at))).thenReturn(List.of(valid));

        Map<String, Object> payload = resource.impact(
            rootId,
            "UPSTREAM",
            2,
            null,
            null,
            null,
            null,
            true,
            true,
            at.toString(),
            null
        ).getData();

        assertThat(payload.get("snapshotAt")).isEqualTo(at.toString());
        assertThat(payload.get("columnLineageSnapshotAt")).isEqualTo(at.toString());
        // 字段血缘只来自同一时刻的 repository 查询——已失效行由 findByDatasetLineageIdInAt 过滤
        assertThat(payload.get("columnLineages"))
            .isInstanceOfSatisfying(List.class, rows -> assertThat(rows).hasSize(1));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<UUID>> captor = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(columnLineageRepository).findByDatasetLineageIdInAt(captor.capture(), eq(at));
        assertThat(captor.getValue()).containsExactly(edgeId);
    }

    @Test
    void impactWithoutColumnsDoesNotQueryColumnLineageRepository() {
        Instant at = Instant.parse("2026-08-01T00:00:00Z");
        when(datasetRepository.findById(rootId)).thenReturn(Optional.of(dataset(rootId)));
        when(accessChecker.canRead(any())).thenReturn(true);
        when(accessChecker.departmentAllowed(any(), any())).thenReturn(true);
        when(lineageRepository.findByEitherSideAt(eq(rootId), eq(at))).thenReturn(List.of(edge()));

        Map<String, Object> payload = resource.impact(
            rootId,
            "UPSTREAM",
            2,
            null,
            null,
            null,
            null,
            false,
            false,
            at.toString(),
            null
        ).getData();

        assertThat(payload.get("columnLineages")).isInstanceOfSatisfying(List.class, rows -> assertThat(rows).isEmpty());
        org.mockito.Mockito.verify(columnLineageRepository, org.mockito.Mockito.never())
            .findByDatasetLineageIdIn(any());
        org.mockito.Mockito.verify(columnLineageRepository, org.mockito.Mockito.never())
            .findByDatasetLineageIdInAt(any(), any());
    }
}
