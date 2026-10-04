package com.yuzhi.dts.copilot.analytics.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsReportTemplate;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsReportTemplateRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.ReportTemplateCatalogService;
import com.yuzhi.dts.copilot.analytics.service.report.FixedReportPageAnchorService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class ReportTemplateCatalogResourceTest {

    private static final FixedReportPageAnchorService PAGE_ANCHOR_SERVICE = new FixedReportPageAnchorService();

    @Mock
    private AnalyticsSessionService sessionService;

    @Mock
    private AnalyticsReportTemplateRepository templateRepository;

    @Test
    void listTemplatesShouldRequireAuthenticatedSession() {
        ReportTemplateCatalogService service =
                new ReportTemplateCatalogService(templateRepository, PAGE_ANCHOR_SERVICE);
        ReportTemplateCatalogResource resource = new ReportTemplateCatalogResource(sessionService, service);

        ResponseEntity<?> response = resource.listTemplates(null, null, 100, null);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void listTemplatesShouldReturnOnlyCertifiedPublishedActiveTemplatesByDefault() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(sessionService.resolveUser(request)).thenReturn(Optional.of(buildUser()));
        stubCatalogTemplates(List.of(
                row(
                        101L,
                        "应收总览看板",
                        "财务",
                        "KPI",
                        "FIN-01",
                        "VIEW",
                        "v_monthly_settlement",
                        "real-time",
                        "certified",
                        true,
                        false,
                        Instant.parse("2026-03-20T08:00:00Z")),
                row(
                        102L,
                        "客户欠款排行",
                        "财务",
                        "RANK",
                        "FIN-02",
                        "MART",
                        "mart_finance_ar_rank",
                        "5m",
                        "certified",
                        false,
                        false,
                        Instant.parse("2026-03-20T09:00:00Z")),
                row(
                        103L,
                        "采购申请待办",
                        "采购",
                        "DETAIL",
                        "PROC-01",
                        "VIEW",
                        "v_procurement_todo",
                        "real-time",
                        "certified",
                        false,
                        false,
                        Instant.parse("2026-03-20T10:00:00Z")),
                row(
                        104L,
                        "库存现量看板",
                        "仓库",
                        "KPI",
                        "WH-01",
                        "VIEW",
                        "v_inventory_current",
                        "real-time",
                        "draft",
                        true,
                        false,
                        Instant.parse("2026-03-20T11:00:00Z"))));

        ReportTemplateCatalogService service =
                new ReportTemplateCatalogService(templateRepository, PAGE_ANCHOR_SERVICE);
        ReportTemplateCatalogResource resource = new ReportTemplateCatalogResource(sessionService, service);

        ResponseEntity<?> response = resource.listTemplates(null, null, 100, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) response.getBody();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("templateCode", "FIN-01");
        assertThat(rows.get(0)).containsEntry("certificationStatus", "certified");
        assertThat(rows.get(0)).containsEntry("published", true);
    }

    @Test
    void listTemplatesShouldFilterByDomainCategoryAndLimit() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(sessionService.resolveUser(request)).thenReturn(Optional.of(buildUser()));
        stubCatalogTemplates(List.of(
                row(201L, "应收总览看板", "财务", "KPI", "FIN-01", "VIEW", "v_monthly_settlement", "real-time", "certified", true, false, Instant.parse("2026-03-20T08:00:00Z")),
                row(202L, "客户欠款排行", "财务", "RANK", "FIN-02", "MART", "mart_finance_ar_rank", "5m", "certified", true, false, Instant.parse("2026-03-20T09:00:00Z")),
                row(203L, "采购申请待办", "采购", "DETAIL", "PROC-01", "VIEW", "v_procurement_todo", "real-time", "certified", true, false, Instant.parse("2026-03-20T10:00:00Z"))));

        ReportTemplateCatalogService service =
                new ReportTemplateCatalogService(templateRepository, PAGE_ANCHOR_SERVICE);
        ReportTemplateCatalogResource resource = new ReportTemplateCatalogResource(sessionService, service);

        ResponseEntity<?> response = resource.listTemplates("财务", "RANK", 1, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) response.getBody();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("templateCode", "FIN-02");
    }

    @Test
    void listTemplatesShouldKeepCertifiedRowsWhenLaterDraftNoiseExists() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(sessionService.resolveUser(request)).thenReturn(Optional.of(buildUser()));
        List<AnalyticsReportTemplateRow> rows = new ArrayList<>();
        rows.add(row(
                301L,
                "应收总览看板",
                "财务",
                "KPI",
                "FIN-01",
                "VIEW",
                "v_monthly_settlement",
                "real-time",
                "certified",
                true,
                false,
                Instant.parse("2026-03-20T08:00:00Z")));
        for (int i = 0; i < 20; i++) {
            rows.add(row(
                    400L + i,
                    "草稿报表-" + i,
                    "财务",
                    i % 2 == 0 ? "KPI" : "RANK",
                    "DRAFT-" + i,
                    "VIEW",
                    "v_noise_" + i,
                    "real-time",
                    i % 2 == 0 ? "draft" : "review",
                    false,
                    false,
                    Instant.parse("2026-03-20T09:" + String.format(Locale.ROOT, "%02d", i) + ":00Z")));
        }
        stubCatalogTemplates(rows);

        ReportTemplateCatalogService service =
                new ReportTemplateCatalogService(templateRepository, PAGE_ANCHOR_SERVICE);
        ReportTemplateCatalogResource resource = new ReportTemplateCatalogResource(sessionService, service);

        ResponseEntity<?> response = resource.listTemplates(null, null, 1, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> responseRows = (List<Map<String, Object>>) response.getBody();
        assertThat(responseRows).hasSize(1);
        assertThat(responseRows.get(0)).containsEntry("templateCode", "FIN-01");
    }

    @Test
    void getTemplateShouldResolveLatestPublishedCertifiedVersionByTemplateCode() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(sessionService.resolveUser(request)).thenReturn(Optional.of(buildUser()));
        when(templateRepository.findLatestRunnableTemplateByTemplateCode("fin-ar-overview"))
                .thenReturn(Optional.of(toEntity(row(
                        501L,
                        "应收总览看板",
                        "财务",
                        "KPI",
                        "FIN-AR-OVERVIEW",
                        "VIEW",
                        "authority.finance.receivable_overview",
                        "REALTIME",
                        "certified",
                        true,
                        false,
                        Instant.parse("2026-03-20T10:00:00Z")))));

        ReportTemplateCatalogService service =
                new ReportTemplateCatalogService(templateRepository, PAGE_ANCHOR_SERVICE);
        ReportTemplateCatalogResource resource = new ReportTemplateCatalogResource(sessionService, service);

        ResponseEntity<?> response = resource.getTemplate("FIN-AR-OVERVIEW", request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> row = (Map<String, Object>) response.getBody();
        assertThat(row).containsEntry("templateCode", "FIN-AR-OVERVIEW");
        assertThat(row).containsEntry("targetObject", "authority.finance.receivable_overview");
    }

    @Test
    void getTemplateShouldExposeLegacyPageAnchorForKnownTemplate() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(sessionService.resolveUser(request)).thenReturn(Optional.of(buildUser()));
        when(templateRepository.findLatestRunnableTemplateByTemplateCode("fin-ar-overview"))
                .thenReturn(Optional.of(toEntity(row(
                        511L,
                        "财务结算汇总",
                        "财务",
                        "KPI",
                        "FIN-AR-OVERVIEW",
                        "VIEW",
                        "authority.finance.receivable_overview",
                        "REALTIME",
                        "certified",
                        true,
                        false,
                        Instant.parse("2026-03-20T10:00:00Z")))));

        ReportTemplateCatalogService service =
                new ReportTemplateCatalogService(templateRepository, PAGE_ANCHOR_SERVICE);
        ReportTemplateCatalogResource resource = new ReportTemplateCatalogResource(sessionService, service);

        ResponseEntity<?> response = resource.getTemplate("FIN-AR-OVERVIEW", request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> row = (Map<String, Object>) response.getBody();
        assertThat(row).containsEntry("legacyPagePath", "/operate/settlement");
        assertThat(row).containsEntry("legacyPageTitle", "财务结算");
    }

    @Test
    void getTemplateShouldExposePlaceholderReviewFlagFromSpecJson() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(sessionService.resolveUser(request)).thenReturn(Optional.of(buildUser()));

        AnalyticsReportTemplate entity = new AnalyticsReportTemplate();
        entity.setTemplateCode("PROC-PURCHASE-REQUEST-TODO");
        entity.setName("采购申请待办");
        entity.setDescription("desc-PROC-PURCHASE-REQUEST-TODO");
        entity.setDomain("采购");
        entity.setCategory("待办");
        entity.setDataSourceType("VIEW");
        entity.setTargetObject("authority.procurement.request_todo");
        entity.setRefreshPolicy("REALTIME");
        entity.setCertificationStatus("certified");
        entity.setPublished(true);
        entity.setArchived(false);
        entity.setSpecJson("""
                {"placeholderReviewRequired":true}
                """);
        entity.setCreatorId(1L);
        try {
            var idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, 601L);
            var updatedField = entity.getClass().getDeclaredField("updatedAt");
            updatedField.setAccessible(true);
            updatedField.set(entity, Instant.parse("2026-03-20T10:00:00Z"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(templateRepository.findLatestRunnableTemplateByTemplateCode("proc-purchase-request-todo"))
                .thenReturn(Optional.of(entity));

        ReportTemplateCatalogService service =
                new ReportTemplateCatalogService(templateRepository, PAGE_ANCHOR_SERVICE);
        ReportTemplateCatalogResource resource = new ReportTemplateCatalogResource(sessionService, service);

        ResponseEntity<?> response = resource.getTemplate("PROC-PURCHASE-REQUEST-TODO", request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> row = (Map<String, Object>) response.getBody();
        assertThat(row).containsEntry("templateCode", "PROC-PURCHASE-REQUEST-TODO");
        assertThat(row).containsEntry("placeholderReviewRequired", true);
    }

    @Test
    void getTemplateShouldExposeUnifiedDbtAssetMetadata() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(sessionService.resolveUser(request)).thenReturn(Optional.of(buildUser()));

        AnalyticsReportTemplate entity = toEntity(row(
                701L,
                "PRS 项目经营 TOP",
                "PRS租赁",
                "项目客户-TOP",
                "PRS-FLOWERBIZ-PROJECT-CUSTOMER-TOP",
                "DBT_ADS",
                "public.xycyl_ads_flowerbiz_project_customer",
                "DBT_BUILD",
                "certified",
                true,
                false,
                Instant.parse("2026-05-29T08:00:00Z")));
        entity.setSpecJson("""
                {
                  "assetKind":"DBT_SPLIT",
                  "assetGroup":{"code":"PRS-FLOWERBIZ-PROJECT-CUSTOMER","name":"PRS 项目客户经营","parentTemplateCode":"PRS-FLOWERBIZ-PROJECT-CUSTOMER"},
                  "queryContract":{
                    "sourceType":"DBT_ADS",
                    "targetObject":"public.xycyl_ads_flowerbiz_project_customer",
                    "primaryDbtModel":"public.xycyl_ads_flowerbiz_project_customer",
                    "dbtModels":["public.xycyl_ads_flowerbiz_project_customer"],
                    "outputColumns":["项目","客户","租金净额"]
                  },
                  "placeholderReviewRequired":false
                }
                """);

        when(templateRepository.findLatestRunnableTemplateByTemplateCode("prs-flowerbiz-project-customer-top"))
                .thenReturn(Optional.of(entity));

        ReportTemplateCatalogService service =
                new ReportTemplateCatalogService(templateRepository, PAGE_ANCHOR_SERVICE);
        ReportTemplateCatalogResource resource = new ReportTemplateCatalogResource(sessionService, service);

        ResponseEntity<?> response = resource.getTemplate("PRS-FLOWERBIZ-PROJECT-CUSTOMER-TOP", request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> row = (Map<String, Object>) response.getBody();
        assertThat(row).containsEntry("assetKind", "DBT_SPLIT");
        assertThat(row).containsEntry("assetGroupCode", "PRS-FLOWERBIZ-PROJECT-CUSTOMER");
        assertThat(row).containsEntry("assetGroupName", "PRS 项目客户经营");
        assertThat(row).containsEntry("parentTemplateCode", "PRS-FLOWERBIZ-PROJECT-CUSTOMER");
        assertThat(row).containsEntry("primaryDbtModel", "public.xycyl_ads_flowerbiz_project_customer");
        assertThat(row).containsEntry("outputColumnCount", 3);
        assertThat(row.get("sourceRefs")).isEqualTo(List.of("dbt-model:public.xycyl_ads_flowerbiz_project_customer"));
    }

    private void stubCatalogTemplates(List<AnalyticsReportTemplateRow> rows) {
        when(templateRepository.findCatalogTemplates(any(), any(), any())).thenAnswer(invocation -> {
            String domain = invocation.getArgument(0);
            String category = invocation.getArgument(1);
            Pageable pageable = invocation.getArgument(2);

            return rows.stream()
                    .map(ReportTemplateCatalogResourceTest::toEntity)
                    .filter(template -> !template.isArchived())
                    .filter(AnalyticsReportTemplate::isPublished)
                    .filter(template -> "certified".equalsIgnoreCase(template.getCertificationStatus()))
                    .filter(template -> matches(domain, template.getDomain()))
                    .filter(template -> matches(category, template.getCategory()))
                    .sorted(Comparator.comparing(AnalyticsReportTemplate::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .skip(pageable.getOffset())
                    .limit(pageable.getPageSize())
                    .toList();
        });
    }

    private static boolean matches(String requested, String candidate) {
        if (requested == null) {
            return true;
        }
        return candidate != null && candidate.trim().toLowerCase(Locale.ROOT).equals(requested.trim().toLowerCase(Locale.ROOT));
    }

    private static AnalyticsUser buildUser() {
        AnalyticsUser user = new AnalyticsUser();
        user.setId(1L);
        user.setUsername("alice");
        user.setActive(true);
        user.setSuperuser(true);
        return user;
    }

    private static AnalyticsReportTemplateRow row(
            Long id,
            String name,
            String domain,
            String category,
            String templateCode,
            String dataSourceType,
            String targetObject,
            String refreshPolicy,
            String certificationStatus,
            boolean published,
            boolean archived,
            Instant updatedAt) {
        return new AnalyticsReportTemplateRow(id, name, domain, category, templateCode, dataSourceType, targetObject, refreshPolicy, certificationStatus, published, archived, updatedAt);
    }

    private static AnalyticsReportTemplate toEntity(AnalyticsReportTemplateRow row) {
        AnalyticsReportTemplate entity = new AnalyticsReportTemplate();
        entity.setTemplateCode(row.templateCode());
        entity.setName(row.name());
        entity.setDescription("desc-" + row.templateCode());
        entity.setDomain(row.domain());
        entity.setCategory(row.category());
        entity.setDataSourceType(row.dataSourceType());
        entity.setTargetObject(row.targetObject());
        entity.setRefreshPolicy(row.refreshPolicy());
        entity.setCertificationStatus(row.certificationStatus());
        entity.setPublished(row.published());
        entity.setArchived(row.archived());
        entity.setSpecJson("{}");
        entity.setCreatorId(1L);
        try {
            var idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, row.id());
            var updatedField = entity.getClass().getDeclaredField("updatedAt");
            updatedField.setAccessible(true);
            updatedField.set(entity, row.updatedAt());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return entity;
    }

    private record AnalyticsReportTemplateRow(
            Long id,
            String name,
            String domain,
            String category,
            String templateCode,
            String dataSourceType,
            String targetObject,
            String refreshPolicy,
            String certificationStatus,
            boolean published,
            boolean archived,
            Instant updatedAt) {}
}
