package com.yuzhi.dts.platform.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.service.catalog.CatalogExternalAssetIdentityRegistry;
import com.yuzhi.dts.platform.service.catalog.CatalogExternalAssetIdentityRegistry.Registration;
import com.yuzhi.dts.platform.service.catalog.CodeAssetGrantWriter;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CatalogExternalAssetIdentityInternalResourceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private CodeAssetGrantWriter grantWriter;

    private CatalogExternalAssetIdentityInternalResource resource;

    @BeforeEach
    void setUp() {
        resource =
            new CatalogExternalAssetIdentityInternalResource(
                new CatalogExternalAssetIdentityRegistry(
                    jdbcTemplate,
                    grantWriter
                )
            );
    }

    @Test
    void missingRequestReturnsBadRequest() {
        assertBadRequest(
            () -> resource.register(null),
            "registration request is required"
        );
    }

    @Test
    void nullRegistrationReturnsBadRequest() {
        assertBadRequest(
            () ->
                resource.register(
                    new CatalogExternalAssetIdentityInternalResource.RegistrationRequest(
                        Collections.singletonList(null)
                    )
                ),
            "registration is required"
        );
    }

    @Test
    void invalidCanonicalKeyReturnsBadRequest() {
        assertBadRequest(
            () ->
                resource.register(
                    request(
                        List.of(
                            new Registration(
                                "METRIC",
                                "not-a-metric-key",
                                "metric-revenue",
                                null
                            )
                        )
                    )
                ),
            "invalid metric asset key"
        );
    }

    @Test
    void moreThanFiveHundredRegistrationsReturnsBadRequest() {
        Registration registration = new Registration(
            "METRIC",
            "metric:core/revenue",
            "metric-revenue",
            null
        );

        assertBadRequest(
            () ->
                resource.register(
                    request(Collections.nCopies(501, registration))
                ),
            "at most 500"
        );
    }

    private CatalogExternalAssetIdentityInternalResource.RegistrationRequest request(
        List<Registration> registrations
    ) {
        return new CatalogExternalAssetIdentityInternalResource.RegistrationRequest(
            registrations
        );
    }

    private void assertBadRequest(
        org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
        String message
    ) {
        assertThatThrownBy(action)
            .isInstanceOfSatisfying(
                ResponseStatusException.class,
                exception -> {
                    assertThat(exception.getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).contains(message);
                }
            );
    }
}
