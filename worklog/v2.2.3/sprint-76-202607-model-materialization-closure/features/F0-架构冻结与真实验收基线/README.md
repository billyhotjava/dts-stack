# F0：架构冻结与真实验收基线

**优先级**：P0
**状态**：DONE（2026-07-27；DEV/TEST 实施 GO，PROD NO-GO）

## 目标

在业务代码开始前冻结物化定义、快捷入口 owner、Airflow 唯一调度真值、dbt/Airflow 双图、正确运行落账、单 target/secret 门禁和真实验收路径，使后续任务不再边编码边决定“谁负责建表、如何持续计算、什么算成功”。

## 契约定义

| 类型 | 契约 | 关键字段/结果 |
|---|---|---|
| 基线 | `it/baseline.md` | P1～P8 实测，四目标关系 RED |
| 领域 | `assets/domain-profile.md` | 统一语言、不变量、真实数量 |
| 架构 | Sprint ADR-76-01～36 | owner、状态、DAG、调度真值、落账顺序、凭据、Publish Intent、原子发布、上线投影、失败边界 |
| 评审 | `assets/architecture-review-agenda.md` | GO/REVISE/NO-GO |
| NFR | `assets/nfr-budget.md` | 每项有可执行 fitness function |

## UI/UX 规格

本 Feature 不改 UI，但必须确认后续唯一控制面：

- 模型详情和高级页可提供“构建/提交上线”，但只调用 Build/Publish Intent；
- Publish Intent 只推进质量与提交审核；reviewer/operator candidate commands、运行与 allowedActions 仍由既有计划级交付控制面拥有；
- 单模型快捷入口和批量工作台必须读取同一 candidate/run；
- 不新增菜单、路由或第二套物化页。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 复测验收环境并冻结 RED 数据集 | P0 | DONE | - |
| T02 | 完成架构方向完整性与扩展性复审 | P0 | DONE | T01 |

## Definition of Ready

- [x] 可验收目标已定义。
- [x] 初始 API、数据和运行 seam 已写入 Context Ledger。
- [x] UI 唯一入口已命名。
- [x] 每个缺口有归属 Task。
- [x] Airflow/credential 当前事实与生产 No-Go 已归属 F2/T04、F7/T02。
- [x] 架构复审结论为 DEV/TEST 实施 GO；生产维持 NO-GO。

## 完成标准

- [x] P1～P8 基线已归档；登录、Chrome95 与构建沿用同日有效证据，不做无漂移重复执行。
- [x] 四个代表目标的初始 relation absence 可重复证明。
- [x] 架构复审已接受 ADR-76-01～39 及生产门槛边界。
- [x] F1 已按依赖进入实施；其余 Feature 仍按前置结果逐项转 READY/IN_PROGRESS。
