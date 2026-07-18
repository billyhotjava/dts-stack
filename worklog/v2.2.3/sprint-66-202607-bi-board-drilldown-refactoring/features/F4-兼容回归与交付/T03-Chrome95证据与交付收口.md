# T03：Chrome 95 证据与交付收口

**优先级**：P0
**状态**：DONE
**依赖**：T01、T02

## 目标

完成生产构建、Chrome 95 主路径、代码影响范围和 Sprint 状态的可复核交付。

## 技术设计

- 执行 Node 契约测试、Biome、`pnpm build` 和 `git diff --check`。
- 在 Chrome 95 执行两级下钻、上卷、重置、面板和内部跳转。
- 保存截图、控制台、Network 脱敏请求和 commit 信息。
- 提交前执行 GitNexus detect-changes，确认只影响预期大屏执行流。
- 同步 Task、Feature、Sprint、IT 和 sprint-queue 状态。

## 影响范围

- `workflow/v2.2.3/sprint-66-202607-bi-board-drilldown-refactoring/it/README.md`
- `workflow/v2.2.3/sprint-66-202607-bi-board-drilldown-refactoring/it/evidence/`（实施时创建）
- `workflow/v2.2.3/sprint-66-202607-bi-board-drilldown-refactoring/README.md`
- `workflow/v2.2.3/sprint-queue.md`

## 验证

- [x] 自动化命令记录退出码和结果。
- [x] Chrome 95 截图覆盖根层、下钻层和返回后状态。
- [x] GitNexus 结果无非预期 HIGH/CRITICAL 影响。
- [x] 文档状态与真实证据一致。

## 完成标准

- [x] IT-01 至 IT-12 全部有结论。
- [x] 所有 No-Go 条件均未触发。
- [x] Sprint 仅在真实交付完成后标记 DONE。
