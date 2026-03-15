# SD-004

## 标题

收口 `ScreenHeader` 的高价值动作，使大屏发布、回滚、模板保存、分享、导出更适合交付。

## 范围

- `source/dts-analytics-webapp/modern/src/pages/screens/components/ScreenHeader.tsx`
- 相关通用表单/确认/通知组件
- `source/dts-analytics-webapp/modern/src/pages/screens/components/ScreenHeader.test.tsx`

## 目标

- 将高频动作从 `prompt/alert/confirm` 迁移到结构化 UI
- 保留现有后端链路与权限校验，不重做状态机

## 交付

- 存为模板弹窗
- 发布与回滚确认流
- 分享与导出结果展示
- 分析会话沉淀入口的结构化表单

## 验收

- 高价值动作不再依赖浏览器原生弹窗
- 失败反馈明确展示错误原因
- 发布/回滚后的状态更新可被自动化脚本稳定识别

## 当前进度

- 状态：DONE
- 备注：存为模板、沉淀分析会话改为结构化表单；分享/复制结果改为页内通知，发布结果继续复用已存在的发布 notice

## 风险

- 若不抽取复用的 action dialog 组件，`ScreenHeader` 会继续膨胀
