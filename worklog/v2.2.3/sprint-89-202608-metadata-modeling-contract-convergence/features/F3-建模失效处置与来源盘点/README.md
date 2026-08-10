# F3: 建模失效处置与来源盘点

**优先级**: P0
**状态**: API_DONE / UI_BLOCKED_INPUT
**依赖**: F2 完成；真实登录/来源样本用于验收
**价值**: 让建模人员在现有数仓规划中看见来源变化、理解影响并安全重确认，而不是遇到笼统的 stale 错误。

## 唯一页面与接口 owner

- 页面：复用 `/data-modeling/planning/spaces?view=baseline&tab=sources`。
- API：扩展现有 `GET/PUT /api/modeling/warehouse-plans/{id}/baseline/sources`。
- 数据：复用 `modeling_warehouse_plan_source`、source mapping 与 `catalog_schema_drift_event`。
- 不新增菜单、路由 owner、来源台账或浏览器本地事实。

## 状态与动作

| 状态 | 展示 | 允许动作 | 后续门禁 |
|---|---|---|---|
| CURRENT | 当前版本已确认 | 查看详情、排除 | 通过 |
| COMPATIBLE_DRIFT | 有兼容更新 | 查看差异、重确认、排除 | 已引用字段复核通过可继续，保留提醒 |
| BREAKING_DRIFT | 来源变化影响模型 | 查看差异、修复映射、排除 | 阻断 |
| MISSING | 来源不可用 | 去数据资产查看、排除 | 阻断 |
| UNKNOWN/REVIEW_REQUIRED | 状态待确认 | 查看证据、人工判断 | 默认阻断发布 |

写动作必须由服务端重新读取 currentVersion 并做 optimistic check；若用户查看后又发生采集，返回 409，禁止盲目覆盖。

## Task

| Task | 状态 |
|---|---|
| T01-补齐来源状态差异与重确认契约 | DONE |
| T02-在现有数仓规划入口完成来源处置 | BLOCKED_INPUT |

## DoD

- [x] GET 返回结构化状态/diff/action，不泄露无权限 schema
- [x] PUT 重确认有 expectedVersion、幂等与审计，竞争更新返回稳定 409
- [ ] UI 五态、空/加载/错误/只读状态齐全，Chrome 95 通过
- [x] 反向建模、编译、发布和物化消费同一 confirmed/current 语义
