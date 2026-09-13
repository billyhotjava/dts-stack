# T02：绑定 dbt run、selector、target、revision 与 checksum

**优先级**：P0
**状态**：READY
**依赖**：T01

## 目标

验证外部 dbt 运行确实针对当前候选和环境执行，杜绝客户端仅提交一个 runId 即获得通过证据。

## 技术设计

- 服务端读取 invocation/manifest/run_results，校验 selector、target、adapter 和 invocation 终态。
- manifest node 必须覆盖候选条目并匹配 implementation path/checksum。
- 运行证据绑定 candidateId、entryId、environment 和 canonical revision。
- run 未结束、节点缺失、target 不符或 checksum 漂移均 fail closed。

## 影响范围

- `ModelLifecycleCompilerPort.java`
- `CanonicalModelLifecycleCompilerAdapter.java`
- dbt run 查询适配器
- fixture/adapter 集成测试

## 实施步骤

1. 先准备成功、缺节点、错误 target、旧 revision 和失败终态 fixtures。
2. 实现服务端 run 校验和幂等证据写入。
3. 用最小 dbt 项目执行一次真实 compile/build/test。

## 完成标准

- [ ] 伪造、过期或不完整的 externalRunId 无法通过构建门禁。
- [ ] 重复回调不重复创建证据，终态不可被旧状态覆盖。
- [ ] **UI 契约验收**：构建详情能展示 selector、target、invocation、revision 和 checksum；错 target、缺节点或旧 revision 显示明确阻塞而非笼统“构建失败”。
