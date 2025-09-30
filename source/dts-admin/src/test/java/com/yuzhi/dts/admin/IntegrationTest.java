package com.yuzhi.dts.admin;

import com.yuzhi.dts.admin.config.AsyncSyncConfiguration;
import com.yuzhi.dts.admin.config.EmbeddedSQL;
import com.yuzhi.dts.admin.config.JacksonConfiguration;
import com.yuzhi.dts.admin.config.TestSecurityConfiguration;
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
@SpringBootTest(classes = { DtsAdminApp.class, JacksonConfiguration.class, AsyncSyncConfiguration.class, TestSecurityConfiguration.class })
@EmbeddedSQL
public @interface IntegrationTest {
}
