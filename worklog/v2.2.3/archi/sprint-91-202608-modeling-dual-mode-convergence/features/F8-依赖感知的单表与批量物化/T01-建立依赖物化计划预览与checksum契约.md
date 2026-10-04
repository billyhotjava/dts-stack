# T01: 建立依赖物化计划预览与 checksum 契约

**优先级**: P0
**状态**: CODE_COMPLETE / E2E_PENDING
**依赖**: F6/T01、F6/T03

## 目标

在既有 release candidate 前增加只读 plan preview，基于 F6 的固定依赖计算 BUILD/REUSE/BLOCK 和拓扑顺序；创建候选时服务端重新计算并校验 checksum，防止 UI 自报依赖或绕过围栏。

## API 契约

```http
POST /api/modeling/plans/{planId}/materialization-plans/preview
```

```text
request {
  environment: string,
  requestedModelSpecIds: UUID[],
  strategy: WITH_MISSING_UPSTREAMS | CURRENT_ONLY
}

response {
  planChecksum: string,
  canStart: boolean,
  requestedModelSpecIds: UUID[],
  orderedEntries: [{
    modelSpecId, modelRevision, modelChecksum,
    implementationRevision, implementationChecksum,
    dependencyChecksum, layer, dependencyRole,
    action: BUILD | REUSE,
    reasonCode
  }],
  blockers: [{code, modelSpecId, message, details}]
}
```

- `requestedModelSpecIds` 去重、稳定排序；`orderedEntries` 按拓扑和稳定身份排序后计算 checksum。
- candidate create 只可新增可选 `materializationPlanChecksum`；服务端按相同请求上下文重算，不接受客户端提交任意 orderedEntries。
- `CURRENT_ONLY` 仅在所有上游均可 REUSE 时 `canStart=true`；默认 `WITH_MISSING_UPSTREAMS`。

## 解析规则

- 使用 F6 snapshot 的 `UPSTREAM/DIMENSION` pins；不重新解析 UI、模型名称或自由 SQL。
- 精确 verified relation 判定继续复用既有 `loadPinnedDependencyArtifacts`/relation observation seam。
- 缺失实现、stale pin、source unavailable、cycle、environment/target 不一致、selector collision 均返回稳定 blocker。
- 对多个请求目标求依赖闭包后去重；同一节点只出现一次，保留所有触发路径摘要。

## Feature 关联

- F6/T01 是唯一图 owner；F6/T03 提供可执行 implementation pins。
- T02 只渲染本 API；T03 只执行已重算通过的候选。
- F5 的 candidate/build/test/review/publish 从本计划继续，不创建物化旁路。

## Definition of Ready

- [x] F6/T01 snapshot 可按固定 pins 批量读取，F6/T03 implementation 可执行。
- [x] API request/response、策略、blocker、checksum 和容量预算已冻结。
- [ ] F0/T03 目标环境 relation observation 基线已归档。

## 验证 (RED→GREEN)

- [x] 单选与多选依赖闭包去重、稳定拓扑顺序已有聚焦测试；真实四层对象留待 IT-13/IT-14。
- [x] 全 BUILD、部分上游 REUSE、CURRENT_ONLY 阻断与依赖失败 blocker 已覆盖。
- [x] cycle/selector conflict 返回稳定阻断；相同输入重放 checksum 一致。
- [x] preview 后依赖/实现/物理 observation 漂移，candidate create 拒绝旧 checksum。
- [ ] 64 请求节点、256 闭包节点下无 N+1 且满足 NFR 延迟预算。

## Definition of Done

- [x] planner 没有新依赖台账或状态机，只编排既有 pins/observation/candidate。
- [x] F8/T02 和 T03 不各自实现依赖闭包。
- [ ] API、稳定错误码与 checksum 已有契约测试；真实审计 correlation 待 IT-13/IT-14。
