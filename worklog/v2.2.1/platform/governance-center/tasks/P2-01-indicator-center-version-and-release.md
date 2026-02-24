# P2-01 指标中心版本与发布治理

`status`: `done`
`priority`: `P2`

## 目标

补齐指标从草稿到发布的版本治理能力。

## 后端实施点

1. 指标版本快照、版本差异接口。
2. 发布前依赖校验（引用码表/维度/上游指标）。
3. 发布失败原因标准化。

## 前端实施点

1. 指标详情增加版本时间线与差异对比。
2. 发布前预检面板（阻断项/告警项）。
3. 发布后回滚到历史版本。

## 验收标准

- 指标发布有预检，不满足条件不能发布。
- 已发布指标可回滚并保留审计轨迹。

## 完成情况

1. 后端已补齐版本治理接口：
   - `GET /api/governance/indicators/{id}/versions/diff`
   - `POST /api/governance/indicators/{id}/versions/{version}/rollback`
2. 发布预检结果结构化：
   - `blockingIssues` / `warningIssues`
   - `publishGate` / `failureReasonCode`
   - 保留兼容字段 `issues`
3. 发布接口硬门禁：
   - `POST /api/governance/indicators/{id}/publish` 在预检不通过时直接返回 `400`。
4. 前端指标中心已补齐：
   - 版本对比（`CURRENT` vs 历史版本）
   - 历史版本回滚
   - 发布预检阻断/告警分层展示
5. 构建验证：
   - `source/dts-platform`: `./mvnw -DskipTests compile` 通过
   - `source/dts-platform-webapp`: `pnpm build` 通过
