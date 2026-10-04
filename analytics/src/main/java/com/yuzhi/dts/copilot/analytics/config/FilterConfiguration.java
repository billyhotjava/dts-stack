package com.yuzhi.dts.copilot.analytics.config;

import com.yuzhi.dts.copilot.analytics.web.filter.DtsRequestContextFilter;
import com.yuzhi.dts.copilot.analytics.web.filter.PlatformSessionBridgeFilter;
import com.yuzhi.dts.copilot.analytics.web.filter.RequestIdFilter;
import com.yuzhi.dts.copilot.analytics.web.filter.RequestLoggingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ForwardedHeaderFilter;

@Configuration
public class FilterConfiguration {

    @Bean
    public FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilterRegistration() {
        FilterRegistrationBean<ForwardedHeaderFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ForwardedHeaderFilter());
        registration.setOrder(0);
        registration.addUrlPatterns("/*");
        registration.setName("forwardedHeaderFilter");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration(RequestIdFilter filter) {
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.setOrder(1);
        registration.addUrlPatterns("/*");
        registration.setName("requestIdFilter");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<DtsRequestContextFilter> dtsRequestContextFilterRegistration(
            DtsRequestContextFilter filter) {
        FilterRegistrationBean<DtsRequestContextFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.setOrder(2);
        registration.addUrlPatterns("/*");
        registration.setName("dtsRequestContextFilter");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilterRegistration(
            RequestLoggingFilter filter) {
        FilterRegistrationBean<RequestLoggingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.setOrder(4);
        registration.addUrlPatterns("/*");
        registration.setName("requestLoggingFilter");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<PlatformSessionBridgeFilter> platformSessionBridgeFilterRegistration(
            PlatformSessionBridgeFilter filter) {
        FilterRegistrationBean<PlatformSessionBridgeFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.setOrder(3);
        registration.addUrlPatterns("/*");
        registration.setName("platformSessionBridgeFilter");
        return registration;
    }
}
