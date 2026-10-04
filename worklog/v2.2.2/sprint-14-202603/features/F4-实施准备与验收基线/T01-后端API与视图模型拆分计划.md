# T01: 后端 API 与视图模型拆分计划

**优先级**: P1
**状态**: DONE
**依赖**: 无

## 目标
给后续 `dts-analytics` 实现明确 screen-oriented 的后端拆分计划。

## 拆分原则
- 按页面输出 API，不按源表输出 API
- 复用现有 `ProjectCockpitService` 的聚合思路，但不复用旧业务语义
- GPMC 单独建 facade、query service、view model 和 warehouse gateway

## 推荐后端结构

### Resource 层
- `GpmcResource`
  - `/analytics/api/gpmc/overview`
  - `/analytics/api/gpmc/execution-board`
  - `/analytics/api/gpmc/quality-board`
  - `/analytics/api/gpmc/tech-state-board`
  - `/analytics/api/gpmc/cost-board`
  - `/analytics/api/gpmc/risk-board`
  - `/analytics/api/gpmc/drill/execution`
  - `/analytics/api/gpmc/drill/quality`
  - `/analytics/api/gpmc/drill/tech-state`
  - `/analytics/api/gpmc/drill/cost`
  - `/analytics/api/gpmc/drill/risk`

### Service 层
- `GpmcFacadeService`
  - 对外屏蔽主题域与页面拼装细节
- `GpmcOverviewService`
- `GpmcExecutionService`
- `GpmcQualityService`
- `GpmcTechStateService`
- `GpmcCostService`
- `GpmcRiskService`
- `GpmcDrillService`

### Gateway / Query 层
- `GpmcWarehouseGateway`
  - 负责读取 9 张源表对应的数据集
- 需要内部拆分：
  - `loadExecutionFacts`
  - `loadQualityFacts`
  - `loadTechStateFacts`
  - `loadCostFacts`
  - `loadRiskFacts`

### View Model / DTO 层
- `StrategicOverviewVm`
- `ExecutionBoardVm`
- `QualityBoardVm`
- `TechStateBoardVm`
- `CostBoardVm`
- `RiskBoardVm`
- `ProjectExecutionDrillVm`
- `QualityIssueDrillVm`
- `TechStateDrillVm`
- `CostDrillVm`
- `RiskDrillVm`

## 实施阶段建议

### 阶段 1：先打通总览与执行域
- `overview`
- `execution-board`
- `execution-drill`

原因：
- 可直接复用 `project1/project3` 的口径信息
- 也能最先替换当前 demo 中最大的一块骨架

### 阶段 2：补质量、技术、风险
- `quality-board`
- `tech-state-board`
- `risk-board`
- 对应 drill

原因：
- 三者都包含“汇总表 + 措施表”的双表关系，适合一并抽象

### 阶段 3：补成本
- `cost-board`
- `cost-drill`

原因：
- 成本域字段字典当前最不完整，宜后置

## API 契约约束
- 接口只接受页面级筛选参数：
  - `dateFrom`
  - `dateTo`
  - `deptId`
  - `projectNo`
  - `riskLevel`
- 接口返回页面级 view model，避免前端自行拼指标
- 措施明细统一在 drill API 中完整返回；看板 API 只返回预览摘要

## 测试建议
- Resource 层：接口返回结构与筛选参数契约
- Service 层：指标口径与聚合逻辑
- Gateway 层：源表字段映射与空值兼容
- 至少一组集成测试覆盖：
  - `overview`
  - `execution-board`
  - `quality-board`
  - `risk-board`

## 影响范围
- `source/dts-analytics`

## 验证
- [x] 后端拆分边界明确

## 完成标准
- [x] 后端实施计划完成
