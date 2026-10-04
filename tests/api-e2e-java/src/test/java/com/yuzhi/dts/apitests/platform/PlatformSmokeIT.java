package com.yuzhi.dts.apitests.platform;

import static org.hamcrest.Matchers.equalTo;

import com.yuzhi.dts.apitests.support.BaseTestApiTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("platform")
class PlatformSmokeIT extends BaseTestApiTest {

    @Test
    @Tag("smoke")
    void platformTestApiReachable() {
        platformTestApi()
            .when().get("/test/ping")
            .then().statusCode(200)
            .body("module", equalTo("platform"));
    }
}
