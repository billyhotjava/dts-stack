package com.yuzhi.dts.ingestion.service.etl.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ApiAuthProviderRegistry {

    private final List<ApiAuthProvider> providers = List.of(
        provider("none", "无鉴权", "不向请求注入任何鉴权信息", List.of(), false),
        provider(
            "apiKey",
            "API Key",
            "通过 header 或 query 参数注入 API Key",
            List.of(
                field("name", "参数名", "text", true, false, "例如 X-API-Key 或 api_key"),
                field("location", "注入位置", "select", true, false, "header 或 query"),
                field("value", "API Key", "password", true, true, "保存为 secretRef，不回显明文")
            ),
            true
        ),
        provider(
            "bearerToken",
            "Bearer Token",
            "通过 Authorization: Bearer <token> 注入令牌",
            List.of(field("token", "Token", "password", true, true, "保存为 secretRef，不回显明文")),
            true
        ),
        provider(
            "basic",
            "Basic Auth",
            "使用用户名和密码生成 Basic Authorization header",
            List.of(
                field("username", "用户名", "text", true, false, "Basic Auth 用户名"),
                field("password", "密码", "password", true, true, "保存为 secretRef，不回显明文")
            ),
            true
        ),
        provider(
            "oauth2ClientCredentials",
            "OAuth2 Client Credentials",
            "预留 OAuth2 client_credentials 鉴权扩展点",
            List.of(
                field("tokenUrl", "Token URL", "url", true, false, "OAuth2 token endpoint"),
                field("clientId", "Client ID", "text", true, false, "OAuth2 client id"),
                field("clientSecret", "Client Secret", "password", true, true, "保存为 secretRef，不回显明文"),
                field("scope", "Scope", "text", false, false, "可选 scope")
            ),
            true
        ),
        provider(
            "customSignature",
            "自定义签名",
            "预留国密、HMAC、时间戳签名等客户自定义鉴权",
            List.of(
                field("algorithm", "签名算法", "text", true, false, "例如 HMAC-SHA256、SM2-SIGN"),
                field("keyId", "Key ID", "text", false, false, "可选 key id"),
                field("secret", "签名密钥", "password", true, true, "保存为 secretRef，不回显明文")
            ),
            true
        ),
        provider(
            "mtls",
            "mTLS",
            "预留双向 TLS 客户端证书鉴权",
            List.of(
                field("certSecretRef", "证书 Secret", "secretRef", true, true, "客户端证书引用"),
                field("keySecretRef", "私钥 Secret", "secretRef", true, true, "客户端私钥引用")
            ),
            true
        )
    );

    public List<ApiAuthProviderDescriptor> listDescriptors() {
        return providers.stream().map(ApiAuthProvider::descriptor).toList();
    }

    public Optional<ApiAuthProviderDescriptor> findDescriptor(String id) {
        if (!StringUtils.hasText(id)) {
            return Optional.empty();
        }
        return listDescriptors().stream().filter(descriptor -> descriptor.id().equalsIgnoreCase(id.trim())).findFirst();
    }

    private static ApiAuthProvider provider(
        String id,
        String label,
        String description,
        List<ApiAuthProviderField> fields,
        boolean supportsRotation
    ) {
        ApiAuthProviderDescriptor descriptor = new ApiAuthProviderDescriptor(id, label, description, fields, supportsRotation);
        return () -> descriptor;
    }

    private static ApiAuthProviderField field(
        String name,
        String label,
        String type,
        boolean required,
        boolean sensitive,
        String description
    ) {
        return new ApiAuthProviderField(name, label, type, required, sensitive, description, Map.of());
    }
}

