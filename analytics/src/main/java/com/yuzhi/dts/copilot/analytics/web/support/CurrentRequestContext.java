package com.yuzhi.dts.copilot.analytics.web.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.web.bind.annotation.ValueConstants;

@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentRequestContext {
    /**
        * Whether to fail if no context has been established by the filter chain.
        * Defaults to true to mirror middleware expectations that context exists.
        */
    boolean required() default true;

    /**
        * Optional fallback request ID if no context is available and required=false.
        */
    String fallbackRequestId() default ValueConstants.DEFAULT_NONE;
}
