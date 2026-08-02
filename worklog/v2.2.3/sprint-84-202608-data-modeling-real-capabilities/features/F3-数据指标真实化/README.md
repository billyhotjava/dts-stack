# F3：数据指标真实化

**优先级**：P0  
**状态**：CODE_COMPLETE（部署/E2E 待 F5/T02）

## 目标

指标管理员在现有页面完成真实指标查询、新建、校验、保存、引用、发布、版本查看和归档。

## 契约

复用 `/governance/indicators/**` 的 definition/version/reference/run/template/publish-preview；不恢复旧 semantic metric 或第二套指标中心。

| Task | 状态 |
|---|---|
| T01 接入真实指标目录 | CODE_COMPLETE |
| T02 接入编辑校验保存发布 | CODE_COMPLETE |
| T03 接入引用版本七态与测试 | CODE_COMPLETE |

## 当前证据

- `MetricsWorkspace` 已删除 `TYPE_CONFIG`、`METRIC_CATALOG`、客户示例和占位导入/导出动作，目录、计数、域、筛选与刷新均来自真实 Governance Indicator 分页结果。
- `MetricEditor` 已接入真实数据集选择、指标新建/更新、校验、发布预检、首次发布、新版本发布、归档、版本快照与引用只读上下文；无维护权限时明确只读。
- 页面目录不直接依赖 `platformApi`；`src/api/services/indicatorGovernanceService.ts` 是受控 API bridge，页面 adapter 复用现有 `/governance/indicators/**`。
- `pnpm exec vitest run src/pages/data-modeling/indicatorWorkspaceAdapter.test.ts src/pages/data-modeling/metricsWorkspaceIntegration.source-contract.test.ts`：11/11 通过。
- 上述 6 个 F3 文件 `pnpm exec biome check`：通过；Sprint-84 前端冻结快照 Chrome 95 生产构建通过，浏览器 E2E 按 F5/T02 集中门禁执行。
- `pnpm exec tsc --noEmit --pretty false`：通过；`MetricEditor.tsx` 满足不超过 800 行的 UI 门禁。
- 指标上下文刷新使用 request epoch；对象切换后迟到响应不会覆盖当前指标。
- 最终冻结快照已通过总代码 Review 和 Chrome 95 build；部署后真实发布/版本/审计仍待 F5/T02。
