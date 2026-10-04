# T03：实现 dbt 产物与模型证据回写

- **状态**：READY
- **优先级**：P0
- **依赖**：T01、T02
- **影响模块**：dbt artifact/import、ModelSpec、lineage、pipeline run、stage evidence

## 目标

把 manifest、compiled SQL、tests、run results 和 lineage 作为实现证据绑定到现有 ModelSpec 和 WarehousePlan，而不是形成第二套模型台账。

## 实施内容

1. 以 project + uniqueId + artifact checksum 幂等登记 dbt 产物。
2. 对已绑定 node 更新实现修订、编译状态、测试结果、运行和血缘引用。
3. 未绑定 node 进入候选队列，人工确认 ModelSpec、粒度和业务范围。
4. Schema/SQL/依赖差异生成 drift 记录并进入发布门禁。
5. 导入和运行失败保留部分结果、correlationId 和可重试边界。

## 验收标准

- 重复 import 不创建重复模型或 artifact；
- 业务语义未经确认不进入正式状态；
- compile/test/run 能追溯到 planId/modelSpecId；
- lineage 与模型依赖一致或明确标记冲突；
- stage projection 可消费最新证据。

## 验证证据

- import 幂等测试；
- artifact-to-model mapping tests；
- lineage/drift consistency tests；
- 失败重试与部分结果测试。
