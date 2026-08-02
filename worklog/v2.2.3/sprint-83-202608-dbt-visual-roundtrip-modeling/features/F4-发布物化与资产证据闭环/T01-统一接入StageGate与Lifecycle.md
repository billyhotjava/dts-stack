# T01：统一接入 StageGate 与 Lifecycle

**优先级**：P1
**状态**：DRAFT  
**依赖**：F1/T03，以及 F2/T03 或 F3/T04 至少一个已固定 ImplementationRevision 生产者

## 目标

让高级 checkpoint 和 dbt 导入结果只创建 DRAFT/Implementation Revision，并通过现有门禁和状态机推进。

## Contract-first

- **输入**：modelSpecId、modelRevision、implementationRevision/checksum、targetStage、expectedVersion、idempotencyKey。
- **输出**：StageGateDecision、evidenceRefs、violations、lifecycle version/state。
- **错误路径**：revision/implementation 不匹配、缺标准/来源/质量/权限/解析证据为 BLOCKED/409/422；resource 不直接写状态列。
- **复用**：ModelSpecStageGateService、ModelLifecycleService、公共 audit outbox。

## 验证

- [ ] DESIGNER_GENERATED/DBT_MANAGED 使用相同门禁入口。
- [ ] 导入结束不会自动进入 IMPLEMENTATION_READY/RELEASE_READY。

## Definition of Done

- [ ] 无快捷 API 复制门禁或 lifecycle 状态机。
