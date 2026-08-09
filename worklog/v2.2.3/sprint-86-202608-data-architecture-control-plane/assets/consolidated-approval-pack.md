# Sprint-86 剩余决策集中审批包

**状态**：APPROVED

**形成日期**：2026-08-09

**审批人**：xiezm（兼任产品、数据架构、建模、资产、指标、质量、安全/权限、前端/平台及交付评审角色）

**审批范围**：ADR-86-04/05/06/07/09/10/13/14/15/16/18/19 与 NFR-86-01～13 中尚未批准的设计值。

本文件用于一次性完成 Sprint-86 架构评审。批准只代表架构契约可供 Sprint-87 消费，不代表 schema、代码、迁移、构建、部署或真实 E2E 已完成。

## 1. 已批准、无需重复确认

| ADR | 已批准结论 |
|---|---|
| ADR-86-01 | 当前阶段按平台全局设计，不实现多租户产品能力 |
| ADR-86-02 | 业务分类 1:n 数据域，数据域单父级 |
| ADR-86-03 | 技术分层与业务树正交 |
| ADR-86-08 | 公共架构字典归属逻辑上的平台数据架构控制面 |
| ADR-86-11 | 密级、权限、业务标签与分类/域分离 |
| ADR-86-12 | 只冻结 MDM 边界，具体 MDM 后续独立实现 |
| ADR-86-17 | 唯一 command boundary；方案 A allowlist；部门角色只读 |

## 2. 已批准选择

| 编号 | 已批准结论 | 关键边界/反例 |
|---|---|---|
| D01（ADR-86-04） | 所有稳定、可寻址、可发现的真实表/视图/物化视图进入资产台账 | ephemeral、CTE、临时关系不入账；“发现”不等于“已治理/已发布/可消费” |
| D02（ADR-86-05） | `SOURCE` 归一为 ProducerRef 来源语义且 canonical layer 为空；`DIM` 归一为 `DWD + DIMENSION_TABLE` 角色 | 旧值兼容保留；无法唯一解析进入 issue，不按名称猜测 |
| D03（ADR-86-06/07） | 所有指标单值必填业务分类；ATOMIC 必填域/过程；DERIVED 同分类同域；COMPOSITE 可同分类跨域；v1 禁止跨分类 | category、metricType、metricGroup 分离；旧字符串字段先兼容 |
| D04（ADR-86-09） | 允许一次 B1 显式例外，新增一级“数据架构”入口并重组既有页面；模型记录树改为搜索/筛选 + 多选 Table | 不新增平行 CRUD/API；业务层级选择仍用树；旧路由兼容保留 |
| D05（ADR-86-10） | Expand → 兼容读/双写 → dry-run/分批回填 → 切换 → 观测 → Contract | Sprint-87 首轮不删旧列、表、API 或路由；Contract 另批审批 |
| D06（ADR-86-13） | 语义模型资产与物理资产分离，通过 immutable revision/candidate/observation 关联 | 已提交/已发布模型不推断物理表存在；物理关系复用 CatalogAssetKey |
| D07（ADR-86-14） | 一个数据集市只属于一个业务分类；APPLICATION 必填 dataMartId + subjectDomainId | 兼容期保留关联表并由服务层强制单值；历史多归属人工裁决 |
| D08（ADR-86-15） | 模型依赖为不可变 revision DAG；候选创建全有或全无；运行可逐项失败；再次物化新增 attempt/observation | 不静默排除 blocker；跨计划只接固定已发布 revision；旧历史不覆盖 |
| D09（ADR-86-16） | 资产/域统计采用服务端增量投影 + durable event/outbox + 24h 周期对账 | 在线请求禁止无界全量扫描；旧 fallback 触顶只能显示近似值 |
| D10（ADR-86-18） | ProducerRef 与 RegistrationEvidence 分轴，多渠道发现幂等归并同一资产身份 | 生产者不是登记渠道；冲突进入治理队列，不创建第二资产 ID |
| D11（ADR-86-19） | 发现、治理、发布、服务健康、生命周期五轴保存；消费资格由策略计算并返回 reason codes | 不把 DISCOVERED/PUBLISHED/HEALTHY/RETIRED 塞进单一互斥状态 |

