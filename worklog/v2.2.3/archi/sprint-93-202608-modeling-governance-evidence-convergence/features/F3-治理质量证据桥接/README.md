# F3：治理质量证据桥接

**优先级**：P0
**状态**：COMPLETE / IT-05-06_PASS

## 目标

让模型 StageGate 和发布候选只消费钉定的治理质量证据，同时保留 dbt build/test 作为独立工程质量，从而避免“构建成功即数据质量通过”。

## 契约定义

| 类型 | 契约 | 关键字段 |
|---|---|---|
| Port 请求 | `QualityEvidenceRequest` | assetType/assetKey/ruleVersionIds/asOf/maxAgeSeconds |
| Port 响应 | `QualityEvidence[]` | ruleId/ruleVersionId/bindingId/runId/status/finishedAt/evidenceChecksum/violations |
| 发布钉定 | candidate command `response_snapshot` | engineeringEvidence + governanceQualityEvidenceRefs + combinedChecksum |
| StageGate | build/tests/quality 三项独立 | quality 只来自 Evidence Port |

## UI/UX 规格

复用现有发布面板，在“质量检查”中分成“工程验证”和“治理数据质量”两个区块；每条治理证据可跳到对应质量运行详情。缺失、运行中、失败和过期均显示明确原因及“重新运行”动作。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 实现版本钉定的 QualityEvidence Port | P0 | COMPLETE / IT-05_PASS | - |
| T02 | 接入 StageGate、发布命令与质量双栏 UI | P0 | COMPLETE / IT-06_PASS | - |

## Definition of Ready

- [x] Port 字段、有效性、查询预算和错误语义已钉定。
- [x] 明确不复制治理质量表。
- [x] T02 已消费 T01 DTO；真实 pass/fail/expired 样本留给集中 IT。

## 完成标准

- [x] StageGate 不再把 TEST 同时当 quality。
- [x] 历史候选质量结论不受 latest rule version 漂移影响。
- [x] UI 分开展示两类质量并可追溯 run。
- [x] 重试产生新 runId，不改历史证据。
