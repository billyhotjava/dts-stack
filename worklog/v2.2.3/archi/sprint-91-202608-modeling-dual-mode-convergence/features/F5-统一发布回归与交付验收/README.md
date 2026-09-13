# F5: 统一发布回归与交付验收

> **Sprint-92 验收重定向（2026-08-19）**：本 Feature 继续拥有候选、构建、质量、评审、发布、物化和治理交接回归；不再把“接管后永久只读/不可回退”作为通过条件。创作入口、PUBLISHED fork、visual/code 同草稿与 provenance 无关权限由 Sprint-92 验收，F5 只验证其提交 pins 能无分支进入现有生命周期。

**优先级**: P0
**状态**: BLOCKED（等待 F6～F8、真实登录/Chrome 95/事务 IT，并需先裁决既有 `BUILT → CANCELLED` 状态机与测试语义冲突）

## 目标

证明不依赖 ZIP 的手工 dbt、可视化生成、ZIP apply、**以及接管而来的 dbt** 在完成编辑后使用同一套依赖快照、候选、构建、测试、质量、显式审核、发布和治理交接架构，并完成单表/批量/二次物化、Chrome 95 与真实浏览器验收。

> 接管路径仍是复核结论 A 的必要回归；新增的手工全链路则是产品主验收，必须证明用户无需 ZIP 也能从已有 ODS 一直形成 ADS 和治理证据。

## Feature 关联

- 上游：F0 提供唯一样本/账号，F1～F4 提供双模式和代码实现，F6 提供依赖快照，F7 提供系统生成实现，F8 提供物化计划与执行证据。
- 本 Feature：只编排并验收既有 candidate/build/test/quality/review/publish/retry/rollback，不再实现上游能力。
- 下游：Sprint-93 消费同一 candidate/artifact/relation/quality/publication/lineage correlation；治理缺证据必须回到对应 owner 修复，不在 F5 手工补台账。

## 契约定义

| 类型 | 契约 | 关键字段/签名 |
|---|---|---|
| 候选创建 | `POST /api/modeling/plans/{planId}/release-candidates` | header `Idempotency-Key`；body `{environment,entries:[{modelSpecId,sortOrder,selectedReason}],reason}` |
| 物化预览 | `POST /api/modeling/plans/{planId}/materialization-plans/preview` | 单选/多选共用；候选创建带 `materializationPlanChecksum`，服务端重算，不接受客户端自报依赖条目 |
| 候选读写 | workspace/get/lock/quality/review/publish 等既有命令 | `If-Match` + `Idempotency-Key`；不按 ownership 分叉新 API |
| 发布钉定 | candidate entry | model revision/checksum + implementation id/mode/revision/checksum + dependency checksum 必须对应最新提交实现 |
| UI | 当前 ModelPublishDialog | visual/code 两种模式均返回同一发布入口；代码编辑器内不新增发布按钮 |
| 治理交接 | Sprint-93 既有 evidence seams | 发布/物化输出 candidate、artifact、relation、quality、publication、lineage correlation；不人工补治理台账 |

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 覆盖三种 authoring 方式的统一物化发布链 | P0 | BLOCKED | F2～F4、F6～F8 |
| T02 | 完成发布安全与 Chrome95 集中验收 | P0 | BLOCKED | F0/T01、T01 |

## Definition of Ready

- [x] 发布接口与 checksum 钉定要求已明确
- [x] 手工/可视化/ZIP/接管四类 E2E 路径及同一全链路样本已定义
- [x] 不新增代码模式发布控制面
- [x] stale candidate 已确认按 model revision/checksum/implementation mode 漂移转 STALE，实施期必须补强制回归
- [ ] F0～F4、F6～F8 已完成且 focused tests 通过

## 完成标准

- [ ] 手工 DBT（不使用 ZIP）与 DESIGNER 各有一条四层链从实现到 ONLINE 的真实证据；ZIP/接管路径完成同一控制面等价性回归。
- [ ] 单表和批量物化都先形成依赖计划；二次物化复用模型/资产身份并新增执行历史。
- [ ] 发布失败重试有回归；ownership 转换后旧候选必须转 STALE，缺少该保护时 Sprint 不得 DONE。
- [ ] 所级管理员 `xiezm` 可在现有 command guard 授权范围内完成显式命令链；部门账号越界 403；不因自服务权限自动推进审核/发布状态。
- [ ] 资产、元数据、血缘、质量可沿相同 correlation 查询，不需要发布后人工补登记。
- [ ] Chrome 95 四态、只读账号、权限、深链、dirty/ETag 冲突无 console/network 错误。
- [ ] 建模页首屏体积在预算内。
