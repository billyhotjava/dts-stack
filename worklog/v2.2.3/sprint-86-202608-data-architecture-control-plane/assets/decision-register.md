# Sprint-86 决策登记簿

状态说明：`ACCEPTED` 为已确认约束；`DIRECTION_CONFIRMED` 为方向已确认但精确契约待冻结；`PROPOSED` 为待讨论建议；`OPEN` 为未决问题。

| ID | 决策点 | 当前选择 | 状态 | 说明 |
|---|---|---|---|---|
| ADR-86-01 | 作用域 | 当前阶段按平台全局设计 | ACCEPTED | 不做租户选择器或租户级架构字典副本；权限和审计仍保留 |
| ADR-86-02 | 业务树 | 业务分类 1:n 数据域，数据域单父级 | ACCEPTED | 复用 `catalog_domain.parent_id`，不引入第二棵域树 |
| ADR-86-03 | 分层关系 | 数仓分层与业务树正交 | ACCEPTED | 模型/资产分别引用业务归属和技术分层 |
| ADR-86-04 | 资产范围 | 稳定、可寻址、可发现的真实关系均为资产 | DIRECTION_CONFIRMED | 资产存在与治理/发布/可消费状态分离；ephemeral/CTE/临时对象排除 |
| ADR-86-05 | SOURCE 语义 | SOURCE_SYSTEM 作为资产来源，外部源资产 `warehouseLayer=null` | PROPOSED | 当前 `warehouse_layer=SOURCE` 仅作兼容值，不继续扩展为总分层 |
| ADR-86-06 | 指标 category | 指标必须有业务分类归属 | DIRECTION_CONFIRMED | `metricType` 和可选 `metricGroup` 另设，不能复用 category 多义表达 |
| ADR-86-07 | 指标业务链 | 原子指标绑定数据域与业务过程；派生/复合按来源继承并校验 | PROPOSED | 跨分类复合指标政策仍 OPEN |
| ADR-86-08 | 控制面归属 | 建议把公共架构字典从数据建模中独立为“数据架构” | PROPOSED | 只移动/收敛既有能力，不新建平行表、API 或 CRUD |
| ADR-86-09 | 页面形态 | 平面台账用 Table，层级架构字典用树/分组+Table（**按客户口径 10–50 单一形态设计**），复杂设计用列表+编辑器 | PROPOSED | 模型批量物化在模型列表完成，不把目录树当批处理控件。**（RF-86-14）不承诺双形态自适应**：成本高于任选其一而客户域数未决；「实际规模远超此范围则降级」记为具名风险，阈值判断复用 `DomainScopeNav.tsx:33` 既有 `SEARCH_THRESHOLD = 8` |
| ADR-86-10 | 兼容迁移 | Expand → 双读/回填 → 切换 → 观测 → Contract | PROPOSED | 保留现有 tenant 字段、旧 URL 和旧字段，直到有删除门禁证据 |
| ADR-86-11 | 合规边界 | 密级、权限、业务标签与业务分类/数据域分离 | ACCEPTED | 沿用 DTS 领域不变量 |
| ADR-86-12 | 架构元数据与业务主数据边界 | 架构字典与 MDM 分离，MDM 通过引用/分析投影接入 | DIRECTION_CONFIRMED | 人员/组织/项目/物料等由独立 MDM 维护；Sprint-86 不实现通用 MDM |
| ADR-86-13 | 模型到资产关系 | 语义模型资产与物理资产分离，以 revision/candidate/observation 证据关联 | PROPOSED | 禁止用“已提交/已发布”推断物理表存在或可服务；精确 locator 映射待冻结 |
| ADR-86-14 | 数据集市归属基数 | 目标为业务分类 1:n 数据集市，当前关联表可表达 n:m | OPEN | 决定服务层强制单值并保留关联表，或迁移为显式单外键；未决前不扩展多归属 UI |
| ADR-86-15 | 模型依赖与重复物化 | 模型依赖为 DAG，批量候选固定依赖闭包；同 revision 重试新增 attempt/observation | PROPOSED | DWD→DWS→ADS 不得反向依赖；新 revision 必须进入新 candidate/version entry，旧运行证据不可覆盖；**批量候选原子性/显式排除策略属本 ADR，由 F1/T02 冻结（RF-86-10）** |
| ADR-86-16 | 资产统计架构 | 待定：实时聚合 / 缓存 domainStats / 物化统计表 / 增量维护 | OPEN | 现状 `ASSET_STATS_SCAN_CAP=5000`、每次地图加载两轮全量扫描、`truncated` 判定已记为不可靠；ADR-86-04 扩大纳管范围会更早触顶。必须与 ADR-86-04 同批冻结（RF-86-08） |
| ADR-86-17 | 架构字典写权限强制 | 待定：服务层 port 收口 / 独立角色 / 审计后置检测 / 显式接受约定级 | OPEN | 现有粒度仅 read/write/export，无法按实体类型区分写权限，I07「单一写 owner」运行时不可强制；直接影响 ADR-86-08 能否真正落地（RF-86-09） |

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

