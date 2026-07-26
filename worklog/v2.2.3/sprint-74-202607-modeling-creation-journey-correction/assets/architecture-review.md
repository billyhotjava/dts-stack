# Sprint-74 架构复审

**复审日期**：2026-07-26  
**复审范围**：目标旅程、对象所有权、阶段门禁、DataWorks 分层适配、dbt 边界、存量兼容、发布治理  
**结论**：CONDITIONAL_PASS——方向和对象边界成立；在用户确认本复审结论、G0 恢复且在途 Sprint-73 变更完成影响审计前，不得编码

## 1. 结论摘要

本 Sprint 不是给现有长表单增加解释，而是纠正顺序：

```text
业务目的/每行含义
  → 模型类型
  → 逻辑设计（可独立完成）
  → 实现方式（普通或 dbt）
  → 发布控制面
  → 发布结果/物理资产
```

该顺序与 DataWorks “规划/建模后再发布物化”、Kimball “业务过程→粒度→维度→事实”的原则一致，同时保留 DTS 已有四层 canonical 对象，不引入平行控制面。

## 2. 关键问题复审

| 问题 | 原设计 | Sprint-74 结论 | 判定 |
|---|---|---|---|
| 新建为什么容易选错 | 默认 FACT | 不设默认，先做业务目的决策 | PASS |
| 逻辑建模为何要求数据实现 | 无 DESIGNED，进入实现门禁混入逻辑页 | DESIGNED 可独立完成；只有要发布才进入实现 | PASS |
| 数据实现是什么 | 来源、上游、字段映射与逻辑字段混放 | 唯一 ModelImplementation revision，负责“如何生成” | PASS |
| 物理资产是什么 | 页面同时承担 dbt 入口 | 改为“发布结果”，只读展示真实对象和证据 | PASS |
| 物理资产是否挂钩 dbt | 被 UI 暗示为是 | 否；dbt 只是实现方式之一 | PASS |
| DataWorks DIM 如何处理 | DIMENSION 固定 DWD | 按计划 layer scheme 决定，兼容 classic 和 DataWorks | PASS |
| 九项缺口哪些必须修 | 所有 gate 一次显示 | 只显示当前下一道门禁；未来项不计数 | PASS |
| 财务项目误建如何恢复 | 只能继续填或重建 | DRAFT 无实现时 preview/apply 追加 revision 改型 | PASS |

## 3. 架构一致性检查

### 3.1 唯一所有者

- 逻辑正文仍归 `ModelSpecRevision`；
- 实现配置归既有 `ModelImplementationRevision`；
- dbt 继续使用同一 implementation ownership，不新建 dbt 模型台账；
- 发布继续由 Sprint-69 ReleaseCandidate 管理；
- 物理结果继续消费 lifecycle/artifact/catalog registration。

结论：通过 domain-dts A4“禁止平行实现”。

### 3.2 分层策略

不直接把所有 DIMENSION 从 DWD 批量迁到 DIM，而是使用已有 `WarehousePlanPolicy.layerPolicyCode` seam：

- 经典方案保持兼容；
- DataWorks 方案显式选择；
- 目标 layer 由服务端解析，前端只展示；
- 历史 plan/model revision 不静默改写。

该结论局部替代 Sprint-73“保持 DIMENSION→DWD”的全局硬编码决定，但保留它对经典四层计划的兼容结果。除分层策略外，Sprint-73 的 DataMart、DimensionDefinition 和 ModelSpec 关系不被改写。

结论：比全局硬切 DIM 风险更低，也比永久硬编码 DWD 更符合用户预期。

### 3.3 阶段门禁

`DESIGNED` 是本次架构中不可删除的核心。没有它，页面仍会把“逻辑完成”和“有实现输入”混为同一件事。主动作必须读取服务端 gate，而不是只依赖前端 dirty/configured 状态。

结论：通过，但 F2/T02/F2/T03 必须作为同一提交批次完成。

### 3.4 在途 Sprint-73 冲突

当前未提交代码把 `implementationPolicy` 加入 ModelSpec 及逻辑页。它和 ADR-74-04 冲突。Sprint-74 不回退用户变更，但实施前必须：

1. 运行 GitNexus impact；
2. 明确哪些改动尚未落库；
3. 把实现设置迁入 `ModelImplementation.settings`；
4. 为已经存在的 snapshot 提供读取适配；
5. 禁止用 UI 文案掩盖所有权冲突。

结论：这是实施前阻断项，不影响本 Sprint 设计本身。

## 4. 风险与控制

| 风险 | 等级 | 控制 |
|---|---|---|
| `DIM` 扩展波及分层校验和 dbt 导入 | 高 | 按计划策略解析；实施前对 Layer/targetLayer resolver 做 GitNexus impact 并告警 |
| 字段 `displayName` 影响 checksum/snapshot | 高 | JSONB expand；旧 snapshot 原样可读；只在新 revision 写入 |
| 改型导致类型专属字段被静默清空 | 高 | preview 列出 `acceptedClearFields`；用户确认后才 apply |
| Sprint-73 并行代码冲突 | 高 | 实施前冻结 owning files 和基线 commit，不回退未知改动 |
| 门禁减少被误解为降低治理 | 中 | 只调整执行阶段，不删除 RELEASE_READY 治理门禁 |
| 真实浏览器仍无法自动验收 | 高 | F0/T01 先修 DNS/login/API harness，失败则 Feature 保持 DRAFT |

## 5. 复审决定

方向可继续，前提是同时接受以下四条：

1. 逻辑模型达到 DESIGNED 后可以结束本次工作，不强迫实现；
2. 物理名、装载、分区、保留期全部归数据实现；
3. 高级 dbt 入口从“物理资产”移到“数据实现”；
4. 模型类型与数仓层解耦，DataWorks DIM 通过计划策略启用，不全局硬切。

在用户确认前，Sprint 状态和全部 Task 保持 DRAFT。
