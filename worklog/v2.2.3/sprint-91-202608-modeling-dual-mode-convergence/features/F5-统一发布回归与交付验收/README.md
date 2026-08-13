# F5: 统一发布回归与交付验收

**优先级**: P0
**状态**: BLOCKED（等待真实登录/Chrome 95/事务 IT，并需先裁决既有 `BUILT → CANCELLED` 状态机与测试语义冲突）

## 目标

证明可视化生成、原生手工 dbt、**以及接管而来的 dbt** 三条路径在完成编辑后使用同一套候选、物化、质量、审核和发布架构，并完成 Chrome 95 与真实浏览器验收。

> 第三条路径是本 Sprint 新增风险的唯一出口：它验证 F2 接管产出的 `{SQL,SCHEMA,CONFIG}` 制品确实能通过 `compile()` 门禁并走完发布链（复核结论 A）。

## 契约定义

| 类型 | 契约 | 关键字段/签名 |
|---|---|---|
| 候选创建 | `POST /api/modeling/plans/{planId}/release-candidates` | header `Idempotency-Key`；body `{environment,entries:[{modelSpecId,sortOrder,selectedReason}],reason}` |
| 候选读写 | workspace/get/lock/quality/review/publish 等既有命令 | `If-Match` + `Idempotency-Key`；不按 ownership 分叉新 API |
| 发布钉定 | candidate entry | model revision/checksum + implementation id/mode/revision/checksum 必须对应最新提交实现 |
| UI | 当前 ModelPublishDialog | visual/code 两种模式均返回同一发布入口；代码编辑器内不新增发布按钮 |

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 覆盖两种实现方式的统一物化发布链 | P0 | BLOCKED | F2、F3、F4 |
| T02 | 完成发布安全与 Chrome95 集中验收 | P0 | BLOCKED | F0/T01、T01 |

## Definition of Ready

- [x] 发布接口与 checksum 钉定要求已明确
- [x] 三条 E2E 样本路径已定义
- [x] 不新增代码模式发布控制面
- [x] stale candidate 已确认按 model revision/checksum/implementation mode 漂移转 STALE，实施期必须补强制回归
- [ ] F0～F4 已完成且 focused tests 通过

## 完成标准

- [ ] DESIGNER、DBT 原生、DBT 接管而来各有一条从实现到 PUBLISHED 的真实证据。
- [ ] 发布失败重试有回归；ownership 转换后旧候选必须转 STALE，缺少该保护时 Sprint 不得 DONE。
- [ ] Chrome 95 四态、只读账号、权限、深链、dirty/ETag 冲突无 console/network 错误。
- [ ] 建模页首屏体积在预算内。
