package com.yuzhi.dts.apitests.support;

import static io.restassured.RestAssured.given;

import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;

/** Admin (三员) specification — Bearer token against ADMIN_BASE_URL. */
public abstract class BaseAdminTest {

    protected static RequestSpecification adminSpec;

    @BeforeAll
    static void initAdminSpec() {
        String baseUrl = Env.require("ADMIN_BASE_URL");
        String token = Keycloak.fetchToken("ADMIN_USERNAME", "ADMIN_PASSWORD");
        adminSpec = new RequestSpecBuilder()
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

    protected static RequestSpecification admin() {
        return given().spec(adminSpec);
    }
}
