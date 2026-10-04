# SD-003

## 标题

将 `TemplateGallery` 的模板资产与行业包流程从工程化交互收口为结构化弹窗流。

## 范围

- `source/dts-analytics-webapp/modern/src/pages/screens/components/TemplateGallery.tsx`
- 相关弹窗/表单/结果面板组件
- `source/dts-analytics-webapp/modern/src/pages/screens/components/TemplateGallery.test.tsx`

## 目标

- 去掉模板导入、行业包导入导出、运维巡检、运行时探测、模板恢复等流程中的原始 `prompt/alert`
- 提升现场交付与自动化可测性

## 交付

- 模板包导入弹窗
- 行业包导入/导出弹窗
- 运维巡检结果面板
- 运行时探测结果面板
- 模板上架/下架与版本恢复结构化交互

## 验收

- 关键流程不再依赖 `window.prompt`
- 导入/导出/巡检/探测具备加载态与失败态
- 组件测试至少覆盖一个成功流和一个失败流

## 当前进度

- 状态：DONE
- 备注：行业包导出、采集任务草案、运维巡检、运行时探测、连接器探测、版本恢复已切到结构化弹窗；导入与状态反馈改为页内通知

## 风险

- 若一次性把所有低频操作都塞进单一弹窗，会导致复杂度反弹
