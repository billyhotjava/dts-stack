package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.CreateDraftRequest;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.DraftException;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.DraftState;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.DraftView;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.ErrorKind;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftRejectionAudit;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftService;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import java.time.Instant;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.stream.Stream;
import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@ExtendWith(MockitoExtension.class)
class DbtImplementationDraftResourceTest {

    private static final UUID MODEL_ID = UUID.fromString("20000000-0000-0000-0000-000000000083");
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000083");
    private static final UUID DRAFT_ID = UUID.fromString("30000000-0000-0000-0000-000000000083");

    @Mock
    private DbtImplementationDraftService service;

    @Mock
    private WarehousePlanActorProvider actorProvider;

    @Mock
    private DbtImplementationDraftRejectionAudit rejectionAudit;

    @Test
    void createsAnActorScopedDraftAndReturnsItsStrongEtag() {
        CreateDraftRequest request = new CreateDraftRequest(PLAN_ID, 3, "a".repeat(64), 2, "b".repeat(64), "create-83");
        DraftView created = new DraftView(
            DRAFT_ID,
            PLAN_ID,
            MODEL_ID,
            3,
            "a".repeat(64),
            2,
            "b".repeat(64),
            DraftState.DRAFT,
            "etag-83",
            Instant.parse("2026-08-03T00:00:00Z"),
            null
        );
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("maintainer-83", "dept-83"));
        when(service.create("tenant-83", "maintainer-83", MODEL_ID, request)).thenReturn(created);

        var response = resource().create(MODEL_ID, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"etag-83\"");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isSameAs(created);
        verify(service).create("tenant-83", "maintainer-83", MODEL_ID, request);
    }

    @ParameterizedTest
    @MethodSource("draftStatuses")
    void mapsStableDraftErrorsToThePinnedHttpStatus(ErrorKind kind, HttpStatus expected) {
        var response = resource().handleDraftError(new DraftException("DBT_DRAFT_ERROR", "draft failed", kind));

        assertThat(response.getStatusCode()).isEqualTo(expected);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("DBT_DRAFT_ERROR");
    }

    @Test
    void validatesEveryJsonRequestBodyBeforeCallingTheService() {
        assertThat(Stream.of(DbtImplementationDraftResource.class.getDeclaredMethods())
                .filter(method -> Stream.of("create", "saveFiles", "validate", "commit").anyMatch(method.getName()::equals))
                .map(Method::getParameters)
                .flatMap(Stream::of)
                .filter(parameter -> parameter.isAnnotationPresent(org.springframework.web.bind.annotation.RequestBody.class)))
            .allMatch(parameter -> parameter.isAnnotationPresent(Valid.class));
    }

    @Test
    void strictlyAuditsBeanValidationRejectionsBeforeReturningThePublic400() {
        MethodArgumentNotValidException failure = org.mockito.Mockito.mock(MethodArgumentNotValidException.class);
        BindingResult binding = org.mockito.Mockito.mock(BindingResult.class);
        when(failure.getBindingResult()).thenReturn(binding);
        when(binding.getErrorCount()).thenReturn(3);
        String path = "/api/modeling/model-specs/" + MODEL_ID + "/dbt-drafts";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        var response = resource().handleValidationError(failure, request, servletResponse);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("DBT_DRAFT_REQUEST_INVALID");
        assertThat(servletResponse.getHeader("X-Correlation-Id")).isNotBlank();
        verify(rejectionAudit).recordValidationFailure(
            request,
            servletResponse.getHeader("X-Correlation-Id"),
            3
        );
    }

    @Test
    void strictlyAuditsMalformedJsonBeforeReturningThePublic400() {
        HttpMessageNotReadableException failure = new HttpMessageNotReadableException("malformed JSON");
        String path = "/api/modeling/model-specs/" + MODEL_ID + "/dbt-drafts";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        var response = resource().handleMalformedBody(failure, request, servletResponse);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("DBT_DRAFT_REQUEST_MALFORMED");
        assertThat(servletResponse.getHeader("X-Correlation-Id")).isNotBlank();
        verify(rejectionAudit).recordMalformedBody(
            request,
            servletResponse.getHeader("X-Correlation-Id")
        );
    }

    private DbtImplementationDraftResource resource() {
        return new DbtImplementationDraftResource(service, actorProvider, rejectionAudit, "tenant-83");
    }

    private static Stream<Arguments> draftStatuses() {
        return Stream.of(
            Arguments.of(ErrorKind.BAD_REQUEST, HttpStatus.BAD_REQUEST),
            Arguments.of(ErrorKind.FORBIDDEN, HttpStatus.FORBIDDEN),
            Arguments.of(ErrorKind.NOT_FOUND, HttpStatus.NOT_FOUND),
            Arguments.of(ErrorKind.GONE, HttpStatus.GONE),
            Arguments.of(ErrorKind.CONFLICT, HttpStatus.CONFLICT),
            Arguments.of(ErrorKind.PRECONDITION_FAILED, HttpStatus.PRECONDITION_FAILED),
            Arguments.of(ErrorKind.UNPROCESSABLE, HttpStatus.UNPROCESSABLE_ENTITY),
            Arguments.of(ErrorKind.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR)
        );
    }
}
