package com.yuzhi.dts.platform.service.security;

import com.yuzhi.dts.platform.security.SecurityUtils;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SecurityContextAssetActionSubjectResolver implements AssetActionSubjectResolver {

    @Override
    public Set<SubjectKey> currentSubjects() {
        Set<SubjectKey> subjects = new LinkedHashSet<>();
        SecurityUtils
            .getCurrentUserAuthorities()
            .stream()
            .filter(StringUtils::hasText)
            .map(authority -> new SubjectKey("ROLE", authority))
            .forEach(subjects::add);
        SecurityUtils.getCurrentUserId().filter(StringUtils::hasText).map(id -> new SubjectKey("USER", id)).ifPresent(subjects::add);
        SecurityUtils
            .getCurrentUserLogin()
            .filter(StringUtils::hasText)
            .map(login -> new SubjectKey("USER", login))
            .ifPresent(subjects::add);
        SecurityUtils
            .getCurrentUserDept()
            .filter(StringUtils::hasText)
            .map(department -> new SubjectKey("DEPARTMENT", department))
            .ifPresent(subjects::add);
        return Set.copyOf(subjects);
    }
}
