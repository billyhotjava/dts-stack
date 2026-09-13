# T04: Chrome95、构建、覆盖率与发布证据

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: T01,T02,T03

## 目标

完成 Sprint-60 的统一验收和发布门禁，确保文档、代码和运行证据一致。

## 技术设计

- 前端 `pnpm exec tsc --noEmit`、source-contract、`pnpm build`。
- 后端 targeted unit/integration test、compile、Liquibase migration。
- dbt `dbt parse`、生成物 `dbt compile`、测试命令。
- Playwright Chrome 95 smoke 和截图。
- `git diff --check`、GitNexus detect changes（进入提交前执行）。

## 影响范围

- `worklog/v2.2.3/sprint-60-202607/it/README.md`
- 所有 evidence 子目录。
- Sprint README 完成记录。

## 验证

- [x] 前端 TSC、生产构建、后端 targeted test、真实域名 Playwright 命令和结果已记录。
- [x] 未执行项已明确为 BLOCKED：Chrome 95 专用浏览器、真实租户 vNext 数据初始化、完整外部运行回调。
- [ ] 发布前确认无未预期的菜单、API、数据库和运行链路影响。

## 完成标准

- [ ] Sprint-60 才能从 READY 改为 DONE；代码提交前不得提前标记完成。
