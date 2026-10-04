# BE-002: 项目管理指挥中心大屏 API

- **优先级**: P0
- **状态**: TODO
- **负责人**: TBD

## 范围

完成 v2.2.1 sprint-13 Phase 1 遗留的聚合接口：
- `GET /api/project-cockpit/screen/header` — 顶部概览数据
- `GET /api/project-cockpit/screen/overview` — 总体指标面板
- `GET /api/project-cockpit/screen/execution` — 执行进展面板
- `GET /api/project-cockpit/screen/risk` — 风险归因面板

## 参考

- `/worklog/v2.2.1/sprint-13/plan.md`
- `ProjectCockpitService.java` 中已有 screenHeader/screenOverview/screenExecution/screenRisk 方法框架

## 交付标准

- [ ] 三屏轮播模板在大屏工厂中可预览
- [ ] 数据来自 Java 聚合接口，非 mock
