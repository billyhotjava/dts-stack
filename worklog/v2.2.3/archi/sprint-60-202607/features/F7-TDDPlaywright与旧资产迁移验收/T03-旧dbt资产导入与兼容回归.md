# T03: 旧 dbt 资产导入与兼容回归

**优先级**: P0
**状态**: READY
**依赖**: F1-T03,F5-T02

## 目标

验证旧 PJM dbt 项目可被导入新模型台账，且旧语义页面和运行入口继续可用。

## 技术设计

- 使用 `worklog/v2.2.3/s10/v4/pjm/dbt_model` 执行 `dbt parse` 和 manifest 导入。
- 不执行生产 `dbt run`，集成测试使用测试 PostgreSQL 或 fake adapter。
- 旧模型标记 `LEGACY_READONLY`，校验可查看、可运行、不可被设计器覆盖。

## 影响范围

- dbt importer integration tests。
- 旧 `/api/semantic/*` regression tests。
- `it/evidence/dbt/` 和 `it/evidence/api/`。

## 验证

- [ ] 旧 PJM 模型能够登记模型、字段、来源和层级。
- [ ] 旧接口字段没有净减少。
- [ ] 新版本删除操作不触碰旧 dbt 文件。

## 完成标准

- [ ] 形成迁移报告，包含成功、警告和阻断项。
