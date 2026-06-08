# T03: 指标公式、维度和标准码编辑体验

**优先级**: P0
**状态**: DONE
**依赖**: T02

## 目标

在前端指标与维度编辑中强制展示标准码、中文 label、口径、单位、格式、owner 和术语绑定，避免继续用中文自然值做 Join 或过滤事实。

## 技术设计

- 维度字段列表显示 `standardCodeField` 和 `labelField`。
- 过滤器默认使用 `*_code` 或 `standard_code`，label 只展示。
- 指标公式编辑器只提供受控 DSL 操作：sum、count、count_distinct、avg、ratio、case_when、date_trunc。
- 公式预览显示 SQL 片段但不允许任意 SQL 输入。

## 影响范围

- `source/dts-metrics-webapp/src/features/semantic/SemanticFieldExplorer.tsx`
- `source/dts-metrics-webapp/src/pages/semantic/SemanticDesignerPage.tsx`
- `source/dts-metrics-webapp/src/features/semantic/semanticTypes.ts`

## 验证

- [x] 字段树显示 `standardCodeField` / `labelField`，拖拽 payload 带标准码上下文。
- [x] 对有标准码的维度，字段选择以技术字段 id / code 字段为主，不把中文 label 当 value。
- [x] 公式输入不能提交任意 SQL；派生指标改为 `sum/count/count_distinct/avg/ratio/date_trunc` 受控 DSL 构造器，SQL/DSL 片段只读展示。
- [x] 缺 standard_code 的 DWD 维度在 preflight 中显示 ERROR。

## 完成标准

- [x] UI 能引导用户使用稳定码，不把中文 label 当技术键；字段树、拖拽 payload 和受控公式编辑器已补齐。
