# F2: 前端 Preview 埋点

**优先级**: P0
**状态**: READY

## 目标

在 dts-bi 前端 ScreenPreviewPage 加载完成且用户停留 ≥3s 时调用 `reportsService.visit()` 把 visit 写入 dts-platform 的 `bi_report_visit` 表。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | ScreenPreviewPage 接入埋点 | P0 | READY | - |
| T02 | useScreenVisitTracker hook | P0 | READY | - |
| T03 | 前端单元测试 | P0 | READY | T02 |

## 完成标准

- [ ] 用户进 `/bi/screens/:id/preview` 停留 ≥3s 后，dts-platform 的 `bi_report_visit` 多一条记录
- [ ] 同 id 在 30s 内重复打开仅记一条
- [ ] visit POST 失败被静默吞掉，不影响 preview 渲染
- [ ] hook 单测 3 个全绿
