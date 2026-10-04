# P2-02 公共码表导入回滚可视化

`status`: `done`
`priority`: `P2`

## 目标

把码表导入的预检、应用、回滚做成页面闭环。

## 后端实施点

1. 强化导入运行记录（批次、冲突、影响行数）。
2. 导入事务与回滚接口幂等校验。
3. 补齐导入失败分类。

## 前端实施点

1. 导入向导：预检 -> 冲突处理 -> 提交。
2. 导入历史页：支持查看 diff 与一键回滚。
3. 回滚结果可追踪到受影响码值。

## 验收标准

- 导入前能看到冲突明细。
- 导入后可在页面内完成回滚。

## 完成情况

1. 后端补齐导入运行记录查询接口：
   - `GET /api/governance/reference-codes/{codeTypeId}/items/import/runs`
   - `GET /api/governance/reference-codes/{codeTypeId}/items/import/{runId}`
2. 结构化回滚接口增强幂等：
   - 已回滚批次再次回滚返回 `idempotent=true`，不重复变更数据。
3. 前端公共码表页面已补齐导入历史闭环：
   - “结构化导入”弹窗新增“导入历史”入口；
   - 历史列表支持查看批次详情（状态、策略、冲突/错误计数）；
   - 批次详情支持查看差异明细（ADDED/UPDATED/REMOVED）；
   - 历史批次支持一键回滚。
4. 构建验证：
   - `source/dts-platform`: `./mvnw -DskipTests compile` 通过；
   - `source/dts-platform-webapp`: `pnpm build` 通过。
