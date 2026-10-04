package com.yuzhi.dts.apitests.admin;

import com.yuzhi.dts.apitests.support.BaseAdminTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("admin")
class AuthSmokeIT extends BaseAdminTest {

    @Test
    @Tag("smoke")
    void authInfoReachable() {
        admin()
            .when().get("/api/auth-info")
            .then().statusCode(200);
    }

    @Test
    void currentAccount() {
        int status = admin()
            .when().get("/api/account")
            .then().extract().statusCode();
        // 200 or 404 acceptable until colleagues tighten against OpenAPI spec
        assert status == 200 || status == 404 : "unexpected status: " + status;
    }
}