## 需要架构评审明确回答

1. 是否接受“数据架构”成为一级产品域；若不新增一级菜单，权威维护入口落在哪里？
2. `catalog_domain` 是否继续作为业务分类/数据域的唯一实体，还是需要显式类型字段？若加类型，如何兼容 parent 语义？
3. 数据集市与主题域是否属于平台级数据架构；数仓计划是否接受定性为建模建设范围而非架构字典？
4. 资产来源字段的枚举、来源系统引用和 warehouseLayer 为空时的展示规则。
5. 资产发现状态、治理状态、发布状态、服务健康是否拆成四轴，还是先收敛为最小两轴？
6. 指标的业务分类是单值还是多值；跨分类复合指标首版如何处理？
7. 旧 `category/domain` 字符串字段保留多久，回填失败记录进入什么待治理队列？
8. `/governance/subjects` 的 4 个 Tab 分别由哪个目标页面承接？
   **（RF-86-06）** owner 挂 F4/T01；前置依赖 F1/T01 冻结「数据集市/主题域是否属平台级架构字典」（见 `capability-boundary.md` canonical owner 矩阵候选项）。
9. **（RF-86-01）** DIM 定性为资产目录兼容值，还是新增 canonical 分层？建模 `ModelSpec.Layer`/dbt 无 DIM，但资产目录后端与前端均允许或展示 DIM。
10. 当前 `modeling_data_mart_domain` 的多对多表达如何迁移/约束为已确认的单业务分类目标？owner 为 F1/T02。
11. APPLICATION 模型是否强制引用主题域；若强制，head/revision/candidate/导入导出如何同步扩展？owner 为 F1/T02。
12. physical observation 到 `CatalogDataset` 的 locator、冲突、重命名、撤销和重放契约是什么？owner 为 F1/T02 + F2/T01。
13. 通用 MDM 首版对象、金记录生命周期及既有人员/组织网关复用方式是什么？转入后续独立 Sprint，不阻塞架构字典冻结。
14. 数据集市/主题域遗留 `tenant_id` 在平台全局模式下采用什么默认 scope 与唯一性规则？不得通过 UI 暴露虚假的多租户能力。
15. ModelSpec 自由文本业务活动如何迁为稳定过程 ID；业务矩阵维度引用、跨计划依赖、循环检测、候选原子性、批量拓扑顺序和二次物化授权如何实现？owner 为 F1/T02。
16. **（RF-86-08）** 资产纳管范围扩大后，域/资产统计采用什么口径与规模上限？现有 5000 扫描上限、两轮全量扫描和不可靠的 `truncated` 判定是否继续沿用？触顶后产品如何表达？owner 为 F2/T01 + ADR-86-16。
17. **（RF-86-09）** 在 read/write/export 三档粒度下，用什么机制强制「架构字典单一写 owner」？若首版只能做到约定级，是否显式接受并记为具名风险？owner 为 F1/T01 + ADR-86-17。

## 决策冻结门槛

- 每个 `PROPOSED/OPEN` 项必须有：选择、理由、影响、兼容路径、反例和验收方式。
- ADR-86-05、06、07、08、09、10、12、13、14、15、16、17 未冻结前，不得开始相关 schema、菜单或页面重构。
- **ADR-86-04 与 ADR-86-16 必须同批冻结**：纳管范围与统计架构互为约束，单独冻结任一方会使另一方的规模假设失效（RF-86-08）。
- **ADR-86-08 冻结前须先给出 ADR-86-17 结论**：控制面归属若无权限强制手段，只是页面层独立（RF-86-09）。
- 两个已确认 UI 缺陷（重置静默清域、窄屏域不可达）若需提前修复，必须另立具名 hotfix Task 并单独批准；只允许缺陷最小修复，不得借机改变菜单、路由、字段或业务语义，且须通过 source-contract、Chrome 95 与真实浏览器验收。
- **ADR-86-05 冻结前须先消解 RF-86-01（DIM 定性）**，否则纳管矩阵的分层轴建立在不成立的事实上。
- ADR-86-13/14/15 冻结前须完成 F1/T02 与 IT-03；目标关系不得覆盖当前 schema 事实。
- 任何删列、删表、删路由必须另做当前环境与客户环境画像，并提供 dry-run/回滚证据。
