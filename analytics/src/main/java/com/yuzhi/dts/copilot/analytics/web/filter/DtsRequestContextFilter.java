package com.yuzhi.dts.copilot.analytics.web.filter;

import com.yuzhi.dts.copilot.analytics.web.support.RequestContext;
import com.yuzhi.dts.copilot.analytics.web.support.RequestContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.context.i18n.LocaleContextHolder;

@Component
public class DtsRequestContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DtsRequestContextFilter.class);
    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final String USER_AGENT = "User-Agent";
    private static final String TIMEZONE_HEADER = "X-Timezone";

    private final LocaleResolver localeResolver;

    public DtsRequestContextFilter(LocaleResolver localeResolver) {
        this.localeResolver = localeResolver;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        Locale locale = resolveLocale(request);
        String timeZoneId = resolveTimezone(request);
        RequestContext context = new RequestContext(
                resolveRequestId(request),
                resolveClientIp(request),
                request.getHeader(USER_AGENT),
                resolveScheme(request),
                resolveHost(request),
                request.getRequestURI(),
                locale.toLanguageTag(),
                timeZoneId);
        LocaleContextHolder.setLocale(locale);
        LocaleContextHolder.setTimeZone(TimeZone.getTimeZone(timeZoneId));
        RequestContextHolder.set(context);
        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestContextHolder.clear();
            LocaleContextHolder.resetLocaleContext();
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        if (attribute instanceof String id && !id.isBlank()) {
            return id;
        }
        String header = request.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        if (header != null && !header.isBlank()) {
            return header;
        }
        return "unknown";
    }

    private String resolveClientIp(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(FORWARDED_FOR))
                .filter(h -> !h.isBlank())
                .map(h -> h.split(",")[0].trim())
                .filter(h -> !h.isBlank())
                .orElseGet(request::getRemoteAddr);
    }

    private String resolveScheme(HttpServletRequest request) {
        String forwardedScheme = request.getHeader("X-Forwarded-Proto");
        if (forwardedScheme != null && !forwardedScheme.isBlank()) {
            return forwardedScheme;
        }
        return request.getScheme();
    }

    private String resolveHost(HttpServletRequest request) {
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        if (forwardedHost != null && !forwardedHost.isBlank()) {
            return forwardedHost.split(",")[0].trim();
        }
        return request.getServerName();
    }

    private Locale resolveLocale(HttpServletRequest request) {
        Locale locale = localeResolver.resolveLocale(request);
        if (locale != null) {
            return locale;
        }
        return Locale.US;
    }

    private String resolveTimezone(HttpServletRequest request) {
        String header = request.getHeader(TIMEZONE_HEADER);
        if (header != null && !header.isBlank()) {
            try {
                return ZoneId.of(header.trim()).getId();
            } catch (Exception e) {
                log.debug("Invalid timezone header '{}', falling back to system default", header);
            }
        }
        return ZoneId.systemDefault().getId();
    }
}

