# T03：绑定 dbt artifact 与技术依赖

**优先级**: P0  
**状态**: IN_PROGRESS  
**依赖**: T02

## 目标

复用现有 dbt artifact 导入与所有权校验，把 DBT_BACKED 模型和 technicalNodes 绑定到同一实现图。

## 技术设计

- 拆分现有 importer 的解析与 apply 责任，preview 可独立复用解析结果。
- 对 canonical 模型复用 node 唯一归属、CAS、幂等 artifact upsert。
- 保存 SQL/schema/config/dependency checksum，不把 import 结果直接冒充真实 compile/test 成功。
- STG/ephemeral 作为技术 artifact/依赖节点存在，不创建 ModelSpec。

## 影响范围

- `ModelingDbtManifestImporter`、`ModelingVNextApplicationService`。
- dbt artifact repository 与证据投影。

## 验证

- [ ] 同一 dbt node 不可被两个 ModelSpec 占用。
- [ ] technical STG 可被下游编译图解析。
- [ ] 导入状态与真实 compile/test 状态分离。

## 完成标准

- [ ] artifact 与 ModelSpec/implementation revision/checksum 完全匹配。
- [ ] 旧 `/vnext/dbt/import` 行为有回归测试。
