# T01：建立 QualityRun 与 QualityCheckResult 契约

**优先级**：P0
**状态**：READY
**依赖**：F1-T04

## 目标

建立独立的质量运行、规则级结果和失败样本引用，结束 quality/tests 共用一条 TEST 事件的临时语义。

## 技术设计

- QualityRun 保存 candidate、entry、rulePackVersion、dataSnapshotAt、runId、状态和统计。
- QualityCheckResult 保存 ruleId/version、dimension、severity、threshold、observedValue、result 和 sampleRef。
- sample 仅保存脱敏摘要与受控引用，限制数量、大小和保留期。
- 旧 TEST 证据保留展示但不能满足新发布门禁。

## 影响范围

- 新增 quality contract/entity/repository
- 扩展 Liquibase changeset
- `ModelLifecycleTestEvidencePort.java`
- contract/repository 测试

## 实施步骤

1. 先写多规则、部分失败、敏感样本和旧证据不兼容测试。
2. 编写持久化与 contract，定义容量和脱敏约束。
3. 编写 quality 与 Liquibase rollback 测试源码并完成静态审查；运行验证留到 F6。

## 完成标准

- [ ] 一次运行及每条规则的输入、结论和来源均可审计。
- [ ] 大结果、明文敏感数据和无版本规则不能写入证据。
- [ ] **UI 契约验收**：质量证据抽屉可按运行和规则展示阈值、实际值、严重度、结果及脱敏样本摘要，并覆盖 running、partial、failed、passed。
