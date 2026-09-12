package com.yuzhi.dts.platform.security.modeling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelingPermissionAudit;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ModelingIdentityFilter extends OncePerRequestFilter {
    private final ModelingIdentityService identities;
    private final ObjectMapper mapper;
    private final ModelingPermissionAudit audit;
    private final com.yuzhi.dts.platform.service.modeling.ModelSpecAccessService access;
    public ModelingIdentityFilter(ModelingIdentityService identities, ObjectMapper mapper, ModelingPermissionAudit audit, com.yuzhi.dts.platform.service.modeling.ModelSpecAccessService access) {
        this.identities = identities;
        this.mapper = mapper;
        this.audit = audit;
        this.access = access;
    }
    @Bean
    FilterRegistrationBean<ModelingIdentityFilter> modelingFilterRegistration() {
        var registration = new FilterRegistrationBean<>(this);
        registration.setEnabled(false);
        return registration;
    }
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.equals("/api/modeling") && !path.startsWith("/api/modeling/");
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String id = authentication != null && authentication.isAuthenticated() &&
            authentication.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal
                ? principal.getAttribute("directory_user_id") : null;
        try {
            var user = identities.resolve(id);
            identities.withIdentity(user, () -> {
                try { access.requirePathRead(request.getRequestURI().substring(request.getContextPath().length())); chain.doFilter(request, response); }
                catch (IOException | ServletException ex) { throw new FilterFailure(ex); }
                return null;
            });
        } catch (ModelingIdentityException ex) {
            audit.denied(id, "MODELING_IDENTITY_DENIED", null, ex.code());
            response.setStatus(ex.status());
            response.setContentType("application/json;charset=UTF-8");
            mapper.writeValue(response.getOutputStream(), ApiResponses.error(ex.code(), ex.getMessage()));
        } catch (com.yuzhi.dts.platform.service.modeling.ModelSpecException ex) {
            response.setStatus(404);
            response.setContentType("application/json;charset=UTF-8");
            mapper.writeValue(response.getOutputStream(), ApiResponses.error("MODEL_SPEC_NOT_FOUND", "模型不存在或不可见"));
        } catch (FilterFailure ex) {
            if (ex.getCause() instanceof IOException io) throw io;
            throw (ServletException) ex.getCause();
        }
    }
    private static class FilterFailure extends RuntimeException {
        FilterFailure(Exception cause) { super(cause); }
    }
}
