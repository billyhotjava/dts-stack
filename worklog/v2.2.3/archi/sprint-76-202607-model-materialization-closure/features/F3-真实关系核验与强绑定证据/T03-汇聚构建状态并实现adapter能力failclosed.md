# T03：汇聚构建状态并实现 adapter 能力 fail-closed

**优先级**：P0
**状态**：DONE
**依赖**：T02、F2/T03

## 目标

只有 candidate 全部 entry 同时满足 current dbt success 和 current relation exists 时进入 BUILT；未知 adapter 或能力不足必须阻断。

## 技术设计（Contract-first）

- **输入契约**：candidate entries、current pipeline runs、current observations、capability registry。
- **输出契约**：本 Task 先形成 append-only observation 与 current repository
  projection；F5 只读映射为每 entry 的
  `runState,relationState,locator,observedAt,repairCode`，不得另建状态真值。
- **聚合规则**：
  - 任一 RUNNING/UNKNOWN → candidate 保持 BUILDING；
  - 任一 dbt failed 或 exists=false → BUILD_FAILED；
  - 任一 drift → STALE；
  - 全部 current SUCCESS+EXISTS → BUILT。
- **能力 registry**：按 adapter/materialization/partition/probe 列出 supported + code；无注册项默认 unsupported。
- **兼容**：generic DBT_MANAGED 非 ModelSpec 同步不改变；lifecycle-bound model 走新强证据路径。
- **错误路径**：unsupported adapter、probe unavailable、manifest entry missing 不得回落到 `physicalAssetVerified=true` 标签推断。

## 影响范围

- ReleaseCandidate evidence projection
- DbtAssetSync lifecycle-bound branch
- capability registry
- state aggregation tests

## 验证（RED→GREEN）

- [x] 双 entry 混合结果验证“全部 verified 才 BUILT”；单 entry 覆盖成功、缺失、
  type drift、column drift 与 Candidate 转换失败。
- [x] relation absence 时旧 run_results success 不通过。
- [x] unknown adapter fail-closed。
- [x] generic `DBT_MANAGED` asset sync 兼容回归 13/13 通过。

## Definition of Done

- [x] BUILT 有唯一、可审计判定。
- [x] 任何缺证据状态都不会显示成功。
- [x] capability 通过 inspector registry 扩展，不修改核心聚合逻辑。

证据：`../../it/evidence/f3-real-physical-relation/README.md`。
