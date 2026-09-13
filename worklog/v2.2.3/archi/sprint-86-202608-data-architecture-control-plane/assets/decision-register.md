# Sprint-86 决策登记簿

状态说明：`ACCEPTED` 为已确认约束；`DIRECTION_CONFIRMED` 为方向已确认但精确契约待冻结；`PROPOSED` 为待讨论建议；`OPEN` 为未决问题。

| ID | 决策点 | 当前选择 | 状态 | 说明 |
|---|---|---|---|---|
| ADR-86-01 | 作用域 | 当前阶段按平台全局设计 | ACCEPTED | 不做租户选择器或租户级架构字典副本；权限和审计仍保留 |
| ADR-86-02 | 业务树 | 业务分类 1:n 数据域，数据域单父级 | ACCEPTED | 复用 `catalog_domain.parent_id`，不引入第二棵域树 |
| ADR-86-03 | 分层关系 | 数仓分层与业务树正交 | ACCEPTED | 模型/资产分别引用业务归属和技术分层 |
| ADR-86-04 | 资产范围 | 稳定、可寻址、可发现的真实关系均登记为资产 | ACCEPTED | 资产存在与治理/发布/健康/生命周期/可消费状态分离；ephemeral/CTE/临时对象排除；与 ADR-86-16/18/19 同批批准 |
| ADR-86-05 | SOURCE/DIM 语义 | SOURCE 归一为 ProducerRef 且 canonical layer 为空；DIM 归一为 `DWD + DIMENSION_TABLE` 角色 | ACCEPTED | 旧 SOURCE/DIM 值兼容保留；不向 ModelSpec/dbt 新增 DIM canonical layer；无法唯一解析进入 migration issue |
| ADR-86-06 | 指标 category | 所有指标单值、必填业务分类稳定 ID | ACCEPTED | `metricType` 和可选 `metricGroupCode` 独立；旧 category 字符串只作兼容 |
| ADR-86-07 | 指标业务链 | ATOMIC 固定域/过程；DERIVED 同分类同域；COMPOSITE 可同分类跨域；v1 禁止跨分类 | ACCEPTED | 来源引用固定版本；分类/域/过程不一致时失败关闭，不自动继承歧义值 |
| ADR-86-08 | 控制面归属 | 公共架构字典归属逻辑上的平台“数据架构”控制面 | ACCEPTED | xiezm 以全部评审角色于 2026-08-09 确认；只移动/收敛既有能力，不新建平行表、API 或 CRUD。一级菜单与页面形态由 ADR-86-09 另行决定 |
| ADR-86-09 | 页面形态与导航 | 新增一级“数据架构”作为 B1 显式例外并重组既有页面；模型记录树改为搜索/筛选 + 多选 Table | ACCEPTED | 不新增平行 CRUD/API；层级字典仍用树/分组 + Table；按客户 10～50、两层采用单一形态；旧路由参数无损兼容 |
| ADR-86-10 | 兼容迁移 | Expand → 兼容读/双写 → dry-run/分批回填 → consumer 切换 → 观测 → Contract | ACCEPTED | 首轮不删现有 tenant 字段、旧 URL/API/字段；连续 14 天且跨一个发布周期后，Contract 另批审批 |
| ADR-86-11 | 合规边界 | 密级、权限、业务标签与业务分类/数据域分离 | ACCEPTED | 沿用 DTS 领域不变量 |
| ADR-86-12 | 架构元数据与业务主数据边界 | 只冻结架构字典与 MDM 的边界；MDM 通过引用/分析投影接入，具体能力后续实现 | ACCEPTED | xiezm 以产品、数据架构及受影响 owner 角色于 2026-08-09 确认；通用 MDM 实现转后续独立 Sprint |
| ADR-86-13 | 模型到资产关系 | 语义模型资产与物理资产分离，以 immutable revision/candidate/observation 证据关联 | ACCEPTED | 禁止用“已提交/已发布”推断物理表存在或可服务；物理 locator 解析为既有 `CatalogAssetType + CatalogAssetKey` |
| ADR-86-14 | 数据集市归属基数 | 一个数据集市只属于一个业务分类；APPLICATION 必填 dataMartId + subjectDomainId | ACCEPTED | 兼容期保留现有关联表并由服务层强制单值；0/>1 历史归属进入人工裁决，不自动取第一条 |
| ADR-86-15 | 模型依赖与重复物化 | revision DAG；候选创建全有或全无；运行可逐项失败；同 revision 再物化新增 attempt/observation | ACCEPTED | 服务端不得静默排除 blocker；跨计划只允许固定已发布 revision；新 revision 进入新 candidate/entry，旧证据不可覆盖 |
| ADR-86-16 | 资产统计架构 | 服务端增量统计投影 + durable event/outbox + 24 小时周期对账 | ACCEPTED | 在线请求禁止两轮/无界全量扫描；返回 asOf/freshness/isApproximate；兼容 5000 上限触顶只能显示 `≥5000` |
| ADR-86-17 | 架构字典写权限强制 | 唯一 application command boundary + 现有 authority/对象 guard；方案 A：`ROLE_ADMIN/ROLE_OP_ADMIN/ROLE_INST_DATA_OWNER` 可写平台全局字典，部门角色只读 | ACCEPTED | xiezm 以产品、数据架构、安全/权限及受影响 owner 角色于 2026-08-09 确认。产品/前端授权继续受 read/write/export 粗粒度约束；UI/审计不是安全边界（RF-86-09） |
| ADR-86-18 | 资产来源与登记渠道 | 生产者/上游来源与登记渠道分轴；同一物理身份可有多次发现证据 | ACCEPTED | `SOURCE_SYSTEM/INGESTION_JOB/DBT_MODEL/MANUAL_BUILD/MODELING` 表达生产者或生成链，`SCANNER/INGESTION_EVENT/DBT_SYNC/MATERIALIZATION_OBSERVATION/MANUAL` 表达登记渠道；不得继续用一个 `AssetOrigin` 枚举承载两者 |
| ADR-86-19 | 资产正交状态轴 | 发现、治理、发布、服务健康、生命周期分别记录；消费资格由策略计算 | ACCEPTED | 禁止把 DISCOVERED/PUBLISHED/SERVING/STALE/RETIRED 放入单一互斥状态；状态变化不得覆盖其他轴或历史观测 |

