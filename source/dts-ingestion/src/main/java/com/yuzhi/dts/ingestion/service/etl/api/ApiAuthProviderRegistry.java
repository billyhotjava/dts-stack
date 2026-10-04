package com.yuzhi.dts.ingestion.service.etl.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ApiAuthProviderRegistry {

    private final List<ApiAuthProvider> providers = List.of(
        provider("none", "无鉴权", "不向请求注入任何鉴权信息", List.of(), false, true),
        provider(
            "apiKey",
            "API Key",
            "通过 header 或 query 参数注入 API Key",
            List.of(
                field("name", "参数名", "text", true, false, "例如 X-API-Key 或 api_key"),
                field("location", "注入位置", "select", true, false, "header 或 query"),
                field("value", "API Key", "password", true, true, "保存为 secretRef，不回显明文")
            ),
            true,
            true
        ),
        provider(
            "bearerToken",
            "Bearer Token",
            "通过 Authorization: Bearer <token> 注入令牌",
            List.of(field("token", "Token", "password", true, true, "保存为 secretRef，不回显明文")),
            true,
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
            true,
            true
        ),
        provider(
            "oauth2ClientCredentials",
            "OAuth2 Client Credentials",
            "使用 OAuth2 client_credentials 换取短时访问令牌",
            List.of(
                field("tokenUrl", "Token URL", "url", true, false, "OAuth2 token endpoint"),
                field("clientId", "Client ID", "text", true, false, "OAuth2 client id"),
                field("clientSecret", "Client Secret", "password", true, true, "保存为 secretRef，不回显明文"),
                field("scope", "Scope", "text", false, false, "可选 scope")
            ),
            true,
            true
        ),
        provider(
            "jwtLogin",
            "JWT 登录换令牌",
            "调用客户登录端点换取短时 JWT，内存缓存并在过期或 401 后重取",
            List.of(
                field("loginUrl", "登录 URL", "url", true, false, "客户应用登录端点"),
                field("loginMethod", "登录方法", "select", false, false, "默认 POST", Map.of("options", List.of("POST", "GET"))),
                field("loginBodyTemplate", "登录报文模板", "textarea", true, false, "使用 {{secretRefs.password}} 等占位符引用密钥"),
                field("tokenPath", "Token JSON 路径", "text", true, false, "例如 $.data.token 或 access_token"),
                field("expiresInPath", "过期秒数字段", "text", false, false, "可选；缺省解析 JWT exp claim"),
                field("tokenPlacement", "Token 注入位置", "select", false, false, "默认 Authorization: Bearer", Map.of("options", List.of("bearer", "header"))),
                field("tokenHeaderName", "Token Header 名", "text", false, false, "tokenPlacement=header 时使用"),
                field("secretRefs", "登录密钥引用", "secretRefMap", false, true, "登录报文模板引用的密钥，保存为 secretRef")
            ),
            true,
            true
        ),
        provider(
            "customSignature",
            "自定义签名",
            "预留国密、HMAC、时间戳签名等客户自定义鉴权，运行时暂未开放",
            List.of(
                field("algorithm", "签名算法", "text", true, false, "例如 HMAC-SHA256、SM2-SIGN"),
                field("keyId", "Key ID", "text", false, false, "可选 key id"),
                field("secret", "签名密钥", "password", true, true, "保存为 secretRef，不回显明文")
            ),
            true,
            false
        ),
        provider(
            "mtls",
            "mTLS",
            "预留双向 TLS 客户端证书鉴权，运行时暂未开放",
            List.of(
                field("certSecretRef", "证书 Secret", "secretRef", true, true, "客户端证书引用"),
                field("keySecretRef", "私钥 Secret", "secretRef", true, true, "客户端私钥引用")
            ),
            true,
            false
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
        boolean supportsRotation,
        boolean enabled
    ) {
        ApiAuthProviderDescriptor descriptor = new ApiAuthProviderDescriptor(id, label, description, fields, supportsRotation, enabled);
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

    private static ApiAuthProviderField field(
        String name,
        String label,
        String type,
        boolean required,
        boolean sensitive,
        String description,
        Map<String, Object> constraints
    ) {
        return new ApiAuthProviderField(name, label, type, required, sensitive, description, constraints == null ? Map.of() : constraints);
    }
}