完整选择、失败路径与兼容规则见：

- [`f1-t02-decision-pack.md`](f1-t02-decision-pack.md)
- [`f2-f3-data-contract-pack.md`](f2-f3-data-contract-pack.md)
- [`information-architecture-blueprint.md`](information-architecture-blueprint.md)
- [`implementation-roadmap.md`](implementation-roadmap.md)

## 3. 已批准的设计容量与时限

这些值是客户真实画像尚未补齐时的首版**设计容量**，不是生产容量证明。Sprint-87 G0 仍必须以客户画像校验，超出时回到架构评审调整。

| 编号 | 项目 | 已批准设计值 |
|---|---|---:|
| N01 | 统计设计容量 | 100,000 资产、50 个数据域 |
| N02 | 统计查询 | P95 ≤ 1.5 秒；单请求 ≤ 3 条统计 SQL；禁止无界 Seq Scan |
| N03 | 统计新鲜度 | 投影延迟 ≤ 5 分钟；>10 分钟 STALE；24 小时一次 reconciliation |
| N04 | 批量根模型 `BATCH_MAX` | 100 |
| N05 | DAG | 节点 500、边 2,000、最大深度 20 |
| N06 | 候选预检/创建 | 500 节点设计量 P95 ≤ 5 秒 |
| N07 | 前端轮询 | 前台 5 秒；隐藏页退避到 30 秒；禁止并发轮询 |
| N08 | 运行卡顿/超时 | 10 分钟无 heartbeat 为 STALE；默认最大运行 120 分钟 |
| N09 | 取消 | 5 秒内受理；5 分钟内终态或 `CANCEL_FAILED` |
| N10 | 并发 | 同 plan + environment 最多 1 个 active candidate；同 entry 最多 1 个 active attempt |
| N11 | 页面 | 服务端分页默认 10；四态；Chrome 95；多选不提供无界“选择全部结果” |
| N12 | Contract 观测 | 至少连续 14 天且跨 1 个完整发布周期 |

完整 fitness function 见 [`nfr-budget.md`](nfr-budget.md)。每个值都必须在 Sprint-87 以可执行测试验证；不能达标时阻断对应切片，而不是降低断言。

## 4. 剩余风险与不在本次批准范围的事项

1. 客户/生产资产量、增长率、指标脏数据、SOURCE/DIM 存量和典型批量尚未取得；Sprint-87 F0 保持 `BLOCKED_INPUT`。
2. GitNexus 当前对相关前端/后端查询返回空结果，不能作为无影响证据；Sprint-87 G0 必须刷新索引并逐 symbol impact，前端继续用 scoped `rg` 交叉验证。
3. MDM 只保留引用/投影边界，不批准通用 MDM schema、金记录或 UI。
4. 本次不批准任何删除；旧字段、旧 API、旧路由的 Contract 必须在观测后另立任务和审批。
5. Sprint-87 只有在账号、菜单、Chrome 95、备份、dry-run 样本和审计读取条件齐备后才能从 `BLOCKED` 进入 READY。

## 5. 集中审批记录

- 实际审批时间：2026-08-09（本轮用户明确回复）
- 参与者：xiezm（兼任本批全部 Accountable roles）
- 结论：D01～D11、N01～N12 全部同意，无保留项
- 调整项及替代值：无
- 接受的剩余风险：接受本批 NFR 仅为首版设计容量，客户/生产画像与运行时 fitness functions 仍由 Sprint-87 G0/各实施 Task 验证；不接受以本次批准替代代码、迁移、部署或真实 E2E
- 后续行动 owner/日期：xiezm；Sprint-87 F0 开工前补齐客户画像、GitNexus、登录、Chrome 95、备份与 dry-run 输入；Contract 删除继续另批审批

批准原文：

> 同意 Sprint-86 剩余决策包 D01～D11、N01～N12

依据本文件既有审批边界，本次批准使相关 ADR 可升为 `ACCEPTED`、IT-03～07 可记录架构评审 PASS；Sprint-87 仍保持 `BLOCKED`，直到 G0 外部输入与运行时门禁分别通过。
