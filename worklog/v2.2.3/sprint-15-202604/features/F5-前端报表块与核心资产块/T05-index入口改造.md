# T05: index.tsx 入口改造 + 空态 / 错误态 / 骨架

**优先级**: P0
**状态**: READY
**依赖**: T04

## 目标

将 `src/pages/workbench/index.tsx` 改为薄壳：只做 `<LeaderOverviewPage />` 的渲染入口，删除所有旧 Mini 图表 / 待办列表 / 常用入口 / 资产趋势 / 收藏残留 / Table 大屏，完成从旧工作台到领导视角工作台的切换。

## 技术设计

### 改造后 index.tsx

```tsx
import { LeaderOverviewPage } from "./LeaderOverviewPage";

export default function WorkbenchPage() {
  return <LeaderOverviewPage />;
}
```

就这么薄。

### 要删除的旧代码

对照 `source/dts-platform-webapp/src/pages/workbench/index.tsx`（v2.2.3 当前版本）：

- 所有 `MiniAreaChart` / `MiniBarChart` / `MiniDonutChart` 函数实现（约 70 行起）
- 所有 `TODO_COLORS` / `TODO_LABELS` / `TODO_TAG_COLORS` 常量（已不再用，因为 todo 块整个删）
- 所有 KPI Card 旧版 JSX
- 最近动态列表 + `workbenchService.todos()` 调用
- 资产趋势图 + 相关 state / useEffect
- 数据大屏 Table（由 `ScreenStrip` 替代）
- 常用入口块（旧收藏块入口，F2 已处理；此处确保无残留）
- `fetchScreens` 函数（由 `ScreenStrip` 内部负责）
- 所有未被 `LeaderOverviewPage` 引用的 import（Ant Design Button/Card/Col/Form/Input/Modal/Row/Select/Space/Table/Tag 等）

### 同步清理

- `src/pages/workbench/README.md`：把设计稿摘要替换为新版方案简述（3-5 行），引到 `docs/superpowers/specs/2026-04-24-platform-workbench-leader-overview-design.md`。

### 不在本 task 删除

- `workbench/WorkflowCenterPage.tsx`：属于独立页，本 sprint 不动。
- `workbenchService.overview()` / `workbenchService.todos()`：保留。其他入口可能仍用（例如 `WorkflowCenterPage`）。

## 影响范围

- 大幅简化：`src/pages/workbench/index.tsx`
- 修改：`src/pages/workbench/README.md`

## 验证

- [ ] `pnpm tsc --noEmit` 通过。
- [ ] `pnpm dev` 启动前端 → `/workbench` 显示 `LeaderOverviewPage` 全部内容，无旧块残留。
- [ ] DevTools Network：进入工作台**不**再触发 `/api/workbench/favorites`（F2 已处理）、`/api/workbench/todos`（本 task 删除调用）；**会**触发 `/api/workbench/leader-overview`。
- [ ] 旧的 `MiniAreaChart` 等函数通过 `grep -rn "MiniAreaChart" source/dts-platform-webapp/src/` 确认**仅**在其他无关页面（若有）仍存在；本页无残留。
- [ ] Lint / Prettier 通过；无未使用的 import。

## 完成标准

- [ ] `workbench/index.tsx` 行数 ≤ 30。
- [ ] 页面视觉与 `LeaderOverviewPage` 完全一致。
- [ ] `workbench/README.md` 更新到新版。
- [ ] 合并提交的 commit 信息清晰标注 "feat(F5/T05): replace workbench with LeaderOverviewPage"。
