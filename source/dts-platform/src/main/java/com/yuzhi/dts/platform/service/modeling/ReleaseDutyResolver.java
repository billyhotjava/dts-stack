package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryActorRole;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Resolves modeling release duties from the current authenticated authority set. */
@Component
public class ReleaseDutyResolver {

    public Set<DeliveryActorRole> currentDuties() {
        return resolveAuthorities(SecurityUtils.getCurrentUserAuthorities());
    }

    static Set<DeliveryActorRole> resolveAuthorities(Collection<String> authorities) {
        EnumSet<DeliveryActorRole> duties = EnumSet.noneOf(DeliveryActorRole.class);
        if (authorities == null) return Set.of();
        if (hasAny(authorities, AuthoritiesConstants.MODEL_MAINTAINERS)) {
            duties.add(DeliveryActorRole.MODEL_MAINTAINER);
        }
        if (hasAny(authorities, AuthoritiesConstants.MODEL_RELEASE_REVIEWERS)) {
            duties.add(DeliveryActorRole.RELEASE_REVIEWER);
        }
        if (hasAny(authorities, AuthoritiesConstants.MODEL_RELEASE_OPERATORS)) {
            duties.add(DeliveryActorRole.RELEASE_OPERATOR);
        }
        return Set.copyOf(duties);
    }

    private static boolean hasAny(Collection<String> authorities, String[] allowed) {
        return Arrays.stream(allowed).anyMatch(authorities::contains);
    }
}