## ADR 责任与评审批次

真实姓名与到会结论写入对应 IT 文件；本表只定义 accountable role 和最晚评审日期。

| 批次 | ADR | Accountable role | 对应评审 | 目标日期 |
|---|---|---|---|---|
| A：统一语言与 owner | 01/02/03/08/11/12/17 | 产品决策负责人 + 数据架构负责人 + 安全/权限负责人（17） | IT-01、IT-02、IT-07 | 2026-08-11 |
| B：关系、资产、指标与 NFR | 04/05/06/07/13/14/15/16/18/19 | 数据架构负责人 + 建模/资产/指标 canonical owner | IT-03、IT-04、IT-07 | 2026-08-17 |
| C：IA 与迁移准入 | 09/10 | 产品决策负责人 + 前端/平台 owner | IT-05、IT-06 | 2026-08-20 |

F1/T01 的选择、反例、兼容路径、当前实现证据和评审结论统一见 [`f1-t01-decision-pack.md`](f1-t01-decision-pack.md)。该文件已由 xiezm 以全部评审角色批准，并由 IT-01/02 与 IT-07 权限子评审留证。

剩余 ADR 与 NFR 的选择统一见 [`consolidated-approval-pack.md`](consolidated-approval-pack.md)。xiezm 于 2026-08-09 明确批准 D01～D11、N01～N12；ADR-86-04/05/06/07/09/10/13/14/15/16/18/19 同批升为 `ACCEPTED`。该结论仅批准架构设计，运行时 fitness functions 仍由 Sprint-87 验证。

## 状态词汇表（RF-86-11）

本 Sprint 三处文档曾各用一套状态词。统一映射如下，验收时以本表为准：

