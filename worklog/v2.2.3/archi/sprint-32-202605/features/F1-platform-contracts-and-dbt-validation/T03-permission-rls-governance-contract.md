# T03: 权限、RLS 与治理解析契约

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

保证预览、验证和发布前都能通过 platform 校验资产权限、RLS 策略、术语、数据标准和主题域。

## 技术设计

- 复用或固化 `/api/internal/asset-permission/check`。
- 复用或固化 `/api/internal/v1/asset-permission/policy`。
- 复用 domain/glossary/data-standard resolver。
- 返回 predicate hash、policy source、missing/ambiguous/inactive 诊断。

## 影响范围

- `source/dts-platform` internal resolver and permission API
- `source/dts-metrics` validation precheck

## 验证

- [ ] 无权限 source asset 阻断 artifact 生成。
- [ ] RLS predicate 注入结果可审计。
- [ ] glossary/data-standard 不存在或 inactive 时阻断发布。

## 完成标准

- [ ] 所有 graph validation 结果都能显示到具体节点或字段。
