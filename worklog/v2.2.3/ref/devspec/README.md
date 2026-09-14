# DTS 接口级设计文档（devspec）

本目录是交付文档套件（`worklog/v2.2.3/spec/`，尤其 02-设计方案、04-集成接口说明）的**工程源**：
先用可核对的方式把 T1 主链写到接口与关键调用级别，评审通过后再汇总进正式正文。

- 阅读入口：**[login.html](login.html)**（登录后进入 index.html 门户；默认用户 `admin`，初始口令 `Devops123@`，对外分享前请修改）
- 日期：2026-09-14
- 源码基线：`915097e220817313ac313091d9c22fb21763796c`（每篇文档头部重复记录）
- 上层门面材料：`worklog/v2.2.3/ref/intro/`（Archify 高层图，服务级）
- 本目录不执行构建、部署或页面验收；只记录源码与配置事实，运行事实与验收结果另行留证。

## 1 范围（首批 T1 四条主链）

| 编号 | 主链 | 服务 | 文档 |
|---|---|---|---|
| 01 | 建模主链：规划 → 模型定义 → 加工配置 → 构建计划 → 分发 → serving → 语义同步 → 查询数据集 | dts-platform | [01-modeling-mainline.md](01-modeling-mainline.md) |
| 02 | 接入执行链：任务定义 → 执行 → Addax/Airflow → reader/writer → 目录/血缘 | dts-ingestion | [02-ingestion-execution.md](02-ingestion-execution.md) |
| 03 | 管理审批链：安全入口 → 变更单 → 提交/同意/拒绝 → 分派执行 → 审计 | dts-admin | [03-admin-approval.md](03-admin-approval.md) |
| 04 | 分析消费链：数据集定义缓存 → 引用校验 → 查询执行 → 图表/看板 | dts-analytics | [04-analytics-consumption.md](04-analytics-consumption.md) |
| 05 | 数据质量与工作流：规则版本 → 触发 → 编排 → 执行 → 状态收敛（T2） | dts-platform | [05-quality-workflow.md](05-quality-workflow.md) |
| 06 | 分类分级与标签：统一密级 → 封存投影 → 传播 → 标签/脱敏（T2） | dts-platform | [06-classification-tagging.md](06-classification-tagging.md) |
| 07 | BI 编排：卡片/看板/大屏 → 发布快照 → 平台登记 Outbox（T2） | dts-analytics | [07-bi-orchestration.md](07-bi-orchestration.md) |
| 08 | 数据服务与推送：API/令牌/限流/脱敏 → 数据产品 → 交换与对账（T2） | dts-platform | [08-data-services-push.md](08-data-services-push.md) |
| 09 | PKI 与 MDM 集成：双证书登录 → 验签会话 → MDM 握手/回调（T2 收尾） | admin/platform | [09-pki-mdm-integration.md](09-pki-mdm-integration.md) |

首批不包含：数据治理质量/标签、BI 编排、数据服务与推送、PKI/MDM、前端页面内部结构。
`dts-metrics` 未出现在标准 Compose 运行时，标注为 legacy，不纳入首批。

## 2 每篇文档的固定结构

1. **范围与入口**：业务入口、服务、模块、基线提交。
2. **REST 接口清单**：方法、路径、控制器类#方法、服务入口、鉴权要求、主要错误码。
3. **接口与实现关系**：Mermaid `classDiagram`，含 interface → 实现类、抽象基类、注入点与分派类。
4. **关键链路方法级时序**：Mermaid `sequenceDiagram` + 步骤表（每步 `文件:行号`）。
5. **事务、幂等与错误语义**：事务边界、CAS/ETag、幂等键、失败重试与终态。
6. **证据表**：结论 → `文件:行号` → 核对方式。
7. **待确认项**：无法从源码确认或需要产品/安全决策的内容。

## 3 证据规则

- 每条结论附 `路径:行号`，路径相对仓库根；行号以文档头部记录的基线提交为准。
- 标注类别：`[源码]` 代码实现、`[配置]` Spring/Compose 配置声明、`[运行]` 实际运行观察、`[待确认]` 未证实。
- 接口清单由脚本生成后再人工核对；生成脚本与产物一并入库，便于基线变更时重跑。
- 调用关系证据优先后端源码；GitNexus 的 `context`/`impact`/`callers` 结果只作为检索线索，最终以源码行号为准。
- 负向断言（"没有校验/没有事务"）必须标注核对范围和时间；不把范围外未检查的内容写成"没有"。
- 不声称任何未执行的运行、部署或验收结论。

## 4 术语表（代码 ↔ 旧文档 ↔ 当前界面）

