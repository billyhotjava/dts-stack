# T01: 底部 dock（▶运行 / ⏱调度 / 📜日志 三 tab）

**优先级**: P0
**状态**: READY
**依赖**: S3（画布容器布局）

## 目标

在画布底部实现可展开/收起的运行 dock，含三 tab：▶运行（触发+进度）、⏱调度（mock 配置）、📜日志（mock 流式日志）。

## 技术设计

- 库/栈：Ant Design 5 `Tabs` + `Progress` + `Tag`（状态点）+ `Badge`；底部固定栏用 flex 占位，画布区 ResizeObserver 让出 dock 高度。
- 文件（`app/src/canvas/dock/`）：
  - `RunDock.tsx`（dock 外壳：展开/收起、三 tab 容器、高度状态）。
  - `RunTab.tsx`（▶运行：运行按钮 → 触发 `orchestrationService.runJob`，展示进度 + 运行状态点 运行中/成功/失败）。
  - `ScheduleTab.tsx`（⏱调度：cron 表达式 / 触发方式表单，本 sprint 以只读+占位为主，收编现网调度字段语义）。
  - `LogTab.tsx`（📜日志：流式追加 mock 日志行，自动滚底，级别着色 info/warn/error）。
  - `useRunProgress.ts`（轮询/定时器驱动 mock 进度，参考现网 `useTransformAsyncRunProgress` 形状）。
- 关键实现点：
  - 运行进度用 setInterval 模拟分阶段推进（提交→执行→完成），可调延迟；运行结束写回作业 `lastRunStatus`。
  - 日志流以增量 mock 推送（每 tick 追加几行）；停止运行清理定时器（避免泄漏）。
  - 错误显式处理：运行失败 → 状态点红 + 日志 error 行 + 用户友好提示。
- mock service：`orchestrationService.runJob(jobId): Promise<Result<RunHandle>>`、`orchestrationService.getRunLog(runId)`（增量）；复刻现网 `Result<T>`。

## 影响范围

- 新增 `app/src/canvas/dock/*`。
- 接入 S3 画布容器底部（画布让出 dock 高度）。
- 新增/扩展 `mock/services/orchestrationService.ts`（runJob / getRunLog / getRunProgress + fixtures）。
- 收编现网 `EltConsolePage` 运行/日志语义。

## 验证

- [ ] dock 可展开/收起；三 tab 切换正常。
- [ ] ▶运行 → 进度推进 + 状态点变化（运行中→成功/失败）。
- [ ] 日志 tab 流式追加并自动滚底；级别着色正确。
- [ ] 运行失败路径有显式错误 UI；定时器在卸载/停止时清理。

## 完成标准

- [ ] 三 tab 完整，运行/日志走 `orchestrationService` mock。
- [ ] 进度/日志为不可变状态更新，无内存泄漏。
- [ ] dock 布局 Chrome 95 安全。
