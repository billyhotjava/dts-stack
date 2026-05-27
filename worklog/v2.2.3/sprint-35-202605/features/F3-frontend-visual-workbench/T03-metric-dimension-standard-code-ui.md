# T03: 指标公式、维度和标准码编辑体验

**优先级**: P0
**状态**: READY
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

- [ ] 对有标准码的维度，过滤控件传 code 而非中文 label。
- [ ] 公式输入不能提交任意 SQL。
- [ ] 缺 standard_code 的 DWD 维度在 preflight 中显示 ERROR。

## 完成标准

- [ ] UI 能引导用户使用稳定码，不把中文 label 当技术键。
