# T04：完成构建回滚与最终 Go/No-Go

**优先级**：P0
**状态**：READY
**依赖**：T03

## 目标

验证可发布制品、数据库升级/回滚和运行回滚，并以六层证据给出最终 Go/No-Go。

## 技术设计

- 前端执行 legacy production build，后端执行 jar/build gate。
- 数据迁移验证 upgrade、rollback、re-upgrade 和既有数据兼容。
- 产品回滚验证目标发布、注册补偿、StageProjection 和审计。
- 报告分列 code/test/migration/deploy/browser/rollback，不用单一 DONE 掩盖缺口。

## 影响范围

- `it/evidence/build/`
- `it/evidence/migration/`
- `it/evidence/rollback/`
- `it/go-no-go.md`
- Sprint/Feature/Task 状态回填

## 实施步骤

1. 执行后端 unit/jar、前端 `pnpm build`、Liquibase 和 compose smoke。
2. 执行应用回滚、数据库 rollback/re-upgrade 和浏览器复核。
3. 运行 `git diff --check`、敏感信息扫描和证据完整性检查。

## 完成标准

- [ ] 六层均为 PASS 才能将 Sprint 标记 DONE；缺真实部署或浏览器证据时保持 IN_PROGRESS。
- [ ] 部署、回滚步骤和已知风险可由另一名工程师独立复现。
- [ ] **UI 真实验收**：部署制品展示正确版本，升级、重启、应用回滚和数据库 re-upgrade 后工作台状态不漂移，并保存回滚前后页面证据。
