# T04: 测试矩阵、fixtures 与 E2E 门禁

**优先级**: P0
**状态**: DRAFT
**依赖**: F2/T05, F3, F4, F5

## 目标

建立 API 接入正式发布前的自动化和手工验收矩阵，避免只靠客户接口临场验证。

## 范围

- 单元测试：contract、provider、parser、schema inference、cursor、redaction。
- 集成测试：mock API、preview、execution、ODS write、checkpoint。
- 前端测试：provider form、preview mapping、sync config、diagnostics。
- E2E：创建 API 数据源、preview、创建任务、运行、查看 ODS 和日志。

## 完成标准

- [ ] 测试矩阵覆盖 full_refresh、incremental、auth fail、rate limit、schema drift。
- [ ] 脱敏断言覆盖日志、响应、job artifact、审计。
- [ ] E2E 有可重复 mock API 环境。
- [ ] 发布前有 IT 证据目录。