| 语义 | `decision-register` | `data-model-relationships` | `review-findings` |
|---|---|---|---|
| 已冻结，可作为实施依据 | `ACCEPTED` | `CONFIRMED` | — |
| 方向已定，精确契约未冻结 | `DIRECTION_CONFIRMED` | `TARGET_CONFIRMED` | — |
| 建议待评审 | `PROPOSED` | `PROPOSED` | — |
| 未决 / 缺口 | `OPEN` | `GAP` | `OPEN` |
| 事实已纠正并绑定 owner，决策仍未冻结 | — | — | `MITIGATED` |

Task DoD 中的「形成明确结论」「已冻结」一律指本表第一行，即 `ACCEPTED` / `CONFIRMED`。
`MITIGATED` 不等于 ADR 已 `ACCEPTED`。

## 冻结写回规则（RF-86-13）

- **单一写回位置**：ADR 的状态与结论一律写入本文件的 ADR 表。
- `data-model-relationships.md` 的状态列是**投影**，冻结后须同步更新，并在该行标注对应 ADR 编号。
- 两处不一致时以本文件为准；F5/T01 在实施拆分前须校验两处一致。
- 冻结记录必须包含：选择、理由、影响、兼容路径、反例、验收方式（见下方冻结门槛）。

## 已关闭的评审问题与实施移交

1. 一级“数据架构”入口、模型 Table、旧路由和 `/governance/subjects` 四 Tab 映射按 ADR-86-09/15 与 IA 蓝图实施。
2. `catalog_domain` 继续作为业务分类/数据域唯一实体并沿用单父级树；Sprint-87 首轮不新建第二实体/第二棵树，若增加显式类型只能作为兼容 Expand。
3. 数据集市单业务分类、主题域单集市、计划 baseline、APPLICATION 上下文和不可变 revision/candidate 快照按 ADR-86-14/15 实施。
4. SOURCE/DIM、ProducerRef/RegistrationEvidence、五轴状态、统计投影和触顶表达按 ADR-86-04/05/16/18/19 实施。
5. 指标单分类、三类指标上下文矩阵、旧字段兼容和迁移 issue 按 ADR-86-06/07/10 实施。
6. 物理 locator、冲突、重命名、撤销、重放、DAG、候选原子性和二次物化按 ADR-86-13/15 实施。
7. 遗留 tenant 字段继续物理保留，服务端使用平台 scope，UI/API 不暴露虚假租户能力；真正多租户另立 ADR。
8. 通用 MDM 对象、金记录和 UI 继续移交独立 Sprint；不阻塞本架构 Sprint 关闭。
9. 精确 schema/DTO、数据画像、GitNexus impact、迁移 dry-run、运行时 NFR、Chrome 95 和真实 E2E 已移交 Sprint-87 F0～F6，不属于本次架构批准的运行证据。

## 决策冻结结果与实施门槛

- D01～D11 与 N01～N12 已满足选择、理由、影响、兼容路径、反例和验收方式要求，并由 xiezm 于 2026-08-09 集中批准。
- ADR-86-04 与 ADR-86-16、ADR-86-04/05/18/19、ADR-86-13/14/15 均已按要求同批冻结；RF-86-01/08 关闭。
- **ADR-86-08/17 已按前置顺序冻结**：先确认权限强制方案 A，再批准逻辑控制面归属；运行时仍须证明 command boundary、负向授权与审计契约（RF-86-09）。
- 两个已确认 UI 缺陷（重置静默清域、窄屏域不可达）若需提前修复，必须另立具名 hotfix Task 并单独批准；只允许缺陷最小修复，不得借机改变菜单、路由、字段或业务语义，且须通过 source-contract、Chrome 95 与真实浏览器验收。
- Sprint-87 仍须按 G0 → Expand → 兼容迁移 → fitness functions → 真实验收推进；架构批准不解除客户画像、GitNexus、登录、备份和 Chrome 95 阻塞。
- 任何删列、删表、删路由必须另做当前环境与客户环境画像，并提供 dry-run/回滚证据。
