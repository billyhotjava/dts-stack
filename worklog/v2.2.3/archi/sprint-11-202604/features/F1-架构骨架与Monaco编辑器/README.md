# F1: 架构骨架与 Monaco 编辑器

**优先级**: P0
**状态**: READY

## 目标

搭建 SQL IDE 的整体架构骨架，替换原 textarea 为 Monaco，实现编辑器基础能力（语法高亮、补全、快捷键、格式化），确定前后端目录结构与 Feature Flag 切换机制。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 新建 sql-ide 组件目录与 SqlIdePage 路由 | P0 | READY | - |
| T02 | Feature Flag 前后端打通 | P0 | READY | T01 |
| T03 | Monaco 编辑器封装（SqlEditor + 主题） | P0 | READY | T01 |
| T04 | SQL 补全引擎（catalog + keyword + history） | P0 | READY | T03 |
| T05 | 快捷键体系（Ctrl+Enter 等 9 个） | P0 | READY | T03 |
| T06 | SQL 格式化（sql-formatter + 引擎 dialect） | P1 | READY | T03 |

## 完成标准

- [ ] `SqlIdePage.tsx` 可通过 feature flag 启用，老 `QueryWorkbenchPage` 保持可用
- [ ] Monaco 编辑器加载首屏 ≤800ms
- [ ] 输入 `.` 或 `FROM` 后触发补全，表名/列名来源于 catalog
- [ ] Ctrl+Enter 执行当前 SQL，Ctrl+Alt+F 格式化，9 个快捷键全部生效
- [ ] 编辑器主题跟随 Ant Design 暗黑模式自动切换
- [ ] 单个组件文件不超过 300 行（SqlIde.tsx 除外）
