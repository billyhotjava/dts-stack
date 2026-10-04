# 按钮与组件验收矩阵

| 控件 | Owner | 用户意图 | 契约 | 状态与测试 |
|---|---|---|---|---|
| 字段项/横轴/纵轴货架 | AnalysisWorkspace | 拖入或按钮加入字段 | update `AnalysisQuerySpec` | disabled/duplicate/error/success；unit+E2E |
| 自动预览/执行查询/取消 | AnalysisEditorPage | 获得受治理结果 | `POST /analysis/preview`、cancel | idle/loading/error/success/truncated |
| 图表类型 | AnalysisWorkspace | 切换真实图表 | `visualization.type` | 只开放 6 类；source-contract |
| 图表样式 | AnalysisWorkspace | 配色、标签、堆叠、平滑、轴 | `visualization.settings` | draft only/read-only；round-trip test |
| 新增派生指标 | AnalysisWorkspace | 输入 code/expression/format | `derivedMetrics[]` | limit/invalid/backend error；E2E |
| 参数映射 | ParameterMappingPopover | 将筛选参数绑定组件字段 | `parameter_mappings[]` | empty/disabled/saved/reloaded |
| 联动设置 | InteractionSettingsPopover | 选择目标卡和清除行为 | dashcard visualization settings | none/targeted/clear；hook+E2E |
| 校验/发布 | 既有 Drawer | 固化版本和受众 | validate/publish | blocker/success/immutable |
| 导出 CSV/Excel | AnalysisEditorPage | 下载受控结果 | `POST /analysis/{id}/query/{format}` | unsaved disabled/loading/403/409/success |
