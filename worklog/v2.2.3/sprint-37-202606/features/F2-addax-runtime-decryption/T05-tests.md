# T05: F2 单元/集成测试与 F1 互通

**优先级**: P0
**状态**: READY
**依赖**: T01-T04

## 目标

汇总 F2 测试，验证 runner 解密链路与 F1 加密 fixture 字节级互通，覆盖率 ≥80%（解密/路径改写/擦除/失败分支）。

## TDD 测试先行（RED）

测试已在 T01-T04 先行；本 task 补集成与互通：

- **F1↔F2 互通**：直接用 F1-T05 导出的 `it/fixtures/` 加密样例，runner 解密还原 == 原文件（跨模块契约测试）。
- DAG 渲染快照测试：tmpfs/TMPDIR/密钥环境出现且参数正确。
- 端到端（容器级，归 F3）：本 task 仅到 runner + DAG 文本单测层。
- 大文件解密内存占用评估（tmpfs size 上限触发的行为）。

## 技术设计（GREEN）

- runner：JUnit5 纯 Java，无容器依赖。
- DAG：字符串/快照断言。
- 把 runner 测试纳入 `tests/run_gates.sh`（runner maven 模块 `services/dts-airflow/runner`）。

## 影响范围

- `services/dts-airflow/runner/src/test/java/com/yuzhi/dts/addax/AddaxEnvRunnerTest.java`
- `source/dts-ingestion/src/test/java/.../AirflowDagServiceTest.java`
- `tests/run_gates.sh`（runner 模块门禁）

## 验证

- [ ] F1 fixture 经 F2 解密字节级一致。
- [ ] DAG 渲染含 tmpfs/TMPDIR/密钥。
- [ ] F2 覆盖率 ≥80%，失败分支覆盖。

## 完成标准

- [ ] F1↔F2 加密格式契约由测试锁定，防止单方漂移。
