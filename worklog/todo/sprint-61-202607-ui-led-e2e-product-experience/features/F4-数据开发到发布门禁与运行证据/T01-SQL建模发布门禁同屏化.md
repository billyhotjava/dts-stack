# T01: SQL 建模发布门禁同屏化

**优先级**: P0
**状态**: READY
**依赖**: F3/T02

## 目标

在 SQL 建模页明确显示发布前的标准、质量、权限、dbt 编译/测试门禁。

## 技术设计

- 在 `SqlModelingPage.tsx` 右侧或底部增加发布门禁汇总卡。
- 聚合现有标准门禁、schema.yml、dbt compile/test/run、发布审核状态。
- 阻断项显示修复按钮，成功项显示证据入口。

## 影响范围

- `SqlModelingPage.tsx`
- `sqlModelReleaseSubmit.helpers.ts`
- `dataDevelopmentWorkbench.source-contract.test.ts`

## 验证

- [ ] source-contract 断言发布门禁卡和按钮。
- [ ] 单测覆盖 release submit blocked/warning/submitted。

## 完成标准

- [ ] 用户不用切到多个页面判断模型能否发布。
