package com.yuzhi.dts.platform.web.rest.internal;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationRuntimeException;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationRuntimeSpecService;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationRuntimeSpecService.RuntimeSpecView;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
    "/api/internal/modeling/materialization/runtime-specs"
)
@PreAuthorize(
    "hasAuthority('" +
    AuthoritiesConstants.SERVICE_INTERNAL +
    "') and authentication.name == 'service:dts-airflow'"
)
public class ModelMaterializationRuntimeSpecInternalResource {

    private final ModelMaterializationRuntimeSpecService runtimeSpecs;

    public ModelMaterializationRuntimeSpecInternalResource(
        ModelMaterializationRuntimeSpecService runtimeSpecs
    ) {
        this.runtimeSpecs = runtimeSpecs;
    }

    @PostMapping("/consume")
    public RuntimeSpecView consume(
        @RequestHeader("X-DTS-Runtime-Spec-Token") String token
    ) {
        return runtimeSpecs.consume(token);
    }

    @ExceptionHandler(ModelMaterializationRuntimeException.class)
    public ResponseEntity<Map<String, String>> handle(
        ModelMaterializationRuntimeException failure
    ) {
        HttpStatus status = switch (failure.code()) {
            case
                "MODEL_RUNTIME_SPEC_TOKEN_INVALID" -> HttpStatus.UNAUTHORIZED;
            case
                "MODEL_RUNTIME_SPEC_TOKEN_EXPIRED",
                "MODEL_RUNTIME_SPEC_CONCURRENT_CONSUME" -> HttpStatus.CONFLICT;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity
            .status(status)
            .body(
                Map.of(
                    "code",
                    failure.code(),
                    "message",
                    failure.getMessage()
                )
            );
    }
}
