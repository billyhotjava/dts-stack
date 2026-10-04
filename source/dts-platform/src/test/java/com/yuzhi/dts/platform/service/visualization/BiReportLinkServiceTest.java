package com.yuzhi.dts.platform.service.visualization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.visualization.BiReportLink;
import com.yuzhi.dts.platform.domain.visualization.BiReportVisit;
import com.yuzhi.dts.platform.repository.explore.QueryDatasetAssetRepository;
import com.yuzhi.dts.platform.repository.visualization.BiReportLinkRepository;
import com.yuzhi.dts.platform.repository.visualization.BiReportVisitRepository;
import com.yuzhi.dts.platform.security.ClassificationUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.integration.ScreenReportLinkSyncService;
import com.yuzhi.dts.platform.service.permission.DashboardAccessGuard;
import com.yuzhi.dts.platform.service.permission.DashboardAccessGuard.AccessDecision;
import com.yuzhi.dts.platform.service.permission.DashboardCallerResolver;
import com.yuzhi.dts.platform.service.visualization.dto.BiReportLinkDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class BiReportLinkServiceTest {

    @Mock
    private BiReportLinkRepository reportLinkRepository;

    @Mock
    private BiReportVisitRepository reportVisitRepository;

    @Mock
    private QueryDatasetAssetRepository queryDatasetAssetRepository;

    @Mock
    private ClassificationUtils classificationUtils;

    @Mock
    private DashboardAccessGuard accessGuard;

    @Mock
    private DashboardCallerResolver callerResolver;

    @Mock
    private AuditService audit;

    @InjectMocks
    private BiReportLinkService service;

    @AfterEach
    void cleanupSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listPublishedShouldFilterByGuardDecision() {
        // listPublished 已不再 inline 跑 classification/role/dept 三道 filter，
        // 这部分逻辑统一交给 DashboardAccessGuard。本测试只验证：
        //   service 把每个候选行问一次 Guard，Guard 拒绝的行不出现在结果里。
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "employee",
                "n/a",
                AuthorityUtils.createAuthorityList("ROLE_EMPLOYEE", "ROLE_INTERNAL")
            )
        );

        BiReportLink internal = buildLink("internal_dashboard", "INTERNAL", "ROLE_EMPLOYEE");
        BiReportLink secret = buildLink("secret_dashboard", "SECRET", "ROLE_EMPLOYEE");
        when(reportLinkRepository.findCandidatesForListing(anyBoolean(), any(), any(), any(), any(), any())).thenReturn(List.of(internal, secret));
        // callerResolver 现在统一拼装 Caller；具体内容不影响断言（Guard 已 mock）。
        when(callerResolver.current()).thenReturn(new DashboardAccessGuard.Caller(
            "employee",
            java.util.Set.of("PUBLIC", "INTERNAL"),
            java.util.Set.of("ROLE_EMPLOYEE", "ROLE_INTERNAL"),
            null,
            false,
            false
        ));
        when(accessGuard.canView(eq(internal), any())).thenReturn(AccessDecision.allow("BASE_ACCESS_PLUS_LEVEL"));
        when(accessGuard.canView(eq(secret), any())).thenReturn(AccessDecision.deny("DENY_LEVEL_BLOCKED"));

        List<BiReportLinkDto> result = service.listPublished(null, null, null, null, null, null);

        assertThat(result).extracting(BiReportLinkDto::code).containsExactly("internal_dashboard");
    }

    @Test
    void listPublishedShouldDelegateRoleCheckToGuard() {
        // 历史语义：roleCodes=ROLE_DEPT_DATA_OWNER 大屏需要 caller 持有该角色；现在交给 Guard。
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "employee",
                "n/a",
                AuthorityUtils.createAuthorityList("ROLE_EMPLOYEE", "ROLE_INTERNAL")
            )
        );

        BiReportLink byOwnerRole = buildLink("owner_only", "INTERNAL", "ROLE_DEPT_DATA_OWNER");
        BiReportLink noRoleConstraint = buildLink("open_to_all", "INTERNAL", null);
        when(reportLinkRepository.findCandidatesForListing(anyBoolean(), any(), any(), any(), any(), any())).thenReturn(
            List.of(byOwnerRole, noRoleConstraint)
        );
        // callerResolver 现在统一拼装 Caller；具体内容不影响断言（Guard 已 mock）。
        when(callerResolver.current()).thenReturn(new DashboardAccessGuard.Caller(
            "employee",
            java.util.Set.of("PUBLIC", "INTERNAL"),
            java.util.Set.of("ROLE_EMPLOYEE", "ROLE_INTERNAL"),
            null,
            false,
            false
        ));
        when(accessGuard.canView(eq(byOwnerRole), any())).thenReturn(AccessDecision.deny("DENY_NO_BASE_ACCESS"));
        when(accessGuard.canView(eq(noRoleConstraint), any())).thenReturn(AccessDecision.allow("BASE_ACCESS_PLUS_LEVEL"));

        List<BiReportLinkDto> result = service.listPublished(null, null, null, null, null, null);

        assertThat(result).extracting(BiReportLinkDto::code).containsExactly("open_to_all");
    }

    @Test
    void touchVisitShouldCreateScreenLinkAndAppendVisitWhenMirrorMissing() {
        UUID generatedId = UUID.randomUUID();
        Jwt jwt = Jwt
            .withTokenValue("token")
            .header("alg", "none")
            .claim("preferred_username", "viewer")
            .claim("dept_code", "1502")
            .build();
        SecurityContextHolder
            .getContext()
            .setAuthentication(new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList("ROLE_EMPLOYEE")));

        when(reportLinkRepository.findFirstByCodeIgnoreCase("screen-7")).thenReturn(Optional.empty());
        when(reportLinkRepository.save(any(BiReportLink.class))).thenAnswer(invocation -> {
            BiReportLink link = invocation.getArgument(0);
            if (link.getId() == null) {
                link.setId(generatedId);
            }
            return link;
        });

        service.touchVisit(null, "screen-7", "预算大屏", "https://example.local/ignored", null, null);

        ArgumentCaptor<BiReportLink> linkCaptor = ArgumentCaptor.forClass(BiReportLink.class);
        verify(reportLinkRepository).save(linkCaptor.capture());
        BiReportLink savedLink = linkCaptor.getValue();
        assertThat(savedLink.getId()).isEqualTo(generatedId);
        assertThat(savedLink.getCode()).isEqualTo("screen-7");
        assertThat(savedLink.getTitle()).isEqualTo("预算大屏");
        assertThat(savedLink.getEngine()).isEqualTo(ScreenReportLinkSyncService.ENGINE);
        assertThat(savedLink.getReportType()).isEqualTo(ScreenReportLinkSyncService.REPORT_TYPE);
        assertThat(savedLink.getUrl()).isEqualTo("/bi/screens/7/preview");
        assertThat(savedLink.getClassification()).isEqualTo(ScreenReportLinkSyncService.DEFAULT_CLASSIFICATION);
        assertThat(savedLink.getDeptCodes()).isEqualTo("1502");
        assertThat(savedLink.getLastVisitedAt()).isNotNull();

        ArgumentCaptor<BiReportVisit> visitCaptor = ArgumentCaptor.forClass(BiReportVisit.class);
        verify(reportVisitRepository).save(visitCaptor.capture());
        BiReportVisit savedVisit = visitCaptor.getValue();
        assertThat(savedVisit.getReportId()).isEqualTo(generatedId);
        assertThat(savedVisit.getUserLogin()).isEqualTo("viewer");
        assertThat(savedVisit.getDeptCode()).isEqualTo("1502");
        assertThat(savedVisit.getVisitedAt()).isEqualTo(savedLink.getLastVisitedAt());
    }

    @Test
    void touchVisitShouldIgnoreMissingNonScreenLink() {
        when(reportLinkRepository.findFirstByCodeIgnoreCase("manual-report")).thenReturn(Optional.empty());

        // H1：非大屏 code 找不到镜像时返回 SKIPPED，外层用 SUCCESS 写 VIS_OPEN（保留 fire-and-forget）。
        BiReportLinkService.VisitOutcome outcome =
            service.touchVisit(null, "manual-report", "手工链接", "/manual", null, null);

        assertThat(outcome).isEqualTo(BiReportLinkService.VisitOutcome.SKIPPED);
        verify(reportLinkRepository, never()).save(any(BiReportLink.class));
        verify(reportVisitRepository, never()).save(any(BiReportVisit.class));
    }

    @Test
    void touchVisitOnExistingMirrorShouldDenyWhenGuardDenies() {
        // 已存在 mirror 且 Guard 拒绝时，不更新 lastVisitedAt 也不写 visit log；
        // 但应写一条 audit failure 留痕。
        UUID existingId = UUID.randomUUID();
        BiReportLink existing = buildLink("screen-99", "CONFIDENTIAL", null);
        existing.setId(existingId);
        when(reportLinkRepository.findById(existingId)).thenReturn(java.util.Optional.of(existing));
        when(callerResolver.current()).thenReturn(new DashboardAccessGuard.Caller(
            "low_clearance",
            java.util.Set.of("PUBLIC"),
            java.util.Set.of("ROLE_EMPLOYEE"),
            null,
            false,
            false
        ));
        when(accessGuard.canView(eq(existing), any())).thenReturn(AccessDecision.deny("DENY_LEVEL_BLOCKED"));

        BiReportLinkService.VisitOutcome outcome =
            service.touchVisit(existingId, "screen-99", null, null, null, null);

        // H1 修复：返回 DENIED 让外层 controller 知道要把 VIS_OPEN 标 FAIL。
        assertThat(outcome).isEqualTo(BiReportLinkService.VisitOutcome.DENIED);
        verify(reportLinkRepository, never()).save(any(BiReportLink.class));
        verify(reportVisitRepository, never()).save(any(BiReportVisit.class));
        verify(audit).auditAction(eq("VIS_DASHBOARD_ACCESS_VISIT"), eq(com.yuzhi.dts.common.audit.AuditStage.FAIL), eq("screen-99"), org.mockito.ArgumentMatchers.contains("DENY_LEVEL_BLOCKED"));
    }

    @Test
    void touchVisitOnExistingMirrorWithOverrideShouldWriteSeparateAudit() {
        // 越级访问允许通过，但需要单独审计留痕，便于合规回溯。
        UUID existingId = UUID.randomUUID();
        BiReportLink existing = buildLink("screen-77", "CONFIDENTIAL", null);
        existing.setId(existingId);
        when(reportLinkRepository.findById(existingId)).thenReturn(java.util.Optional.of(existing));
        when(callerResolver.current()).thenReturn(new DashboardAccessGuard.Caller(
            "shared_user",
            java.util.Set.of("PUBLIC"),
            java.util.Set.of("ROLE_EMPLOYEE"),
            null,
            false,
            false
        ));
        when(accessGuard.canView(eq(existing), any())).thenReturn(AccessDecision.allowOverride("OVERRIDE_USED"));
        when(reportLinkRepository.save(any(BiReportLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BiReportLinkService.VisitOutcome outcome =
            service.touchVisit(existingId, "screen-77", null, null, null, null);

        // H1 修复：返回 LOGGED_OVERRIDE，外层不再额外写一条 SUCCESS（避免重复）。
        assertThat(outcome).isEqualTo(BiReportLinkService.VisitOutcome.LOGGED_OVERRIDE);
        verify(reportLinkRepository).save(any(BiReportLink.class));
        verify(audit).auditAction(eq("VIS_DASHBOARD_ACCESS_VISIT_OVERRIDE"), eq(com.yuzhi.dts.common.audit.AuditStage.SUCCESS),
            org.mockito.ArgumentMatchers.contains("OVERRIDE_USED"), eq(null));
    }

    private BiReportLink buildLink(String code, String classification, String roleCodes) {
        BiReportLink link = new BiReportLink();
        link.setId(UUID.randomUUID());
        link.setCode(code);
        link.setTitle(code);
        link.setEngine("HETU");
        link.setReportType("dashboard");
        link.setClassification(classification);
        link.setUrl("https://example.local/" + code);
        link.setEnabled(true);
        link.setRoleCodes(roleCodes);
        link.setDeptCodes(null);
        return link;
    }
}
