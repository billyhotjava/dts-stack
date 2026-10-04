# 领域画像（Gate G0）

**勘察日期**：2026-07-31
**数据来源**：当前 v2.2.3 Compose/PostgreSQL 只读基线；客户生产环境尚未画像
**结论**：当前环境足以冻结目标 owner 和迁移协议，但不足以批准任何客户环境无条件 DROP。

## 1. 统一语言

| 术语 | 定义 | 禁用/迁移词 | 事实源 |
|---|---|---|---|
| WarehousePlan | 数仓规划聚合，拥有计划上下文、来源与规划约束 | 旧 `modeling_plan` 不再作为 owner | Sprint-67/79 canonical 链 |
| ModelSpec v2 | 四类逻辑模型的当前 head | semantic model、SQL model、business object 不是平行模型 owner | Sprint-67/74 |
| ModelSpecRevision | 不可变逻辑设计快照；所有依赖固定 revision | 隐式 latest、可变 snapshot | Sprint-67 |
| StageGate | 对明确 revision 汇集标准、质量、权限、artifact 的判定器 | 页面自行判断、直接改状态 | 本 Sprint ADR-81-03 |
| Lifecycle | 唯一模型状态机 | resource/service 直接写状态 | Sprint-69/74 |
| ReleaseCandidate | 构建、审核、发布的唯一候选控制面 | vNext candidate、快捷 facade 自有状态 | Sprint-69/76 |
| Materialization | candidate 驱动的真实构建、relation 核验和资产交接 | compile 即物化、假成功 | Sprint-76 |
| CatalogAssetKey/Type | 跨域资产唯一身份 | datasetId 字符串拼接、模型专用资产键 | DTS A3 |
| QualityEvidence | rule/version/binding/run 固定后的可追溯门禁证据 | quality template、最新一次模糊结果 | 本 Sprint ADR-81-05 |
| DbtExecutionGateway | platform 唯一 dbt/Airflow 执行端口 | `/etl/dbt/run`、直接 Docker 调用 | 本 Sprint ADR-81-08 |
| domain event outbox | 领域集成消息的 durable 事务发件箱 | audit 事件混入 | 本 Sprint ADR-81-07 |
| audit outbox | 审计投递 dts-admin 的 durable 事务发件箱 | 普通日志、进程内 best-effort | DTS D2、本 Sprint ADR-81-06 |

## 2. 业务与架构不变量

| # | 不变量 | 强制级别 | 违反后果 | 出处 |
|---|---|---|---|---|
| D-01 | 建模 owner 只允许 WarehousePlan、ModelSpec v2/revision、StageGate/Lifecycle、ReleaseCandidate、Materialization 一套 | 架构硬约束 | 状态漂移、重复发布 | ADR-81-03 |
| D-02 | 资产身份必须为 `CatalogAssetType + CatalogAssetKey` | 架构硬约束 | 质量、模型、资产无法对账 | DTS A3 |
| D-03 | 质量门禁必须引用明确 rule version、binding 和终态 run | 业务硬约束 | 模板或过期结果误放行 | ADR-81-05 |
| D-04 | 审核、发布、操作员职责不得被快捷入口绕过 | 合规硬约束 | 越权发布 | Sprint-69/76 |
| D-05 | 业务事件和审计事件必须分离且 durable | 合规/可靠性硬约束 | 审计丢失或重放污染业务 | ADR-81-06/07 |
| D-06 | 旧运行面必须在同 Sprint 完成调用方重接和物理删除 | 架构硬约束 | 长期双轨 | ADR-81-09 |
| D-07 | 未画像客户存量不得 DROP；迁移必须 dry-run、可验证、可回滚 | 数据硬约束 | 客户数据不可恢复 | DTS C2～C4 |
| D-08 | 历史 Liquibase changelog 和 dts-admin 中央审计历史永久保留 | 合规硬约束 | checksum 破坏、审计链断裂 | ADR-81-11 |
| D-09 | 密级与业务标签/质量结果分离；跨租户 fail-closed | 合规硬约束 | 数据泄露、错误定级 | DTS D1/D4 |
| D-10 | 最终 E2E 在全部编码结束后集中执行，过程测试不得冒充 E2E | 交付硬约束 | 中间兼容态被误判完成 | ADR-81-12 |

## 3. 当前环境真实数据画像

