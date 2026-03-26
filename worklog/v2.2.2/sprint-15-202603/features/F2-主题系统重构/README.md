# F2: 主题系统 CSS Variables 化

**优先级**: P0
**状态**: READY

## 目标
将主题从 props 传递 token 改为 CSS Variables 驱动，修复主题切换不生效 bug，扩展内置主题

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | CSS Variables 定义与注入机制 | P0 | READY | F1/T01 |
| T02 | ECharts/DataV 主题联动 | P0 | READY | T01 |
| T03 | 内置主题扩展(6个) + 自定义主题面板 | P1 | READY | T02 |

## 完成标准
- [ ] 编辑器切换主题 → 画布即时响应
- [ ] ECharts 图表颜色跟随主题
- [ ] 6 个内置主题 + 自定义主题入口
