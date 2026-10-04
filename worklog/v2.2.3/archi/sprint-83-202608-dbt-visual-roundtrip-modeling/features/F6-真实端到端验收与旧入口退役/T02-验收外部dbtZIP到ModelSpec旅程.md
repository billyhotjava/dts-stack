# T02：验收外部 dbt ZIP → ModelSpec DRAFT 旅程

**优先级**：P0
**状态**：E2E_PENDING
**依赖**：F3/T01～T04、F5/T01～T03

## 目标

用真实认证用户完成上传、检查、映射、预检、冲突确认、apply/retry，并跳转精确 ModelSpec/Implementation revision。

## 验收路径

1. 进入逆向建模，选择 dbt 项目包。
2. 上传 FX-01，查看项目/依赖/字段/capability。
3. 映射 WarehousePlan/域/来源并补业务语义。
4. 查看 CREATE/UPDATE/SKIP/CONFLICT/BLOCKED 与 closure。
5. apply，刷新恢复，核对逐项结果。
6. 默认进入普通业务可视化，确认 SQL/dbt 技术正文不可见并校验 revision/checksum；技术维护者可在同一模型详情显式进入高级 dbt 实现。

## 证据

- [ ] Chrome95 四态截图、API 摘要、PostgreSQL run/attempt/result/ModelSpec/Implementation checksum。
- [ ] dts-admin inspect/preview/apply 审计，无敏感正文。
- [ ] 重复上传和 partial+retry 结果。

## Definition of Done

- [ ] 导入只产生 DRAFT，不创建可消费 Catalog 资产或绕过门禁。
