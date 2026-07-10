# T01: 规划上下文纯函数与 session 草稿

**优先级**: P0  
**状态**: DONE
**依赖**: 无

## 目标

建立可版本校验、可恢复、可诊断的 `WarehousePlanningContext`，作为主题域、标准和维度建模页面的共同输入。

## 技术设计

- 新增独立 helper，定义 `version=1`、规划层、建模模式；**status 不入存储**，由 `resolvePlanningStatus` 纯函数现算（设计文档 3.1）。
- 使用版本化 session storage；读写异常静默降级（复用 journeySnapshot 的注入式 storage 模式）。
- 提供 `buildPlanningRoute`，只传递规划上下文白名单参数。
- **旅程白名单扩展（本任务承担，设计文档 3.3）**：`JOURNEY_CONTEXT_PARAM_KEYS` 增加 `planningId/domainId/warehouseLayer/modelingMode` 并补 `JOURNEY_CONTEXT_PARAM_LABELS`；`ARTIFACT_VALIDATION_API_NAMES` 补规划实体接口名（设计文档第 9 节）。

## 影响范围

- 新增 `source/dts-platform-webapp/src/pages/governance/warehousePlanningContext.ts` + 纯函数测试 + source-contract 测试
- `src/components/journey/journeyContext.ts`（PARAM_KEYS/PARAM_LABELS 扩展）
- `src/components/journey/journeyArtifactValidation.ts`（API_NAMES 补条目；新 key 自然落 unknown）
- 既有测试回归：`journeySnapshot.test.ts`（快照含规划参数 round-trip）、`journeyContext.test.ts`（清参 URL 保留规划参数）、`JourneyContextBar` 契约

## 验证

- [x] RED 覆盖创建、恢复、版本失效、storage 异常。
- [x] GREEN 覆盖 URL 参数完整传递和无效上下文阻断。
- [x] 旅程侧回归：buildJourneyUrl/清参/快照对四个规划参数透传，全部既有 journey 测试不破坏。
