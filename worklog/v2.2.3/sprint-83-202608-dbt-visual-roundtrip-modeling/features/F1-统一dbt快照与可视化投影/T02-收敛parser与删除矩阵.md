# T02：收敛 manifest/catalog/source parser 与删除矩阵

**优先级**：P0  
**状态**：DRAFT  
**依赖**：T01

## 目标

指定现有 `ModelPackage` converter/projection 为规范化 seam，迁移重复消费者，并明确哪些 parser 保留为 owner adapter、哪些在 caller=0 后删除。

## Contract-first

- **输入**：账本 CL-09、CL-10、CL-15 与 FX-01～03。
- **输出**：consumer → normalized field mapping；保留/适配/删除矩阵；版本兼容与 reason code。
- **错误路径**：消费者需要 schema 未提供的字段时，先扩展同一 ModelPackage/owner projection；禁止复制正则或 uniqueId 算法。
- **安全**：source-only 解析不执行模型 SQL、不下载 packages、不读取 profiles secrets。

## 验证

- [ ] 同一 fixture 经导入、诊断、血缘得到一致 uniqueId/依赖/字段/materialization。
- [ ] 退役项有 GitNexus caller=0 和迁移契约证据。

## Definition of Done

- [ ] 只有一个 normalized projection seam。
- [ ] 不保留长期双写、feature flag 或隐藏兼容 parser。
