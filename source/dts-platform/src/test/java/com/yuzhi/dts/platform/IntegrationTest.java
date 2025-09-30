package com.yuzhi.dts.platform;

import com.yuzhi.dts.platform.config.AsyncSyncConfiguration;
import com.yuzhi.dts.platform.config.EmbeddedSQL;
import com.yuzhi.dts.platform.config.JacksonConfiguration;
import com.yuzhi.dts.platform.config.TestSecurityConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Base composite annotation for integration tests.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
    classes = { DtsPlatformApp.class, JacksonConfiguration.class, AsyncSyncConfiguration.class, TestSecurityConfiguration.class }
)
@EmbeddedSQL
public @interface IntegrationTest {
}
