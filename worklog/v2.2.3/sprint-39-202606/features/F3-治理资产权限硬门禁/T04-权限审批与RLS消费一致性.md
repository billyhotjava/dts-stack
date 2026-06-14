# T04: 权限审批与 RLS 消费一致性

**优先级**: P0
**状态**: READY
**依赖**: T03

## 目标

确保资产门户、metrics、BI、大屏、API 服务和数据产品都读取同一 platform 权限事实源。

## 技术设计

- 检查 `asset_grant`、审批流、RLS/masking、metrics policy hash、大屏权限 fallback。
- 默认关闭或标记 legacy local fallback，只保留明确 break-glass。
- 每次消费记录权限快照和策略 hash，便于审计。

## 影响范围

- `source/dts-platform` permission/catalog/services
- `source/dts-metrics` platform contract
- `source/dts-platform-webapp` screens/BI

## 验证

- [ ] 同一用户在资产门户可见性、metrics 预览、BI、大屏/API 消费结果一致。
- [ ] 无权限错误不泄露资产细节。

## 完成标准

- [ ] platform 成为消费权限唯一事实源。