| 对象/表族 | 实测值 | 设计影响 |
|---|---:|---|
| `modeling_model_spec` | 6 | canonical owner 已有真实 head，不允许重建第二套台账 |
| `modeling_model_spec_revision` | 10 | 迁移必须保持 revision pin/checksum，不只搬 head |
| `modeling_dimension_definition` | 7 | 维度 owner 保留；不并入 business object |
| `modeling_dimension_definition_revision` | 21 | ModelSpec 的维度引用必须保留 revision |
| `modeling_warehouse_plan` | 4 | 旧 plan 迁入/重接目标明确为 WarehousePlan |
| DataMart | 2 | 规划和模型归属不能在迁移中丢失 |
| glossary | 42 | 标准/词汇事实需通过既有 owner 查询，不复制进模型表 |
| modeling template | 4 | 模板不是 ModelSpec 或运行事实 |
| legacy semantic | 0 | 当前环境可走空表退役路径；客户环境仍需画像 |
| `modeling_sql_model` | 0 | 当前环境无数据，但调用方/客户数据未知，先解耦再删 |
| `modeling_business_object` | 0 | 当前环境无数据，迁移分类仍需可执行 |
| 旧 `modeling_plan` | 0 | HTTP/service/entity/table 属优先退役批次 |
| legacy run/candidate/materialization 表族 | 0 | 不得据此推断客户环境或跳过备份 |
| `gov_quality_template` | 10 | 只能作为规则创建模板，不能作为 StageGate evidence |
| `gov_quality_run` | 0 | 当前环境尚不能证明质量门禁真实旅程；F6 需隔离测试 run |

### 3.1 画像查询清单

实施前由 F0/T02 在每个目标环境只读执行并保存结果、时间和数据库标识：

```sql
select count(*) from modeling_model_spec;
select count(*) from modeling_model_spec_revision;
select count(*) from modeling_dimension_definition;
select count(*) from modeling_dimension_definition_revision;
select count(*) from modeling_warehouse_plan;
select count(*) from modeling_sql_model;
select count(*) from modeling_business_object;
select count(*) from gov_rule;
select count(*) from gov_rule_version;
select count(*) from gov_rule_binding;
select count(*) from gov_quality_run;
select count(*) from modeling_legacy_object_migration_batch;
select count(*) from modeling_legacy_object_migration;
select count(*) from modeling_legacy_api_usage;
```

待退役 manifest 还必须逐表统计：总数、tenant 分布、状态分布、空值率、孤儿 FK、源/目标映射数、冲突数、最大更新时间和 30/90 天调用量。禁止只跑总数。

## 4. 对设计的直接影响

- canonical 有数据而 legacy 当前为空：目标是把调用方切到 canonical 并删除旧 owner，不是把 canonical 重写到新表。
- ModelSpec/Dimension 均有 revision：任何迁移只保存 head 都会破坏可重现性，manifest 必须含 revision 级 checksum。
- 质量模板有 10、运行为 0：StageGate 接线可先做契约/故障测试，但 Sprint 完成前必须生成隔离真实 run 证据。
- 所有旧表当前 0 只支持“当前环境空表路径”；客户环境必须在停机窗口再次画像并按“零数据或已迁移”二选一判定。
- 客户规模未知：列表、outbox、迁移批次和查询 P95 使用保守预算，并在客户画像后收紧，不能用 6 条 ModelSpec 推导生产容量。

## 5. 外部边界

| 系统 | 我方契约 | 当前可用性 | 失败策略 |
|---|---|---|---|
| dts-ingestion/integration | 只提交资产注册/解析命令 | 运行中；业务接线待 F3 | 资产身份未解析则 fail-closed，不创建 ModelSpec |
| dts-admin | AuditService adapter 投递中央审计 | 服务健康；durable outbox 待实现 | 本地 outbox 保留并重试，业务已提交但发布类高风险动作可按策略阻断 |
| Airflow | DbtExecutionGateway adapter 提交/查询/取消 | scheduler/triggerer/webserver 运行中 | 超时后标 UNKNOWN 并对账，禁止重提覆盖 |
| dbt | 版本化 project/profile lease 执行 | 容器运行中；最终链未验证 | 失败回传 candidate attempt，不伪造 relation |
| Catalog/OpenMetadata | 统一资产身份和发布后资产同步 | 既有 owner | 同步失败保留本地发布事实并显式 DEGRADED；不得造第二资产表 |
| 客户环境 | 只读画像、备份、停机迁移 | 未授权/未知 | F5 对应 DROP 阻断，不以 410/兼容壳绕过 |

## 6. 合规与留存

| 要求 | 硬门槛 | 验证 |
|---|---|---|
| tenant/actor/client IP 服务端解析 | 是 | 跨租户 403；IP 使用 `IpAddressUtils.resolveClientIp` |
| 审计 action code 在 dts-admin 字典登记 | 是 | 中央记录不为“未分类” |
| 审计 outbox 不含凭据/完整 profile | 是 | payload schema + secret scan |
| 历史审计不可随旧表删除 | 是 | 退役前后中央审计 count/checksum 不变 |
| 历史 changelog 不修改/删除 | 是 | Liquibase checksum/upgrade IT |
| 客户备份可恢复 | 是 | restore rehearsal + 对象级 checksum |

## 7. 未决问题

- 客户环境待退役表的真实数量、tenant 分布、引用和外部 API 调用方。
- 质量 run 在最终验收环境使用哪条隔离规则/数据集，需用户确认可写测试范围。
- dts-admin 审计接收边界的吞吐上限和维护窗口；不影响分离 outbox 的架构决策，但影响 F3 的批量参数。
