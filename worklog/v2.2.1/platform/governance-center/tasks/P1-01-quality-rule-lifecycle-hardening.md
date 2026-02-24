# P1-01 质量规则生命周期加固

`status`: `done`
`priority`: `P1`

## 目标

建立规则从定义、试跑、发布、执行到归档的可追踪生命周期。

## 后端实施点

1. 为规则增加版本与状态流转校验。
2. 规则执行记录补充输入参数与错误分类。
3. 增加规则试跑（dry-run）接口（复用现有执行引擎）。

## 前端实施点

1. 规则编辑页增加“试跑结果”与“版本对比”。
2. 执行历史支持按状态、数据集、时间筛选。
3. 失败日志展示结构化错误信息。

## 验收标准

- 规则发布前可试跑，失败可定位到字段/条件。
- 执行历史可直接回溯到对应规则版本。

## 本轮进展

1. 后端新增规则试跑能力：
   - `POST /api/governance/quality/runs/dry-run`
   - 触发类型标记为 `DRY_RUN`，试跑流程同步返回结果。
2. 后端补齐运行历史筛选参数：
   - `status`、`startedFrom`、`startedTo`（保留 `ruleId`、`datasetId`、`limit`）。
3. 前端质量规则页新增：
   - 规则行“试跑”按钮与试跑结果弹窗。
   - 执行历史区域（状态/数据集/时间筛选）。
4. 编译校验通过：
   - `cd source/dts-platform && ./mvnw -DskipTests compile`
   - `cd source/dts-platform-webapp && pnpm build`
5. 规则版本生命周期收敛：
   - `QualityRuleUpsertRequest` 新增 `publishNow`，支持“草稿保存/直接发布”。
   - 新增 `POST /api/governance/quality/rules/{id}/versions/{version}/status`，并校验状态流转：
     - `DRAFT -> PUBLISHED/ARCHIVED`
     - `PUBLISHED -> ARCHIVED`
     - `ARCHIVED` 不可回退
6. 执行记录结构化增强：
   - `gov_quality_run` 新增 `input_params_json`、`error_category` 字段（Liquibase: `20260224_01...`）。
   - 运行记录自动落库触发参数，并按失败信息归类（如 `SQL_SYNTAX`、`OBJECT_NOT_FOUND`、`PERMISSION_DENIED`、`TIMEOUT` 等）。
7. 前端体验补齐：
   - 质量规则页新增“发布/归档”动作、版本状态展示。
   - 执行历史筛选条件支持 URL 保持（状态/数据集/时间窗）。
   - 运行日志详情补充输入参数与错误分类展示。
