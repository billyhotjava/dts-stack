package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.modeling.WordRootContract.UpsertRequest;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

class ModelingWordRootResourceTest {

    @Test
    void exposesASeparateWordRootBoundaryAndProtectsWrites() throws NoSuchMethodException {
        RequestMapping mapping = ModelingWordRootResource.class.getAnnotation(RequestMapping.class);
        assertThat(mapping.value()).containsExactly("/api/modeling/word-roots");

        Method create = ModelingWordRootResource.class.getMethod("create", UpsertRequest.class, String.class);
        Method update = ModelingWordRootResource.class.getMethod("update", UUID.class, UpsertRequest.class, String.class);
        assertThat(create.getAnnotation(PreAuthorize.class)).isNotNull();
        assertThat(update.getAnnotation(PreAuthorize.class)).isNotNull();
        assertThat(update.getAnnotation(PreAuthorize.class).value()).isEqualTo(create.getAnnotation(PreAuthorize.class).value());
    }
}
