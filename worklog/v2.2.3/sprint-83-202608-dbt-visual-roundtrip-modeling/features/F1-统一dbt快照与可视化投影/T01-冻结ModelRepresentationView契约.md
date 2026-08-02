# T01：冻结 ModelRepresentationView 契约

**优先级**：P0  
**状态**：DRAFT  
**依赖**：F0/T04

## 目标

定义一个只读、版本固定的聚合契约，把 ModelSpec、Implementation、dbt artifact 和运行观测返回给 F2/F3，而不成为新的持久化 owner。

## Contract-first

- **输入**：`modelSpecId: UUID`、`modelRevision: int`、`implementationRevision: int`、`representationScope: BUSINESS | TECHNICAL`；tenant 与 authority 从服务端解析。
- **输出**：ID/checksum、ownershipMode、visualizationCapability/reasons、logicalModel、dependencyProjection、latestPublishedRef、servingRef、runtimeObservation、previewCapability、drift/ETag；仅 TECHNICAL scope 可返回受控 technicalImplementation。
- **预览边界**：表示读模型只返回证据、能力与“加载样例”可用性，不嵌入样例行。BUSINESS 只能得到 servingRef；TECHNICAL 在维护者权限下可得到成功 candidate capability，但仍通过统一 physical-preview 契约读取脱敏行。
- **最小披露**：BUSINESS scope 的响应 schema 根本不定义 SQL/Jinja、macro、project path、compiled SQL、dbt 文件树或完整技术 DAG 字段，不以 null/CSS 隐藏代替服务端裁剪。
- **错误路径**：模型/实施不存在 404；跨租户 404/403；revision 不匹配 409；artifact 缺失返回 capability=BLOCKED，不填充猜测数据。
- **数据流**：owner-side read ports → assembler → REST DTO；禁止跨域 repository/entity import。

## 验证

- [ ] JSON schema/DTO 契约测试。
- [ ] 历史 revision 重读内容与 checksum 稳定。
- [ ] 跨租户和缺失 artifact fail-closed。
- [ ] BUSINESS/TECHNICAL scope 的权限、字段差异和序列化快照测试。
- [ ] latestPublishedRef 与 servingRef 不同的 r2-published/r1-serving fixture；样例值在表示 DTO 中为 0。

## Definition of Done

- [ ] 不新增业务表；所有字段均有 owner/provenance。
- [ ] F2/F3 不直接调用各底层 service 拼装同一信息。
