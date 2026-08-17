# T02：接入 StageGate、发布命令与质量双栏 UI

**优先级**：P0
**状态**：IMPLEMENTATION_COMPLETE / MODULE_VERIFIED / IT-06_PENDING
**依赖**：F3/T01、F1/T01、F0/T01 样本

## 目标

让候选版本只有在策略要求的工程质量和治理质量分别满足时才能发布，并在 UI 中完整展示和重试。

## 技术设计（Contract-first）

- **输入契约**：当前 ModelSpec/implementation/candidate pins、F3/T01 `QualityEvidence[]`、现有 compile/build/test evidence、发布策略要求的 ruleVersionIds/maxAgeSeconds。
- **StageGate 输出**：`GateEvidence` 中 build/tests/quality 独立；quality violations 以 `MODEL_SPEC_GOVERNANCE_QUALITY_*` 返回。
- **候选命令输出**：既有 append-only `response_snapshot` 增加 `{engineeringEvidence:{...},governanceQualityEvidence:[{ruleVersionId,bindingId,runId,evidenceChecksum}],combinedEvidenceChecksum}`。
- **状态流**：工程构建成功 → 触发/等待治理运行 → QUALITY_PASSED；所级/范围内管理员可自助 publish；选择传统 review 时仍可沿原路径。
- **重试**：失败/过期只允许新建 quality run，旧 command/run 不修改；重复 idempotencyKey 不重复触发。
- **错误路径**：Port 不可用、evidence stale/missing/mismatch、candidate pins 变化均阻塞并返回修复深链。
- **复用点**：现有 ModelSpecStageGate、candidate command、self-service publish、quality routes。

## UI 交互规格

1. 发布面板显示“工程验证”和“治理数据质量”。
2. 每项显示状态、完成时间、规则版本和运行详情链接。
3. 失败/过期提供“重新运行”；点击后进入加载态，返回新 runId。
4. 所有证据通过后，xiezm 可直接发布；部门角色只操作所属范围。
5. 空/加载/错误/成功四态均有稳定文案，禁止只显示笼统“质量未知”。

## 影响范围

dts-platform stage gate/candidate application/DTO；dts-platform-webapp 发布面板与 source-contract；审计字典。

## 验证（RED→GREEN）

- [x] dbt test 通过但治理质量缺失时仍阻塞。
- [x] 治理质量通过但 compile/build 失败时仍阻塞。
- [x] 证据全通过后 self-publish 和 legacy review 两条路径均兼容。
- [x] 重试产生新 runId，历史 candidate snapshot 不变。
- [ ] UI 四态、深链、权限和 Chrome 95 验证。

## Definition of Done

- [x] 两类质量语义、DTO、页面和审计完全分离。
- [ ] IT-05/06 通过，无前端伪造 passed。
- [x] 旧候选和旧 API 可读取，Expand/Contract 可回滚。
