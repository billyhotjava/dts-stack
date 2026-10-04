# AN-001

## 标题

定稿内置模板 contract 与项目管理交互 contract `v1`。

## 范围

- `source/dts-analytics-webapp/modern/src/pages/screens/types.ts`
- `source/dts-analytics-webapp/modern/src/pages/screens/screenTemplates.ts`
- `source/dts-analytics-webapp/modern/src/pages/screens/specV2.ts`
- `source/dts-analytics-webapp/modern/src/pages/screens/components/PropertyPanel.tsx`
- `source/dts-analytics-webapp/modern/src/pages/screens/components/ComponentRenderer.tsx`

## 目标

- 把模板分类从旧行业包扩展到客户语义分类
- 明确项目管理大屏所需的共性交互类型
- 为后续模板与运行态增强提供统一字段，不让 `AN-003` 到 `AN-007` 各自发明配置结构

## Contract `v1`

- 模板分类新增：`qms`、`plm`、`hr`、`project-management`
- 交互动作统一到以下类型：
  - `set-variable`
  - `drill-down`
  - `drill-up`
  - `jump-url`
  - `open-panel`
  - `emit-intent`
- 项目管理推荐变量基线：
  - `projectId`
  - `programId`
  - `stageCode`
  - `ownerOrgId`
  - `ownerUserId`
  - `riskLevel`
  - `issueStatus`
  - `dateFrom`
  - `dateTo`

## 验收

- 设计器类型定义可表达上述分类与动作
- 旧模板仍可兼容渲染
- 新配置结构不会破坏现有 `preview`/`export` 流程

## 当前进度

- 状态：`done`
- 实施顺序：
  1. 已收敛 `types.ts` 中的动作 contract，新增 `actions` 与 `ScreenActionType`
  2. 已收敛 `specV2.ts` 对 `actions` 的校验，保持旧 `interaction` 兼容
  3. 已扩展 `screenTemplates.ts` 的模板分类定义，为后续交互增强留好字段

## 风险

- 现有 `interaction` 与 `drillDown` 已经在线上使用，改字段时必须保持兼容
- `emit-intent` 只能定义前端协议，不能误做成真实后端动作
