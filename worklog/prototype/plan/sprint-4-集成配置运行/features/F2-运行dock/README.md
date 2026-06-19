# F2: 运行 dock

**优先级**: P0
**状态**: READY

## 目标

在画布底部提供运行 dock（▶运行 / ⏱调度 / 📜日志 三 tab），并以 `CompactTable` 展示运行历史。收编现网 `EltConsolePage`（运行/日志）与 `OrchestrationRunsTab` / `TransformExecutionHistoryPage`（运行历史）。

## 背景

设计 §6/§7 草图：画布下方一条 dock —— ▶运行 调度 运行历史 日志。运行/预览/日志均 mock；运行触发 mock 进度 + 日志流，历史以 10 条/页表格展示。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 底部 dock（运行/调度/日志 三 tab） | P0 | READY | S3 |
| T02 | 运行历史（CompactTable 收编） | P0 | READY | T01 |

## 完成标准

- [ ] 画布底部 dock 可展开/收起，三 tab（▶运行 / ⏱调度 / 📜日志）可切换。
- [ ] ▶运行触发 mock 运行：进度推进 + 状态点（运行中/成功/失败），日志 tab 流式追加 mock 日志行。
- [ ] ⏱调度 tab 展示/编辑 mock 调度配置（cron/触发方式，只读+占位为主）。
- [ ] 运行历史以 `CompactTable`（10 条/页）展示，列收编现网 Orchestration/Transform 执行历史。
- [ ] dock 与日志走 `orchestrationService` / `transformService` mock；`VITE_USE_MOCK` 默认开。
- [ ] dock 布局 Chrome 95 安全（固定底栏 flex + ResizeObserver）。
