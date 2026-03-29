package com.yuzhi.dts.platform.service.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yuzhi.dts.platform.service.admin.gateway.auth.AdminAuthGateway;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AdminAuthClient {

    private final AdminAuthGateway gateway;

    public AdminAuthClient(AdminAuthGateway gateway) {
        this.gateway = gateway;
    }

    public LoginResult login(String username, String password) {
        AdminAuthGateway.LoginResult result = gateway.login(username, password);
        return new LoginResult(
            result.user(),
            result.accessToken(),
            result.refreshToken(),
            result.accessTokenExpiresIn(),
            result.refreshTokenExpiresIn()
        );
    }

    public void logout(String refreshToken) {
        gateway.logout(refreshToken);
    }

    public RefreshResult refresh(String refreshToken) {
        AdminAuthGateway.RefreshResult result = gateway.refresh(refreshToken);
        return new RefreshResult(
            result.accessToken(),
            result.refreshToken(),
            result.accessTokenExpiresIn(),
            result.refreshTokenExpiresIn()
        );
    }

    public record LoginResult(
        Map<String, Object> user,
        String accessToken,
        String refreshToken,
        Long accessTokenExpiresIn,
        Long refreshTokenExpiresIn
    ) {}

    public record RefreshResult(String accessToken, String refreshToken, Long accessTokenExpiresIn, Long refreshTokenExpiresIn) {}

    public record ApiEnvelope<T>(
        @JsonProperty("status") String status,
        @JsonProperty("message") String message,
        @JsonProperty("data") T data
    ) {
        public boolean isSuccess() {
            return status != null && ("SUCCESS".equalsIgnoreCase(status) || "OK".equalsIgnoreCase(status) || "200".equals(status));
        }
    }
}
