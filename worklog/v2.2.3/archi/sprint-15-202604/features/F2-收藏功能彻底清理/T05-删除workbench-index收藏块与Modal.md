# T05: 删除 workbench/index.tsx 收藏块与 Modal

**优先级**: P0
**状态**: READY
**依赖**: T04

## 目标

从 `source/dts-platform-webapp/src/pages/workbench/index.tsx` 删除所有收藏相关的代码：state、Modal、Table 列、"编辑收藏"按钮。本 task **只做删除**，不引入新的 LeaderOverviewPage（那是 F5/T05 的工作）；执行完后 `index.tsx` 仍是旧的通用工作台，只是少了收藏功能。

## 技术设计

### 要删除的符号 / 片段

- `FavoriteFormValues` type
- `favorites` state、`favoriteLoading` state（如有）
- `WorkbenchFavorite` / `workbenchService.favorites()` 相关 fetch、useEffect
- 收藏 Card 区域 JSX
- 收藏 Modal（`<Modal>`、`<Form>`、相关 onFinish handler）
- `workbenchService.createFavorite` / `updateFavorite` / `deleteFavorite` 调用
- `EditOutlined` 如果仅被收藏 "编辑收藏" 用，一并从 `@ant-design/icons` 的 import 里删除
- `Form` / `Input` / `Modal` import 如仅被收藏使用，一并删除

### 删除前扫描

```bash
grep -n "favorite\|Favorite" source/dts-platform-webapp/src/pages/workbench/index.tsx
```

把所有命中点列出来，对照本 task 的删除清单对号入座，避免漏删。

### 保留（本 task 不动）

- KPI 4 卡、`MiniAreaChart` / `MiniBarChart` / `MiniDonutChart`、大屏 Table、最近动态列表、常用入口（非收藏）、资产趋势——F5/T05 会在 LeaderOverviewPage 里彻底替换；本 task 先保留以降低 diff 复杂度。

### 验证

- [ ] `grep -in "favorite\|Favorite" source/dts-platform-webapp/src/pages/workbench/index.tsx` 无命中。
- [ ] `pnpm tsc --noEmit` 通过（与 T04 合并提交验证）。
- [ ] `pnpm dev` 启动前端，进入 `/workbench`，页面无收藏卡片、无"编辑收藏"按钮、无 Modal 相关报错。
- [ ] DevTools Network 面板：进入工作台**不**触发 `GET /api/workbench/favorites`。

## 影响范围

- `source/dts-platform-webapp/src/pages/workbench/index.tsx`

## 完成标准

- [ ] 文件内零 favorite 符号残留。
- [ ] 与 T04 合并 commit 后类型检查 + 运行时均通过。
- [ ] 页面视觉比对：收藏卡完全消失，其他保持不变；F5 会在此基础上继续替换。
