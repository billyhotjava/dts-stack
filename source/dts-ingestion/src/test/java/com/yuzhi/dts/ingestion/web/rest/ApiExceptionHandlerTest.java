package com.yuzhi.dts.ingestion.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.ingestion.service.etl.api.ApiHttpException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void shouldExposeOnlyStableApiHttpCodeAndGenericMessage() {
        ApiHttpException exception = new ApiHttpException(
            "API_RUNTIME_AUTH",
            "upstream body {\"token\":\"secret-token\"}",
            401,
            1
        );

        var response = handler.handleApiHttp(exception);
        ApiResponse<Object> body = response.getBody();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
        assertThat(body.getCode()).isEqualTo("API_RUNTIME_AUTH");
        assertThat(body.getMessage()).isEqualTo("外部 API 调用失败");
        assertThat(body.getMessage()).doesNotContain("secret-token", "upstream body");
    }

    @Test
    void shouldNotExposeResponseStatusReasonOrUnreadableBodyDetail() {
        var statusResponse = handler.handleStatus(
            new ResponseStatusException(HttpStatus.BAD_REQUEST, "client_secret=line-secret")
        );
        var readableResponse = handler.handleReadable(
            new HttpMessageNotReadableException("{\"token\":\"json-secret\"}")
        );
        ApiResponse<Object> statusBody = statusResponse.getBody();
        ApiResponse<Object> readableBody = readableResponse.getBody();

        assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(readableResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(statusBody).isNotNull();
        assertThat(statusBody.getCode()).isEqualTo("HTTP_400");
        assertThat(statusBody.getMessage()).isEqualTo("请求参数错误");
        assertThat(readableBody).isNotNull();
        assertThat(readableBody.getCode()).isEqualTo("INVALID_REQUEST_BODY");
        assertThat(readableBody.getMessage()).isEqualTo("请求体解析失败");
        assertThat(statusBody.getMessage() + readableBody.getMessage())
            .doesNotContain("line-secret", "json-secret", "client_secret", "token");
    }
}
