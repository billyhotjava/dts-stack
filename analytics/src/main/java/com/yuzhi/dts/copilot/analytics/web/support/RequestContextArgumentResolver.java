package com.yuzhi.dts.copilot.analytics.web.support;

import org.springframework.core.MethodParameter;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class RequestContextArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(@NonNull MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentRequestContext.class)
                && RequestContext.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            @NonNull MethodParameter parameter,
            @Nullable ModelAndViewContainer mavContainer,
            @NonNull NativeWebRequest webRequest,
            @Nullable WebDataBinderFactory binderFactory) {
        CurrentRequestContext annotation = parameter.getParameterAnnotation(CurrentRequestContext.class);
        RequestContext context = RequestContextHolder.current();
        if (context != null) {
            return context;
        }
        if (annotation != null && annotation.required()) {
            throw new IllegalStateException("RequestContext is required but missing");
        }
        String fallback = annotation == null ? null : annotation.fallbackRequestId();
        String requestId = StringUtils.hasText(fallback) ? fallback : "unknown";
        return new RequestContext(
                requestId, "unknown", "unknown", "unknown", "unknown", "unknown", "en-US", "UTC");
    }
}
