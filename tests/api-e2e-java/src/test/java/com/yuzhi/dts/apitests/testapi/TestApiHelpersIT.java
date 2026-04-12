package com.yuzhi.dts.apitests.testapi;

import static org.hamcrest.Matchers.equalTo;

import com.yuzhi.dts.apitests.support.BaseTestApiTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class TestApiHelpersIT extends BaseTestApiTest {

    @Test
    @Tag("smoke")
    void adminPing() {
        adminTestApi()
            .when().get("/test/ping")
            .then().statusCode(200)
            .body("status", equalTo("ok"))
            .body("module", equalTo("admin"));
    }

    @Test
    @Tag("smoke")
    void platformPing() {
        platformTestApi()
            .when().get("/test/ping")
            .then().statusCode(200)
            .body("status", equalTo("ok"))
            .body("module", equalTo("platform"));
    }
}
