# T01: ServiceDependencyAuthenticationFilter 安全加固

**严重度**: Critical
**文件**: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/security/ServiceDependencyAuthenticationFilter.java`
**关联**: `AssetPermissionInternalResource.java`

## 问题

Filter 仅检查 `X-DTS-Service` header 值是否匹配配置的 service name，即授予 `OP_ADMIN` 权限。
无 shared secret/token 验证。攻击者若能绕过反向代理直达平台服务，可伪造 header 获取管理员权限。

新增的 `AssetPermissionInternalResource` 无 `@PreAuthorize` 注解，使攻击面扩大。

## 修复方案

两个方案（选一）：

**方案 A — 添加 shared secret 验证：**
```java
// 新增 header: X-DTS-Service-Secret
String secret = request.getHeader("X-DTS-Service-Secret");
if (!configuredSecret.equals(secret)) {
    filterChain.doFilter(request, response);
    return; // 不授权
}
```

**方案 B — 在 Resource 层添加 @PreAuthorize：**
```java
@PreAuthorize("hasAuthority('" + AuthoritiesConstants.OP_ADMIN + "')")
public class AssetPermissionInternalResource { ... }
```

推荐方案 A，从根源修复。

## 验证

- 无 `X-DTS-Service-Secret` header 的请求被拒绝
- analytics 服务携带正确 secret 的请求正常通过
