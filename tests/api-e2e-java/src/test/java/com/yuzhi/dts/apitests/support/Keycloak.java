package com.yuzhi.dts.apitests.support;

import static io.restassured.RestAssured.given;

import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;

/** Fetches OIDC access_token via password grant. */
public final class Keycloak {

    private Keycloak() {}

    public static String fetchToken(String usernameEnv, String passwordEnv) {
        String issuer = Env.require("OIDC_ISSUER_URI");
        String clientId = Env.require("OIDC_CLIENT_ID");
        String clientSecret = Env.optional("OIDC_CLIENT_SECRET", "");
        String username = Env.require(usernameEnv);
        String password = Env.require(passwordEnv);

        var req = given()
            .config(RestAssuredConfig.config().sslConfig(Env.verifyTls() ? new SSLConfig() : new SSLConfig().relaxedHTTPSValidation()))
            .contentType("application/x-www-form-urlencoded")
            .formParam("grant_type", "password")
            .formParam("client_id", clientId)
            .formParam("username", username)
            .formParam("password", password)
            .formParam("scope", "openid");
        if (!clientSecret.isBlank()) {
            req = req.formParam("client_secret", clientSecret);
        }

        var resp = req.when().post(issuer.replaceAll("/+$", "") + "/protocol/openid-connect/token");
        if (resp.statusCode() != 200) {
            throw new IllegalStateException(
                "Keycloak password grant failed [" + resp.statusCode() + "]: " + resp.asString()
            );
        }
        return resp.jsonPath().getString("access_token");
    }
}
