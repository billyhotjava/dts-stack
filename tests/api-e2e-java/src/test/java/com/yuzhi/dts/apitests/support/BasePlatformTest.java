package com.yuzhi.dts.apitests.support;

import static io.restassured.RestAssured.given;

import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;

/** Platform specification — Bearer token against PLATFORM_BASE_URL. */
public abstract class BasePlatformTest {

    protected static RequestSpecification platformSpec;

    @BeforeAll
    static void initPlatformSpec() {
        String baseUrl = Env.require("PLATFORM_BASE_URL");
        String token = Keycloak.fetchToken("PLATFORM_USERNAME", "PLATFORM_PASSWORD");
        platformSpec = new RequestSpecBuilder()
            .setBaseUri(baseUrl)
            .addHeader("Authorization", "Bearer " + token)
            .addHeader("Accept", "application/json")
            .addFilter(new AllureRestAssured())
            .setConfig(
                RestAssuredConfig.config().sslConfig(
                    Env.verifyTls() ? new SSLConfig() : new SSLConfig().relaxedHTTPSValidation()
                )
            )
            .build();
    }

    protected static RequestSpecification platform() {
        return given().spec(platformSpec);
    }
}
