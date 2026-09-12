package com.yuzhi.dts.platform.security.modeling;

import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway;
import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway.ModelingUser;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayException;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.stereotype.Service;

@Service
public class ModelingIdentityService {
    private final AdminDirectoryGateway directory;
    public ModelingIdentityService(AdminDirectoryGateway directory) { this.directory = directory; }

    public ModelingUser resolve(String id) {
        if (id == null || id.isBlank()) throw new ModelingIdentityException(401, "MODELING_REAUTHENTICATION_REQUIRED", "请重新登录后进入数据建模");
        ModelingUser user;
        try {
            user = directory.currentModelingUser(id);
        } catch (AdminGatewayException ex) {
            if (Integer.valueOf(404).equals(ex.getUpstreamStatus())) throw inactive();
            throw unavailable();
        } catch (RuntimeException ex) { throw unavailable(); }
        if (user == null || !id.equals(user.id()) || user.roles() == null || user.enabled() == null || user.username() == null) throw unavailable();
        if (!user.enabled()) throw inactive();
        if (!ModelingIdentity.isAuthor(user)) throw new ModelingIdentityException(403, "MODELING_ROLE_REQUIRED", "当前账号不具备数据建模权限");
        return user;
    }

    /** Trusted caller supplies a previously sealed stable ID; never a request-body actor. */
    public <T> T asCurrentUser(String id, Supplier<T> action) {
        return withIdentity(resolve(id), action);
    }

    private Scope openIdentity(ModelingUser user) {
        var previous = SecurityContextHolder.getContext();
        var previousUser = ModelingIdentity.optional().orElse(null);
        var authorities = user.roles().stream().map(SimpleGrantedAuthority::new).toList();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", user.id());
        attributes.put("directory_user_id", user.id());
        attributes.put("preferred_username", user.username());
        attributes.put("username", user.username());
        attributes.put("roles", user.roles());
        if (user.deptCode() != null) attributes.put("dept_code", user.deptCode());
        if (user.personnelLevel() != null) attributes.put("personnel_level", user.personnelLevel());
        var principal = new DefaultOAuth2AuthenticatedPrincipal(user.username(), attributes, authorities);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
        SecurityContextHolder.setContext(context);
        ModelingIdentity.set(user);
        return () -> {
            ModelingIdentity.set(previousUser);
            SecurityContextHolder.setContext(previous);
        };
    }

    public Scope openCurrentUser(String id) { return openIdentity(resolve(id)); }
    public <T> T withIdentity(ModelingUser user, Supplier<T> action) {
        try (Scope scope = openIdentity(user)) { return action.get(); }
    }
    public interface Scope extends AutoCloseable { @Override void close(); }

    private static ModelingIdentityException inactive() { return new ModelingIdentityException(401, "MODELING_IDENTITY_INACTIVE", "账号已停用或不存在，请重新登录"); }
    private static ModelingIdentityException unavailable() { return new ModelingIdentityException(503, "MODELING_IDENTITY_UNAVAILABLE", "当前用户目录不可用，请稍后重试"); }
}
