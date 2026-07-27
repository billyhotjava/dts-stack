# T03：汇聚构建状态并实现 adapter 能力 fail-closed

**优先级**：P0
**状态**：DRAFT
**依赖**：T02、F2/T03

## 目标

只有 candidate 全部 entry 同时满足 current dbt success 和 current relation exists 时进入 BUILT；未知 adapter 或能力不足必须阻断。

## 技术设计（Contract-first）

- **输入契约**：candidate entries、current pipeline runs、current observations、capability registry。
- **输出契约**：candidate build evidence 每 entry 包含 `runState,relationState,locator,observedAt,repairCode`；聚合状态 `RUNNING|BUILD_FAILED|BUILT|STALE`。
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

- [ ] 多 entry 全排列状态表测试。
- [ ] relation 删除后旧 run success 不通过。
- [ ] unknown adapter fail-closed。
- [ ] generic dbt asset sync 兼容回归。

## Definition of Done

- [ ] BUILT 有唯一、可审计判定。
- [ ] 任何缺证据状态都不会显示成功。
- [ ] capability 扩展不修改核心聚合逻辑。
