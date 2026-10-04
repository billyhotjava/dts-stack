# F4: 契约约束与验收

**优先级**: P0
**状态**: DONE
**目标**: 将 DWD/DWS/ADS 口径从文案升级为前端和 API 调用约束，并补 Sprint-26 smoke。

## 任务

| Task | 状态 | 内容 |
|------|------|------|
| T01 | DONE | DWD 输入选择器只推荐 DWD 明细模型 |
| T02 | DONE | DWS/ADS 发布动作只允许消费模型 |
| T03 | DONE | 旧路由兼容 smoke |
| T04 | DONE | metrics ownership smoke |
| T05 | DONE | 语义 / 血缘真实页面 smoke |
| T06 | DONE | `pnpm build` 证据 |

## 交付说明

- `semanticModelingShared.ts` 新增 `isDwdSemanticInput`，主题域映射、业务对象 Join、指标配置三个入口统一使用 DWD 输入判断。
- `semanticModelingShared.ts` 新增 `isConsumableSemanticModel`，发布页只展示和操作 DWS/ADS 模型。
- 发布页的 dbt 发布、BI 注册、血缘写入动作统一受 DWS/ADS 和审核状态约束。
- 旧 `/governance/indicator-*`、`/modeling/semantic-center*`、`/bi/semantic-modeling` 入口仍保留兼容。

## 验证

- `pnpm build` 通过。
- `it/scripts/metrics-module-smoke.sh` 通过。
- `it/scripts/semantic-real-page-smoke.sh` 通过。
- `it/scripts/lineage-real-page-smoke.sh` 通过。
- `it/scripts/semantic-contract-smoke.sh` 通过。
