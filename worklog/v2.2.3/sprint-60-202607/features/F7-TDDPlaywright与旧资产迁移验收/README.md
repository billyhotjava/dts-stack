# F7: TDD、Playwright 与旧资产迁移验收

**优先级**: P0
**状态**: IN_PROGRESS（TDD、构建、真实域名 Playwright 已通过；真实租户/Chrome95/外部运行待验收）

## 目标

用 TDD 和真实浏览器黄金路径证明新版本前端、API、后端、dbt 和运行编排完整可用，并验证旧 dbt 项目可导入。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 前端/API/后端单元与集成 RED-GREEN | P0 | DONE | F2,F3,F4,F5 |
| T02 | PJM Playwright 黄金路径 | P0 | IN_PROGRESS | T01,F6 |
| T03 | 旧 dbt 资产导入与兼容回归 | P0 | DONE | F1-T03,F5-T02 |
| T04 | Chrome95、构建、覆盖率与发布证据 | P0 | IN_PROGRESS | T01,T02,T03 |

## 完成标准

- [x] 当前新增生产变更均有先失败测试、最小实现和通过证据；真实域名浏览器已通过。
- [x] Playwright 覆盖普通用户对象台账和高级开发模型台账两条路径（当前数据由 route fixture 注入）。
- [ ] 旧接口、旧模型和新版本不发生净功能回退。
- [ ] Sprint IT 目录包含可重复命令、结果和截图/JSON。
