# T01：冻结 ModelRepresentationView 契约

**优先级**：P0  
**状态**：DRAFT  
**依赖**：F0/T04

## 目标

定义一个只读、版本固定的聚合契约，把 ModelSpec、Implementation、dbt artifact 和运行观测返回给 F2/F3，而不成为新的持久化 owner。

## Contract-first

- **输入**：`modelSpecId: UUID`、`modelRevision: int`、`implementationRevision: int`；tenant 从服务端解析。
- **输出**：ID/checksum、ownershipMode、visualizationCapability/reasons、logicalModel、dbtStructure、runtimeObservation、drift/ETag。
- **错误路径**：模型/实施不存在 404；跨租户 404/403；revision 不匹配 409；artifact 缺失返回 capability=BLOCKED，不填充猜测数据。
- **数据流**：owner-side read ports → assembler → REST DTO；禁止跨域 repository/entity import。

## 验证

- [ ] JSON schema/DTO 契约测试。
- [ ] 历史 revision 重读内容与 checksum 稳定。
- [ ] 跨租户和缺失 artifact fail-closed。

## Definition of Done

- [ ] 不新增业务表；所有字段均有 owner/provenance。
- [ ] F2/F3 不直接调用各底层 service 拼装同一信息。
