# F3：外部 dbt 包逆向建模产品化

**优先级**：P0
**状态**：CODE_COMPLETE / E2E_PENDING
**依赖**：F1

## 目标

用真实 ZIP inspect/preview/apply/retry 替换硬编码逆向原型，让用户看见依赖、可信结构、损失项、兼容能力与冲突；只有技术事实可验证且业务语义补齐的依赖闭包才生成 canonical `ModelSpec DRAFT + DBT_MANAGED Implementation Revision`。

**切片边界**：P0 只承诺 artifact-rich ZIP 的一次安全导入、逐项结果与恢复；D10 source-only/complex 政策保持冻结但实现归入 P1/T06，重新导入三方漂移与前向撤销归入 P1/T05。

## UI/UX 规格

- **入口**：`/data-modeling/dimensions/reverse?source=dbt`。
- **来源**：P0 只允许上传 ZIP 快照；不提供 Git 地址、凭据、同步或在线依赖下载入口。
- **可信交接**：安全检查成功后返回 30 分钟有效的 `inspectionProof`；preview 绑定原 tenant、actor 和规范化技术包 checksum，只接受白名单 context/selection/semanticOverrides。proof 无效或过期必须重新 inspect，不允许客户端重算 checksum 绕过。
- **source-only（P1）**：D10 已冻结为只有 enforced schema contract 且字段 `name+data_type` 完整才可导入；其余结构最多只读且 apply BLOCKED。动态 ref/source、自定义 macro 隐藏依赖或缺失 package 阻断受影响模型及其下游，用户不能人工豁免；独立合格闭包可单独选择，不能把预览中的 blocked 项计为 PARTIAL。P0 artifact-rich 交付不得伪装该能力已实现。
- **向导**：上传 ZIP → 安全检查 → 计划/域/来源映射 → 模型差异与语义补全 → 应用进度/结果。
- **结果分类**：CREATE、UPDATE、SKIP、CONFLICT、BLOCKED；每项显示 conversion capability 与 recoveryAction。
- **重新导入**：以最近一次已接受合并检查点为 base，对 DTS 当前技术实施和新 ZIP 做逐节点三方比较；ModelSpec 业务语义始终保留并重验映射，缺失、删除和重命名均不得自动处理。
- **恢复**：刷新页面可凭 runId 恢复；PARTIAL 必须逐项展示失败码、失败阶段、安全原因、是否可重试和恢复动作；retry 不重复成功项。
- **完成**：成功项默认进入对应 ModelSpec 普通业务可视化；技术维护者可在同一模型详情“数据实现”阶段显式打开高级 dbt 实现，不新增入口或清单。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 增加 dbt ZIP 来源模式并接入安全 inspect | P0 | CODE_COMPLETE | F0/T02、F1/T02 |
| T02 | 建立计划、域、来源映射和语义补全预检 | P0 | CODE_COMPLETE | T01、F1/T03 |
| T03 | 展示首次导入依赖闭包、差异和阻断选择 | P0 | CODE_COMPLETE | T02、F1/T04 |
| T04 | 接入 apply、进度恢复、结果、retry 与历史 | P0 | CODE_COMPLETE | T03 |
| T05 | 建立重新导入、三方漂移和前向撤销 | P1 | CODE_COMPLETE | T04、F1/T05、D05/D07 |
| T06 | 实现 source-only 与复杂依赖闭包 | P1 | CODE_COMPLETE | F1/T02、T01～T04、D10 |

## Definition of Ready

- [x] D04、D05、D07 已于 2026-08-01 确认。
- [x] D09 兼容政策和 D10 source-only 行为已于 2026-08-02 确认。
- [x] D13 inspect → preview 可信交接、30 分钟 proof、白名单 override 与稳定失败码已于 2026-08-02 确认。
- [ ] P0 artifact-rich 的 FX-01 与 FX-05 可用于 RED 测试；materialization profile 由 H83-01 原始证据 + F0/T05 认证登记独立阻断。
- [ ] P1 source-only/complex 的 FX-02/03 已定义但不反向阻断 P0；其 Task 保持 DRAFT 直到证据齐备。
- [ ] 不把数据库表逆向发现误写为本 Feature 已实现能力。

## 完成标准

- [ ] 前端不再使用 `DISCOVERED_MODELS` 作为真实结果。
- [ ] import run/apply attempt 现有台账被复用。
- [ ] inspect 不新建会话表；preview 对无效/过期 proof 分别返回 `DBT_IMPORT_INSPECTION_PROOF_INVALID` / `DBT_IMPORT_INSPECTION_PROOF_EXPIRED`，且 tenant/actor/技术包任一变化均 fail-closed。
- [ ] 导入从不直接进入发布/物化，也不静默覆盖冲突。
- [ ] apply attempt/item/summary 只输出 canonical 状态；存量 `SUCCEEDED→SUCCESS`、`REPLAYED→SKIPPED` 已前向迁移，`BeginDisposition.REPLAY` 只用于幂等响应。
- [ ] P0 artifact-rich 成功项 ownership 始终为 `DBT_MANAGED`，BLOCKED 项不可选择，PARTIAL 只来自已选合格项启动后的逐项失败。
- [ ] P1/T06 完成后，source-only 只在 enforced schema contract、literal 依赖闭包和业务语义同时成立时 apply；未完成前稳定显示未支持/阻断，不伪成功。
- [ ] 每个 FAILED/BLOCKED 项均符合 [`assets/import-partial-result-contract.md`](../../assets/import-partial-result-contract.md)，用户无需查看日志即可知道失败对象、原因和下一步动作。
