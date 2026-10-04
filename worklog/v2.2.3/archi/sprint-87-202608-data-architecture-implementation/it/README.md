# Sprint-87 验收证据索引

**状态**：PARTIAL_LOCAL_VERIFY_PASS

| IT | 范围 | 状态 | 证据 |
|---|---|---|---|
| IT-01 | 架构字典授权与单一 command boundary | LOCAL_PASS_NON_E2E | F1/T01；`../assets/implementation-evidence-20260810.md` |
| IT-02 | DAG、批量候选、二次物化和模型资产映射 | LOCAL_PASS_NON_E2E | F2/T01；`../assets/implementation-evidence-20260810.md` |
| IT-03 | 资产来源/状态/统计容量与迁移 | LOCAL_PASS_WITH_BASELINE_TEST_DEBT | F3/T01；证据第 4 节 |
| IT-04 | 指标逐消费者兼容迁移 | LOCAL_PASS_NON_E2E | F4/T01；生产消费者观测待执行 |
| IT-05 | 菜单、旧深链、Chrome 95 与真实浏览器旅程 | BUILD_PASS / REAL_E2E_PENDING | F5/T01、F6/T01 |
| IT-06 | 发布、回滚、观测与 Contract 准入 | BLOCKED_NOT_EXECUTED | F6/T01 |

代码、构建、部署、fixture E2E 和真实用户验收分别登记；缺少真实菜单点击证据时不得标记 REAL/DELIVERED。
