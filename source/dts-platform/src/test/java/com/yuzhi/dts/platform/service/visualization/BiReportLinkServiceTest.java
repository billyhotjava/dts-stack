package com.yuzhi.dts.platform.service.visualization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.visualization.BiReportLink;
import com.yuzhi.dts.platform.repository.explore.QueryDatasetAssetRepository;
import com.yuzhi.dts.platform.repository.visualization.BiReportLinkRepository;
import com.yuzhi.dts.platform.security.ClassificationUtils;
import com.yuzhi.dts.platform.service.visualization.dto.BiReportLinkDto;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class BiReportLinkServiceTest {

    @Mock
    private BiReportLinkRepository reportLinkRepository;

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
        when(reportLinkRepository.findByEnabledTrueOrderBySortOrderAscLastModifiedDateDesc()).thenReturn(List.of(internal, secret));
        when(classificationUtils.canAccess("INTERNAL")).thenReturn(true);
        when(classificationUtils.canAccess("SECRET")).thenReturn(false);

        List<BiReportLinkDto> result = service.listPublished(null, null, null, null, null);

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
        when(reportLinkRepository.findByEnabledTrueOrderBySortOrderAscLastModifiedDateDesc()).thenReturn(
            List.of(byOwnerRole, noRoleConstraint)
        );
        when(classificationUtils.canAccess("INTERNAL")).thenReturn(true);

        List<BiReportLinkDto> result = service.listPublished(null, null, null, null, null);

        assertThat(result).extracting(BiReportLinkDto::code).containsExactly("open_to_all");
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
