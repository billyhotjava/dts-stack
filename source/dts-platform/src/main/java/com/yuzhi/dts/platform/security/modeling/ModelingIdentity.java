package com.yuzhi.dts.platform.security.modeling;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway.ModelingUser;
import java.util.*;

/** Request/execution-local facts. No allow decision survives an authorization boundary. */
public final class ModelingIdentity {
    private static final ThreadLocal<ModelingUser> CURRENT = new ThreadLocal<>();
    private ModelingIdentity() {}

    public static ModelingUser current() {
        ModelingUser user = CURRENT.get();
        if (user == null) throw new ModelingIdentityException(401, "MODELING_REAUTHENTICATION_REQUIRED", "请重新登录后进入数据建模");
        return user;
    }
    public static Optional<ModelingUser> optional() { return Optional.ofNullable(CURRENT.get()); }
    static void set(ModelingUser user) { if (user == null) CURRENT.remove(); else CURRENT.set(user); }
    public static boolean isAuthor(ModelingUser user) {
        return user != null && Boolean.TRUE.equals(user.enabled()) && user.roles() != null &&
            Arrays.stream(AuthoritiesConstants.MODEL_AUTHORS).anyMatch(user.roles()::contains);
    }
    public static boolean institute(ModelingUser user) {
        return user != null && user.roles() != null && Arrays.stream(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES).anyMatch(user.roles()::contains);
    }
    public static boolean department(String department) {
        var user = current();
        return isAuthor(user) && department != null && !department.isBlank() && (institute(user) || department.equals(user.deptCode()));
    }
    public static boolean matchesActor(String actor) { return current().id().equals(actor); }
}
