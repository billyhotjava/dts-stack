# F2: 多 Tab 持久化

**优先级**: P0
**状态**: READY

## 目标

实现用户级多 Tab 持久化：SQL 文本、光标位置、关联执行 ID 保存到后端数据库，支持跨设备/跨会话恢复。结果集不持久化，通过 `lastExecutionId` 按需回拉。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T07 | sql_ide_tab 表结构与 Liquibase | P0 | READY | F1 |
| T08 | SqlIdeTabService + CRUD API | P0 | READY | T07 |
| T09 | 前端 Zustand tab store + localStorage + 防抖同步 | P0 | READY | T08 |
| T10 | Tab 切换/关闭/冲突处理 UI | P0 | READY | T09 |

## 完成标准

- [ ] 用户开多个 Tab，刷新浏览器可恢复
- [ ] 另一台设备登录同账号，能看到/恢复所有 Tab
- [ ] 两设备并发修改同一 Tab，以 `updatedAt` 较新的为准，较旧的弹冲突提示
- [ ] 关闭浏览器前最近 2s 的修改不丢失（localStorage 兜底）
- [ ] Tab 数量上限 30 个，超限禁止新开并给出提示
- [ ] 关闭 dirty=true 的 Tab 有确认弹框
