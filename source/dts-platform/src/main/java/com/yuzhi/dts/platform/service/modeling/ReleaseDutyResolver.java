package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryActorRole;
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
        if (authorities.contains(AuthoritiesConstants.OP_ADMIN)) {
            duties.add(DeliveryActorRole.MODEL_MAINTAINER);
            duties.add(DeliveryActorRole.RELEASE_REVIEWER);
            duties.add(DeliveryActorRole.RELEASE_OPERATOR);
        }
        if (authorities.contains(AuthoritiesConstants.INST_DATA_OWNER)) {
            duties.add(DeliveryActorRole.MODEL_MAINTAINER);
        }
        if (authorities.contains(AuthoritiesConstants.INST_LEADER)) {
            duties.add(DeliveryActorRole.MODEL_MAINTAINER);
            duties.add(DeliveryActorRole.RELEASE_REVIEWER);
        }
        return Set.copyOf(duties);
    }
}
