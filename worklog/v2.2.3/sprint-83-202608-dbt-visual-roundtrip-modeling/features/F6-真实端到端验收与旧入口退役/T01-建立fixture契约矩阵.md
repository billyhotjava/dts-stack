# T01：建立 dbt/adapter/恶意包 fixture 契约矩阵

**优先级**：P0  
**状态**：DRAFT  
**依赖**：F0/T02、F1

## 目标

把已确认的 dbt Core、manifest schema、adapter、资源类型和攻击包固定为可重复的 contract/integration fixtures。

## Contract-first

- **矩阵维度**：artifact-rich/source-only/complex/blocked/drift/malicious × 版本 × adapter。
- **断言**：capability、issues、uniqueId、dependency、field provenance、checksum、性能/限制、清理结果。
- **失败路径**：未声明版本或 adapter 不得被测试“顺便支持”；返回明确 UNSUPPORTED/BLOCKED。
- **数据**：fixture 无客户敏感信息且有生成说明。

## Definition of Done

- [ ] 每项可在 CI/隔离环境产生确定红绿结果。
- [ ] 兼容范围与客户文档完全一致。
