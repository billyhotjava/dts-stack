# T02：生成准确 alias、物化配置与生命周期 meta

**优先级**：P0
**状态**：DONE
**依赖**：T01（消费 executionPlan）

## 目标

让普通 compiler 生成的 dbt node 保持稳定技术身份，把物理来源和上游模型明确投影为 `source()/ref()`，同时精确生成用户确认的 relation identifier 和可回溯生命周期 meta。

## 技术设计（Contract-first）

- **输入契约**：current ModelSpec/Implementation + T01 `executionPlan`。
- **依赖投影**：PHYSICAL_SOURCE 生成 `source()`；UPSTREAM_MODEL 生成稳定 `ref()`；字段映射、表达式和 JOIN 只引用已验证输入。
- **输出契约**：
  - node name=`model_<modelSpecId compact>`，稳定不随表名变化；
  - dbt config 至少含 `materialized`, `alias`, `meta`;
  - INCREMENTAL 含 `unique_key=[logical KEY fields]`;
  - artifact 的 implementation revision/checksum/current state 正确；
  - compile 阶段 `physicalAssetRef=null`。
- **meta**：`tenantId,planId,modelSpecId,modelRevision,modelChecksum,implementationRevision,implementationChecksum,targetIdentifier,retentionDays`。
- **错误路径**：生成 alias 与 targetPhysicalName 不一致、meta 缺字段或 selector 冲突时 compile 失败，不保存 PASSED event。
- **循环/锁定**：上游 model revision/checksum 必须 current；dbt graph 形成 cycle 或 unresolved ref 时 compile fail-closed。
- **复用点**：`ModelingDbtCompiler`、`CanonicalModelLifecycleCompilerAdapter`、checksum codec。
- **安全**：identifier 由 canonical validator 产生，不接收任意 Jinja/SQL。

## 影响范围

- `ModelingDbtCompiler.java`
- `CanonicalModelLifecycleCompilerAdapter.java`
- compiler/adapter tests

## 验证（RED→GREEN）

- [x] table/view/incremental golden SQL 测试。
- [x] source/ref/JOIN golden files 与循环、未解析依赖 RED 测试。
- [x] targetPhysicalName 改变只改 alias/artifact checksum，不改 canonical model id。
- [x] input sourceBindingId 不再写入输出 physicalAssetRef。
- [x] 同输入两次 compile 内容与 checksum 完全一致。

## Definition of Done

- [x] dbt compile 对代表 DIM/FACT/SUMMARY 通过。
- [x] artifact 能唯一反查 model/implementation revision。
- [x] manifest parent_map/child_map 与 ModelImplementation 上游依赖一致。
- [x] 编译结果不伪装物理资产。

## 完成证据

- 普通模型统一使用真实 dbt project key `dts`，node unique id 与运行 manifest 可强绑定。
- PostgreSQL cast 只允许受控映射：`string→text`、`decimal→numeric`，不再生成无效
  `cast(... as string)`。
- schema 与 column tests 合并为单一 YAML，避免 dbt duplicate model patch。
- cycle 与 missing ref 在 scoped project 准备阶段使用稳定错误码 fail-closed。
- RED/GREEN、真实 dbt 版本和 manifest 断言见
  `../../it/evidence/f1-t02-compiler-artifacts/README.md`。
