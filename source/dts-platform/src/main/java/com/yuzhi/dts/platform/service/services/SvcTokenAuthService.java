package com.yuzhi.dts.platform.service.services;

import com.yuzhi.dts.platform.domain.service.SvcToken;
import com.yuzhi.dts.platform.security.policy.PersonnelLevel;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SvcTokenAuthService {

    public record TokenPrincipal(String username, String deptCode, PersonnelLevel personnelLevel, SvcToken token) {}

    private final SvcTokenService tokenService;

    public SvcTokenAuthService(SvcTokenService tokenService) {
        this.tokenService = tokenService;
    }

    public TokenPrincipal authenticate(String plainToken) {
        SvcToken token = tokenService.validatePlainToken(plainToken);
        if (token == null) {
            return null;
        }
        String username = StringUtils.hasText(token.getCreatedBy()) ? token.getCreatedBy().trim() : null;
        String dept = StringUtils.hasText(token.getSubjectDeptCode()) ? token.getSubjectDeptCode().trim() : null;
        Integer levelNum = token.getSubjectPersonnelLevel();
        PersonnelLevel personnel = null;
        if (levelNum != null) {
            personnel = PersonnelLevel.normalize(String.valueOf(levelNum));
        }
        if (personnel == null) {
            personnel = PersonnelLevel.GENERAL;
        }
        if (!StringUtils.hasText(username)) {
            username = "anonymous";
        }
        return new TokenPrincipal(username, dept, personnel, token);
    }

    public TokenPrincipal authenticateService(String plainToken, String serviceName) {
        TokenPrincipal principal = authenticate(plainToken);
        if (principal == null || principal.token() == null || !StringUtils.hasText(serviceName)) {
            return null;
        }
        String createdBy = principal.token().getCreatedBy();
        String normalizedService = serviceName.trim();
        if (
            StringUtils.hasText(createdBy) &&
            ("service:" + normalizedService).equalsIgnoreCase(createdBy.trim())
        ) {
            return principal;
        }
        return null;
    }

    public static boolean isSameUser(TokenPrincipal principal, String expectedUsername) {
        if (principal == null) return false;
        if (!StringUtils.hasText(expectedUsername)) return true;
        return Objects.equals(principal.username(), expectedUsername.trim());
    }
}
