# T01: 业务过程契约与 processId 白名单

**优先级**: P0
**状态**: DONE
**依赖**: 无

## 目标

建立 BusinessProcess 契约与存储内核，并把 processId 纳入旅程白名单。

## 技术设计

- 新增 `src/pages/governance/businessProcess.ts`：契约见 design-notes 第 2 节；版本化 session 草稿（复用 journeySnapshot 注入式 storage 模式）；内置三条 PJM 示例过程可一键采用。
- 旅程白名单：`JOURNEY_CONTEXT_PARAM_KEYS` + `JOURNEY_CONTEXT_PARAM_LABELS` 增加 `processId`（标签"业务过程"）；`ARTIFACT_VALIDATION_API_NAMES` 增加 `GET /api/governance/subject-domains/{domainId}/processes`。

## 影响范围

- 新增 `businessProcess.ts` + vitest + source-contract
- `src/components/journey/journeyContext.ts` / `journeyArtifactValidation.ts`
- 既有 journey 测试回归（快照/清参/Bar）

## 验证

- [x] vitest：创建/恢复/版本失效/storage 异常/示例采用。
- [x] 旅程回归：processId 透传全链路，既有用例零破坏。
