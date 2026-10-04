# F1: 编辑器属性面板密级入口

**优先级**: P0
**状态**: READY

## 目标

在大屏编辑器的「基础信息」区块顶部直接放一个密级 select，让 owner 不必打开「分享」弹窗就能改密级，把密级管理从"功能"提升为"基础属性"。原有 ScreenSharePanel 顶部的密级 select 保留，作为二级入口（双入口共用同一 PATCH endpoint）。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 抽离 ClassificationSelect 共享组件（4 选项 + 当前值显示 + onChange） | P0 | READY | - |
| T02 | 编辑器属性面板「基础信息」顶部接入 ClassificationSelect | P0 | READY | T01 |
| T03 | ScreenSharePanel 顶部密级区块改用 ClassificationSelect | P1 | READY | T01 |

## 完成标准

- [ ] 编辑器右侧属性面板的「基础信息」区块顶部有「大屏密级」label + select（PUBLIC / INTERNAL / SECRET / CONFIDENTIAL 四选）。
- [ ] 仅当 `screen.isOwner === true` 时 select 可改；非 owner 显示当前密级 Tag（与 SharePanel 一致）。
- [ ] 修改后调用 `PATCH /api/screens/{id}/classification`，成功后弹 message + 更新 panel 内显示值（无需 reload）。
- [ ] 现有 ScreenSharePanel 顶部「大屏密级」区块复用同一组件（消除重复代码）。
- [ ] 入口在编辑器进入即可见，无需任何点击。

## 关键文件

- 新增：`source/dts-platform-webapp/src/analytics/pages/screens/components/ClassificationSelect.tsx`
- 改：`source/dts-platform-webapp/src/analytics/pages/screens/components/PropertyPanel`（或属性面板对应文件）
- 改：`source/dts-platform-webapp/src/analytics/pages/screens/components/ScreenSharePanel.tsx:301-356`

## 注意

- ClassificationSelect 需支持 `null/undefined` 当前值 → 显示 placeholder「未设密级」并标橙色边框（提示用户去设）。
- 同一 screenId 多入口同时编辑时，靠后端 PATCH 是 last-write-wins，不做乐观锁（密级修改频率极低）。