| 代码标识 | 旧文档用语 | 当前界面用语 | 说明 |
|---|---|---|---|
| ModelImplementation / implementation | 实现、实现配置 | 加工配置 | 模型的数据加工定义（SQL/dbt/普通配置） |
| materialization / materialize | 物化 | 构建 | 执行构建生成物理表的过程 |
| ReleaseCandidate | 发布候选 | 发布单 | 一次发布流程的载体 |
| QueryDataset contract | 契约 | 数据集定义 / 发布结果 | 平台向分析发布的已校验数据集版本 |
| Serving / serving projection | serving 投影 | 服务数据 / 发布状态 | 现行可服务版本与其物理关系 |
| CatalogAsset / asset | 资产 | 数据资产 | 目录中的统一资产身份 |
| StageGate | 阶段门禁 | 交付检查 | 模型版本的交付检查 |
| binding | 绑定 | 关联 / 选择 | 来源、标准、字段的关联关系 |

代码级文档优先使用代码标识，并在首次出现时给出中文解释；不与界面文案混用。

## 5 目录结构

```
ref/devspec/
├── login.html                   登录页（静态门禁；未登录访问 index.html 会自动跳转）
├── index.html                   阅读门户（主题切换、图与文档导航、退出登录）
├── handoff.json                 图表交付/校验/尺寸检查与人工阅读记录
├── README.md                    本文件
├── 01-modeling-mainline.md
├── 02-ingestion-execution.md
├── 03-admin-approval.md
├── 04-analytics-consumption.md
├── 05-quality-workflow.md
├── 06-classification-tagging.md
├── 07-bi-orchestration.md
├── 08-data-services-push.md
├── 09-pki-mdm-integration.md
├── 00-t1-overview.html + .receipt/.validation/.visual-check.json
├── 01-modeling-ports.html + ...
├── 01-modeling-publish.html + ...
├── 02-ingestion-execution.html + ...
├── 03-admin-approval.html + ...
├── 04-analytics-consumption.html + ...
├── 05-quality-run.html + ...
├── 06-classification-seal.html + ...
├── 07-bi-publish.html + ...
├── 08-data-api-invoke.html + ...
├── 09-pki-login.html + ...
├── diagrams/                    Archify 图源 JSON（架构/关系/时序）
└── assets/
    ├── rest-inventory-<service>.md      脚本生成的接口清单
    ├── interface-impl-inventory.md       接口/实现/抽象类关系清单
    ├── call-graph-and-dispatch.md        接口→实现→注入点与分派形态图谱
    ├── error-code-inventory.md           按模块的错误码首次出现位置（脚本生成）
    └── scripts/                          生成脚本（随基线重跑）
```

## 6 当前状态（2026-09-14）

- 已完成 T1 四篇主链文档（内嵌 15 张 Mermaid 作为工程源）与 6 张 Archify 图（2 架构/关系 + 4 时序），四份 REST 接口清单、接口/实现清单、调用分派图谱、错误码清单（719 条首次出现）和生成脚本。
- 6 张 T1 Archify 图 + T2 首图（05 质量链）全部通过 showcase 校验（0 诊断）、deliver 交付与四档桌面尺寸包含检查；门户见 index.html；记录见 handoff.json。
- T2 完成：05 数据质量与工作流、06 分类分级与标签、07 BI 编排、08 数据服务与推送、09 PKI/MDM 均已完成（接口级文档 + 时序图）。
- 四篇文档均补齐主链动作矩阵（端点 → 应用服务 → 关键下游）与关键链路方法级时序。
- 239 处 `文件:行号` 引用已用脚本核对（存在且行号在范围内），并抽样人工比对行内容。
- Mermaid 仅通过基础语法检查（围栏、图类型、常见语法），**未做真实渲染**；评审前建议在支持 Mermaid 的查看器中过一遍。
- 未完成：spec/02、04 正式正文汇总；T2（治理质量/标签、BI 编排、数据服务、PKI/MDM）。

## 7 Archify 图表与再生成

图源在 `diagrams/`，门户为 `index.html`，交接记录为 `handoff.json`。命令（从本目录执行）：

```bash
archify_cli="${HOME}/.agents/skills/archify/bin/archify.mjs"
ARCHIFY_UPDATE_CHECK_DISABLED=1 node "$archify_cli" validate architecture diagrams/00-t1-overview.architecture.json --quality showcase --json > 00-t1-overview.validation.json
ARCHIFY_UPDATE_CHECK_DISABLED=1 node "$archify_cli" deliver architecture diagrams/00-t1-overview.architecture.json 00-t1-overview.html --quality showcase --json > 00-t1-overview.receipt.json
ARCHIFY_UPDATE_CHECK_DISABLED=1 node "$archify_cli" visual-check 00-t1-overview.html --json > 00-t1-overview.visual-check.json
```

其余图替换类型（architecture/sequence）与文件名即可。Archify 只承载架构/关系/时序视图；Markdown 中的 Mermaid 与 file:line 证据仍是内容源，二者随基线一起更新。

## 8 维护方式

1. 基线变更后重跑 `assets/scripts/` 中的清点脚本，更新文档中的行号与 SHA。
2. 只修改有证据变化的内容；新增接口方法必须在清单和对应链路中同步。
3. Mermaid 图须能通过渲染校验；时序图只覆盖关键链路，避免退化为全量调用图。
4. 评审通过后，按 spec 套件模板汇总为 02/04 正文，devspec 保留为可追溯工程源；整理正文前不改动 spec 目录。
