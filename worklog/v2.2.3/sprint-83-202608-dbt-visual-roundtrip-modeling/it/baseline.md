# 交付基线（Gate G0）

**状态**：PENDING  
**原因**：本轮只进行架构评审和 Sprint 设计，未开始编码；按照用户约定不在计划迭代期间反复执行 E2E。

编码前一次性执行并记录：

| # | 探针 | 当前状态 | 通过条件 | 阻断 Task |
|---|---|---|---|---|
| P1 | dts-platform 可运行实例 | PENDING | health=UP | F0/T03 |
| P2 | 真实登录/受保护页面 | PENDING | 建模维护者进入模型工作台 | F0/T03 |
| P3 | schema/changelog | PENDING | Sprint-81 canonical/import 表结构与源码一致 | F0/T03 |
| P4 | 代表 dbt fixture | GAP | FX-01～05 可用 | F0/T02 |
| P5 | API harness | PENDING | inspect→preview→apply 在隔离计划成功 | F0/T03 |
| P6 | UI harness | PENDING | Chrome95 能登录、上传并截图 | F0/T03 |
| P7 | 构建/测试命令 | PENDING | platform focused Maven + webapp build 可运行 | F0/T03 |
| P8 | Airflow/dbt/PostgreSQL | PENDING | DbtExecutionGateway 代表链可验收 | F0/T03 |

受影响 Feature 在 P1～P8 完成前保持 DRAFT。
