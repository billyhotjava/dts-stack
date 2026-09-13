# T03：完成 PJM 真实 E2E 与 Chrome 95 验收

**优先级**: P0  
**状态**: READY  
**依赖**: T01、T02

## 目标

用 PJM 预算模型包在真实认证、PostgreSQL、来源盘点和 dbt artifact 下验证完整导入 Journey。

## 技术设计

- 清理测试业务数据但保留模板/基线数据。
- 建立可编辑计划、项目管理分类和确认的 `public.ods_budget_v2` 来源。
- 验证成功导入、缺语义阻断、来源漂移、幂等重放和并发占用。
- Chrome 95 验证双入口、四步向导、结果跳转和错误恢复。

## 影响范围

- IT fixture、Playwright/Chrome95 脚本与截图。
- 真实 dts-platform/dts-platform-webapp 运行环境。

## 验证

- [ ] FACT、SUMMARY、APPLICATION 及 DBT_BACKED implementation 可见。
- [ ] STG 只在技术图中出现。
- [ ] 数据库、API、页面和审计证据一致。

## 完成标准

- [ ] Journey A-D 全部通过。
- [ ] mock API 截图不得作为最终证据。
