# WB-002: 工作台首页去模拟趋势，补真实图表与角色卡片

## 范围

- `source/dts-platform-webapp/src/pages/workbench/index.tsx`
- 相关 workbench component / api service

## 目标

- 去掉前端模拟趋势逻辑
- 让工作台成为真正的平台首页，而不是轻量统计看板

## 交付

- 绑定后端真实趋势序列
- 角色化摘要卡片
- 更清晰的待办分组与跳转

## 验收

- 页面中不再出现由前端推算出的趋势数据
- 首页具备真实趋势、摘要、待办三个稳定模块
- 能被 Playwright 稳定识别与回归

## 当前进度

- 状态：TODO
- 备注：需要和 `WB-001` 联动交付
