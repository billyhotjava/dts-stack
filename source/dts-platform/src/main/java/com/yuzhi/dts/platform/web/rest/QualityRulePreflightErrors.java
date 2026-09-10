package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.governance.QualityRulePreflightService.Rejected;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class QualityRulePreflightErrors {
    @ExceptionHandler(Rejected.class)
    public ResponseEntity<ApiResponse<?>> rejected(Rejected error) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(ResultStatus.ERROR.getCode(), error.getMessage(), "QUALITY_SQL_INVALID", error.validation()));
    }
}
