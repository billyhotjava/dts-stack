# F2: 接入入湖到建模产品闭环

**优先级**: P0
**状态**: READY

## 目标

把数据源、入湖任务、ODS、dbt source、DWD/DWS/ADS 模型和发布门禁串成默认产品流程，降低手工导入 dbt 模型的依赖。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 数据源入湖任务归一化 | P0 | READY | F1-T01 |
| T02 | ODS 到 dbt source 契约自动化 | P0 | READY | T01 |
| T03 | DWD/DWS/ADS 发布门禁 | P0 | READY | T02 |
| T04 | 存量手工 dbt 导入迁移清单 | P1 | READY | T03 |

## 完成标准

- [ ] JDBC/API/file 均可进入同一入湖到建模主流程。
- [ ] ODS 产物具备 dbt source 注册或生成入口。
- [ ] DWD/DWS/ADS 发布前必须通过 compile/test/build 与治理快照。
