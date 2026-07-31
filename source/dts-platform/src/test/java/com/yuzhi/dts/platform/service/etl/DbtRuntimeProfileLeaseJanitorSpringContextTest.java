package com.yuzhi.dts.platform.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.yuzhi.dts.platform.config.ModelMaterializationProperties;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class DbtRuntimeProfileLeaseJanitorSpringContextTest {

    @Test
    void springContextConstructsJanitorWithRuntimeDependencies() {
        try (
            var context = new AnnotationConfigApplicationContext()
        ) {
            context.registerBean(
                ModelMaterializationProperties.class,
                ModelMaterializationProperties::new
            );
            context.registerBean(
                DbtRuntimeProfileLeaseRepository.class,
                () -> mock(DbtRuntimeProfileLeaseRepository.class)
            );
            context.registerBean(
                DbtRuntimeProfileLeaseFileStore.class,
                () -> mock(DbtRuntimeProfileLeaseFileStore.class)
            );
            context.register(DbtRuntimeProfileLeaseJanitor.class);

            context.refresh();

            assertThat(
                context.getBean(DbtRuntimeProfileLeaseJanitor.class)
            )
                .isNotNull();
        }
    }
}
