# F1: 指标模块所有权迁移

**优先级**: P0
**状态**: DONE
**目标**: 将语义与指标中心的前端页面归入 `pages/metrics/**`，让代码所有权与 Sprint-25 菜单 IA 一致。

## 任务

| Task | 状态 | 内容 |
|------|------|------|
| T01 | DONE | 新建 `pages/metrics` 和 `pages/metrics/semantic` 页面入口 |
| T02 | DONE | 更新静态路由和动态 resolver 到新 metrics 页面路径 |
| T03 | DONE | 保留旧治理 / 建模路径兼容 |
| T04 | DONE | 增加 ownership smoke，防止 `/metrics/*` 再指回 governance/modeling 主实现 |
| T05 | DONE | `pnpm build` 验证 |

## 验收标准

- `/metrics/center` 不再直接指向 `pages/governance/IndicatorCenterPage`。
- `/metrics/dictionary` 不再直接指向 `pages/governance/IndicatorsPage`。
- `/metrics/semantic*` 不再直接指向 `pages/modeling/Semantic*`。
- 旧路径继续可访问。

## 实现记录

- `MetricCenterPage`、`MetricDictionaryPage` 主实现已迁入 `source/dts-platform-webapp/src/pages/metrics/`。
- 语义建模子入口已迁入 `source/dts-platform-webapp/src/pages/metrics/semantic/`。
- `pages/governance/IndicatorCenterPage` 与 `pages/governance/IndicatorsPage` 保留为兼容 wrapper。
- 已更新 `static-routes.tsx` 与 `dynamic-resolver.tsx`，`/metrics/*` 不再指向 governance/modeling 主实现。
- 验证命令:
  - `DTS_SMOKE_OUT=worklog/v2.2.3/sprint-26-202605/it/evidence/20260502-local/metrics-module bash worklog/v2.2.3/sprint-26-202605/it/scripts/metrics-module-smoke.sh`
  - `pnpm build` from `source/dts-platform-webapp`
