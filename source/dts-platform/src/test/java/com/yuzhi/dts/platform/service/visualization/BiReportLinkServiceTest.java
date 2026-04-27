package com.yuzhi.dts.platform.service.visualization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.visualization.BiReportLink;
import com.yuzhi.dts.platform.domain.visualization.BiReportVisit;
import com.yuzhi.dts.platform.repository.explore.QueryDatasetAssetRepository;
import com.yuzhi.dts.platform.repository.visualization.BiReportLinkRepository;
import com.yuzhi.dts.platform.repository.visualization.BiReportVisitRepository;
import com.yuzhi.dts.platform.security.ClassificationUtils;
import com.yuzhi.dts.platform.service.integration.ScreenReportLinkSyncService;
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

    @InjectMocks
    private BiReportLinkService service;

    @AfterEach
    void cleanupSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listPublishedShouldFilterByClassification() {
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
        when(classificationUtils.canAccess("INTERNAL")).thenReturn(true);
        when(classificationUtils.canAccess("SECRET")).thenReturn(false);

        List<BiReportLinkDto> result = service.listPublished(null, null, null, null, null, null);

        assertThat(result).extracting(BiReportLinkDto::code).containsExactly("internal_dashboard");
    }

    @Test
    void listPublishedShouldRespectRoleCodes() {
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
        when(classificationUtils.canAccess("INTERNAL")).thenReturn(true);

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

        service.touchVisit(null, "manual-report", "手工链接", "/manual", null, null);

        verify(reportLinkRepository, never()).save(any(BiReportLink.class));
        verify(reportVisitRepository, never()).save(any(BiReportVisit.class));
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
