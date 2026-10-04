# T01: service-auth、RBAC 和权限边界

**优先级**: P0
**状态**: READY
**依赖**: F2,F4

## 目标

确保 `dts-metrics` 调用 platform internal API 时使用服务鉴权，同时所有用户动作仍按用户权限上下文校验。

## 技术设计

- 服务间调用使用 `X-DTS-Service: dts-metrics` 和 `X-DTS-Service-Token`。
- 用户上下文转发 `X-DTS-User`、`X-DTS-Roles`、`X-DTS-Dept-Code`、`X-DTS-Personnel-Level`。
- platform allowlist 只开放 visual assets、asset permission、policy resolve、validation、release、BI/lineage、audit。
- `dts-metrics` 不写 platform `asset_grant`。

## 影响范围

- `source/dts-platform` service-auth filter / allowlist
- `source/dts-metrics` PlatformContractClient
- `source/dts-metrics/src/test/**`

## 验证

- [ ] 缺 service token 返回 401。
- [ ] endpoint 不在 allowlist 返回 403。
- [ ] 用户无权读取资产时 preview/publish 被阻断。

## 完成标准

- [ ] 服务权限和用户权限都被验证，不互相替代。
