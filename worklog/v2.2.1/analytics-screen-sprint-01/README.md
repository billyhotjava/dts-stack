# Analytics Screen Sprint 01

## 目标

在 `source/dts-analytics-webapp/modern` 内完成一轮面向客户交付的大屏模板与交互增强：

- 新增 `QMS`、`PLM`、`HR`、`财务`、`项目管理` 5 套内置模板
- 将项目管理视角纳入大屏模板体系，而不是独立在模板之外
- 补齐模板运行所需的过滤、下钻、上卷、动作入口基线
- 保持本轮为前端内置模板与交互 contract v1，不引入新的后端写回能力

## 范围

- `source/dts-analytics-webapp/modern/src/pages/screens`
- `source/dts-analytics-webapp/modern/src/api/analyticsApi.ts`
- `source/dts-analytics-webapp/modern/src/pages/screens/components`
- `source/dts-analytics-webapp/modern/src/pages/screens/hooks`

## 输入基线

- `worklog/v2.2.1/BI/screen-designer-gap-analysis.md`
- `worklog/v2.2.1/BI/screen-designer-commercialization-p0-p2-breakdown.md`
- `worklog/v2.2.1/BI/tasks/P1-02-global-filter-interaction-engine.md`
- `worklog/v2.2.1/BI/tasks/P3-03-industry-template-library.md`

## 本 Sprint 不做

- 不接入真实 `QMS / PLM / HR / 财务 / 项目管理` 后端接口
- 不在本轮实现真实审批、写回、工单提交或状态回写
- 不把模板先做成服务端资产模板体系
- 不重做大屏设计器的信息架构

## 交付清单

1. `AN-001` 模板与项目管理交互 contract 定稿
2. `AN-002` 模板库分类与元数据增强
3. `AN-003` `QMS / PLM / HR / 财务` 内置模板
4. `AN-004` 项目管理高定模板
5. `AN-005` 过滤增强 `v1`
6. `AN-006` 下钻 / 上卷增强 `v1`
7. `AN-007` 动作入口模型 `v1`
8. `AN-008` 构建验证与人工走查

## 通过标准

- 模板库可直接选中 `QMS / PLM / HR / 财务 / 项目管理`
- 项目管理模板具备全局过滤、分层下钻、上卷返回、详情面板入口、动作入口
- 过滤、下钻、动作配置在设计器中可配置，在预览态可执行
- `pnpm -C source/dts-analytics-webapp/modern build` 通过

## 当前节奏

- 当前阶段：`AN-001` 与 `AN-002` 先行
- 当前目标：先把分类、模板元数据、交互 contract 落稳，再扩模板内容
