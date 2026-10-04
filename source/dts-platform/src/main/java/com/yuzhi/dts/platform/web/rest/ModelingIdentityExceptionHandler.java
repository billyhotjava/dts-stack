package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.modeling.ModelingIdentityException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ModelingIdentityExceptionHandler {
    @ExceptionHandler(com.yuzhi.dts.platform.service.modeling.ModelSpecException.class)
    public ResponseEntity<ApiResponse<Object>> model(com.yuzhi.dts.platform.service.modeling.ModelSpecException failure) {
        int status = switch (failure.kind()) {
            case BAD_REQUEST -> 400; case UNPROCESSABLE -> 422; case FORBIDDEN -> 403;
            case NOT_FOUND -> 404; case CONFLICT -> 409; case PRECONDITION_REQUIRED -> 428;
        };
        return ResponseEntity.status(status).body(new ApiResponse<>(ResultStatus.ERROR.getCode(), failure.getMessage(), failure.code(), failure.details()));
    }
    @ExceptionHandler(ModelingIdentityException.class)
    public ResponseEntity<ApiResponse<Void>> handle(ModelingIdentityException failure) {
        return ResponseEntity.status(failure.status()).body(ApiResponses.error(failure.code(), failure.getMessage()));
    }
}
