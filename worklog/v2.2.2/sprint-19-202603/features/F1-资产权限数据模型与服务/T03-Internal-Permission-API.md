# T03: Internal Permission API

**优先级**: P0
**状态**: READY
**依赖**: T02

## 目标

暴露内部权限判定 API，供 analytics 等内部服务调用。

## 技术设计

### 端点

```
POST /api/internal/asset-permission/check
POST /api/internal/asset-permission/batch-check
POST /api/internal/asset-permission/accessible-ids
```

### 认证机制

通过 Docker 内部网络直连 `dts-platform:8081`，认证方式：
- 现有 `ServiceDependencyAuthenticationFilter` 校验 `X-DTS-Service` 头
- `/api/internal/**` 路径在 SecurityConfiguration 中仅允许通过 service 认证的请求
- 额外校验：仅允许来自 Docker 内部网段 (172.x.x.x) 的请求

### batch-check 限制

单次请求最多 200 个资产，超过返回 400。

### accessible-ids 响应

```json
{
    "assetIds": ["3", "7", "12"],
    "total": 3,
    "scope": "FILTERED"
}
```

全局角色返回:
```json
{
    "assetIds": [],
    "total": 0,
    "scope": "ALL"
}
```

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/internal/` — 新增 AssetPermissionInternalResource
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/config/SecurityConfiguration.java` — 新增 `/api/internal/**` 路径规则

## 验证

- [ ] 三个端点功能正确
- [ ] 非 service 认证请求返回 401
- [ ] batch-check 超限返回 400
- [ ] accessible-ids 全局角色返回 scope=ALL

## 完成标准

- [ ] API 契约与设计文档一致
- [ ] 集成测试验证 analytics → platform 调用链路
