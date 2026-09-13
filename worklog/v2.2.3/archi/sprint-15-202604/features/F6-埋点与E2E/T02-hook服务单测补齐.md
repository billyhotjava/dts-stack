# T02: hook / service 单测补齐

**优先级**: P1
**状态**: READY
**依赖**: F3/T01, F4/*, F5/T04

## 目标

确保 `src/pages/workbench/**` 目录下单测行覆盖率 ≥ 80%。F3-F5 各 task 已列出各组件单测；本 task 做剩余补齐 + 整合。

## 技术设计

### 补齐清单

逐项检查是否已在子 task 中写过，否则本 task 新建：

| 模块 | 单测已列入 task | 本 task 是否补写 |
|---|---|---|
| `useWorkbenchRole` | F3/T01 | 确认通过率 |
| `DeptSelect` | F3/T02 | 确认通过率 |
| `BizDomainSelect` | F3/T03 | 确认通过率 |
| `TimeRangeSelect` | F3/T04 | 确认通过率 |
| `WorkbenchFilterBar` | F3/T05 | 补 `audit_event_emitted_on_each_filter_change` |
| `KpiRow` | F4/T01 | 确认通过率 |
| `KpiSecondary`（MoM/Ratio/Static） | F4/T02 | 确认通过率 |
| `DomainMatrix` | F4/T03 | 确认通过率 |
| `TopReportsBlock` | F5/T01 | 确认通过率 |
| `CoreAssetsBlock` | F5/T02 | 确认通过率 |
| `ScreenStrip` | F5/T03 | 确认通过率 |
| `LeaderOverviewPage` | F5/T04 | 本 task 覆盖 T03 之外的整体集成（见 F6/T03） |
| `relativeTime` helper | - | 本 task 补：`relativeTime_shows_刚刚 / 分钟 / 小时 / 天` |
| `classificationColor` helper | - | 本 task 补：`returns_correct_colors_for_S1_S4` + `default_for_unknown` |
| `audit` util | F6/T01 | 确认通过率 |

### 覆盖率命令

```bash
pnpm --filter dts-platform-webapp test -- --coverage \
  --testPathPattern "pages/workbench" \
  --coverageReporters json-summary text
```

在 `it/coverage-workbench.json` 里落一份覆盖率报告（作为 IT 证据）。

### 行覆盖红线

| 目录 | 目标 |
|---|---|
| `src/pages/workbench/hooks/` | ≥ 90% |
| `src/pages/workbench/components/` | ≥ 80% |
| `src/pages/workbench/LeaderOverviewPage.tsx` | ≥ 75%（部分分支依赖 E2E） |

## 影响范围

- 新增：`src/pages/workbench/hooks/relativeTime.test.ts`
- 新增：`src/pages/workbench/hooks/classification.test.ts`
- 可能补：其他被发现覆盖率不足的组件 `.test.tsx`

## 验证

- [ ] 跑全量 workbench 单测：`pnpm --filter dts-platform-webapp test -- --testPathPattern "pages/workbench"` 全通过。
- [ ] 查看 HTML coverage report，所有三项达红线。
- [ ] 在 `worklog/v2.2.3/sprint-15-202604/it/` 下保存覆盖率 JSON summary。

## 完成标准

- [ ] 覆盖率红线达标。
- [ ] 测试无 skipped、无 `.only`、无 `console.*` 留白。
