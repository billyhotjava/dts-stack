# T02: PlatformPermissionFilter 请求拦截

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

在 analytics 侧新增 Servlet Filter，拦截所有资产相关请求并经 platform 权限判定。

## 技术设计

### Filter 逻辑

```java
@Component
public class PlatformPermissionFilter extends OncePerRequestFilter {
    // 1. 从请求头提取用户信息 (X-DTS-User, X-DTS-Roles, X-DTS-Dept-Code)
    // 2. 通过 AssetRefResolver 解析请求涉及的资产
    // 3. 调用 PlatformPermissionClient.check()
    // 4. 无权限 → 403; 有权限 → setAttribute("assetPermission", result) 继续
}
```

### AssetRefResolver — URL → 资产映射

```
/api/card/{id}           → AssetRef(CARD, id)
/api/dashboard/{id}      → AssetRef(DASHBOARD, id)
/api/screen/{id}         → AssetRef(SCREEN, id)
/api/table/{id}          → AssetRef(TABLE, id)
/api/card/{id}/query     → AssetRef(CARD, id)
/api/dataset             → 从 request body 解析 (需缓存 body)
```

### 跳过路径

- `/api/session/**` — 认证相关
- `/api/user/**` — 用户信息
- `/api/collection/**` — 集合列表（内部再过滤）
- `/api/database/**` — 数据库元数据
- `/api/health` — 健康检查

### Filter 顺序

在 `PlatformSessionBridgeFilter` 之后、业务 Controller 之前。

## 影响范围

- `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/filter/` — 新增 PlatformPermissionFilter
- `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/support/` — 新增 AssetRefResolver
- `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/config/SecurityConfiguration.java` — 注册 filter

## 验证

- [ ] 有权请求正常通过
- [ ] 无权请求返回 403
- [ ] 跳过路径不触发权限检查
- [ ] AssetRefResolver 正确解析各种 URL 模式

## 完成标准

- [ ] Filter 拦截全部资产相关路径
- [ ] 权限结果通过 request attribute 传递给下游
