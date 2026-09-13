# Asset Portal Evidence

**Sprint**: 31A -> 31B -> 32 final IT 共用
**Owner**: Platform webapp owner
**触发命令**: Sprint-32 final IT 中执行数据资产中心、资产详情、数据产品和 dts-metrics 菜单跳转 smoke tests
**预期输出**: 前端页面可见、可执行核心动作、可处理空态/错误态/无权限态
**目前状态**: PENDING，待 Sprint-32 final IT 写入实际日志

## Linked Scope

- Sprint-31A F5: `worklog/v2.2.3/sprint-31a-202605/features/F5-asset-portal-ux/README.md`
- Sprint-31B F6: `worklog/v2.2.3/sprint-31b-202605/features/F6-frontend-acceptance-recovery/README.md`

## Evidence To Capture

- `/catalog/datasets` 可处置 resolver failure、治理缺口和血缘失败。
- `/catalog/datasets/:id` 使用 assets-v2 contract/schema/governance/lineage。
- 数据产品页面可配置成员资产、核心指标、SLA、密级和消费入口。
- platform 只保留 metrics 菜单/路由入口，业务页面跳转到 `/metrics/**`。
