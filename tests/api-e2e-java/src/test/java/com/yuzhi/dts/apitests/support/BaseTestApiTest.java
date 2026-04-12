package com.yuzhi.dts.apitests.support;

import static io.restassured.RestAssured.given;

import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;

/** /test/** helper API specs — X-Test-Token auth only. */
public abstract class BaseTestApiTest {

    protected static RequestSpecification adminTestApi;
    protected static RequestSpecification platformTestApi;

    @BeforeAll
    static void initTestApiSpecs() {
        String token = Env.require("TEST_API_TOKEN");
        var cfg = RestAssuredConfig.config().sslConfig(
            Env.verifyTls() ? new SSLConfig() : new SSLConfig().relaxedHTTPSValidation()
        );

        adminTestApi = new RequestSpecBuilder()
            .setBaseUri(Env.require("ADMIN_BASE_URL"))
            .addHeader("X-Test-Token", token)
            .addHeader("Accept", "application/json")
            .addFilter(new AllureRestAssured())
            .setConfig(cfg)
            .build();

        platformTestApi = new RequestSpecBuilder()
            .setBaseUri(Env.require("PLATFORM_BASE_URL"))
            .addHeader("X-Test-Token", token)
            .addHeader("Accept", "application/json")
            .addFilter(new AllureRestAssured())
            .setConfig(cfg)
            .build();
    }

    protected static RequestSpecification adminTestApi() {
        return given().spec(adminTestApi);
    }

    protected static RequestSpecification platformTestApi() {
        return given().spec(platformTestApi);
    }
}
