# DTS 原理与设计方案（devspec）

本目录用 Archify 展示 DTS 的原理与设计方案，**粒度到模块**：先按服务拆出功能模块并画出模块之间的调用关系，
再沿关键业务链路给出模块间的方法级调用时序。图是阅读入口，Markdown 设计文档记录每条关系的源码依据（`文件:行号` 或类名）。

- 阅读入口：**https://dev.yuzhicloud.com/**（登录后进入门户 `index.html`，左侧导航、右侧图示；部署与账号见 [deploy/README.md](deploy/README.md)）
- 本地阅读：静态服务器打开 `index.html`；Markdown 通过 `doc.html?f=<文件>` 渲染（含 Mermaid）
- 日期：2026-09-14
- 源码基线：模块图 `da531fb16`；链路图与链路文档 `915097e22`（每篇文档头部记录）
- 服务级高层材料：`worklog/v2.2.3/ref/intro/`
- 本目录只记录源码与配置事实，不代表运行、部署或业务验收结论。

## 1 内容组织

### 1.1 模块架构（服务 → 模块 → 模块间调用）

| 编号 | 图 | 内容 | 依据 |
|---|---|---|---|
| 01 | [10-system-modules.html](10-system-modules.html) | 系统模块全景：四个后端服务的主要模块与跨服务调用 | [10-module-architecture.md](10-module-architecture.md) §3 |
| 02 | [11-platform-modules.html](11-platform-modules.html) | dts-platform 核心业务模块：建模、目录、治理、SQL、构建、数据服务、权限安全 | §4 |
| 03 | [12-platform-support-modules.html](12-platform-support-modules.html) | dts-platform 支撑模块：审计、事件、查询网关、数据源、元数据、回滚、报表登记 | §5 |
| 04 | [13-ingestion-modules.html](13-ingestion-modules.html) | dts-ingestion：任务编排核心、执行层、平台回调 | §6 |
| 05 | [14-admin-modules.html](14-admin-modules.html) | dts-admin：审批、用户角色、组织人员、双证书、审计 | §7 |
| 06 | [15-analytics-modules.html](15-analytics-modules.html) | dts-analytics：受治理查询网关、发布登记、语义、大屏 | §8 |

### 1.2 关键链路（模块间调用时序）

| 编号 | 链路 | 服务 | 设计文档 | 图 |
|---|---|---|---|---|
| 07 | T1 主链服务总览 | 全部 | [assets/call-graph-and-dispatch.md](assets/call-graph-and-dispatch.md) | 00-t1-overview |
| 08—09 | 建模主链：规划 → 模型定义 → 加工配置 → 构建 → 发布 → 数据集 → 契约读取 | dts-platform | [01-modeling-mainline.md](01-modeling-mainline.md) | 01-modeling-ports、01-modeling-publish |
| 10 | 接入执行：任务 → Addax/Airflow → 目标表 → 目录/血缘 | dts-ingestion | [02-ingestion-execution.md](02-ingestion-execution.md) | 02-ingestion-execution |
| 11 | 管理审批：变更单 → 同意 → 分派执行 → 审计 | dts-admin | [03-admin-approval.md](03-admin-approval.md) | 03-admin-approval |
| 12 | 分析消费：契约缓存 → 查询执行 → 图表/看板 | dts-analytics | [04-analytics-consumption.md](04-analytics-consumption.md) | 04-analytics-consumption |
| 13 | 数据质量：规则版本 → 触发 → 执行 → 状态收敛 | dts-platform | [05-quality-workflow.md](05-quality-workflow.md) | 05-quality-run |
| 14 | 分类分级：统一密级 → 封存投影 → 传播 → 标签/脱敏 | dts-platform | [06-classification-tagging.md](06-classification-tagging.md) | 06-classification-seal |
| 15 | BI 编排：看板/大屏 → 发布快照 → 平台登记 Outbox | dts-analytics | [07-bi-orchestration.md](07-bi-orchestration.md) | 07-bi-publish |
| 16 | 数据服务：API/令牌/限流/脱敏 → 数据产品 → 交换与对账 | dts-platform | [08-data-services-push.md](08-data-services-push.md) | 08-data-api-invoke |
| 17 | PKI 与 MDM：双证书登录 → 验签会话 → MDM 握手/回调 | admin/platform | [09-pki-mdm-integration.md](09-pki-mdm-integration.md) | 09-pki-login |

`dts-metrics` 未出现在标准 Compose 运行时，标注为 legacy，不纳入。

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
├── index.html                   门户：左侧导航（模块架构 / 关键链路 / 配套材料），右侧图示
├── doc.html                     Markdown 设计文档阅读页（markdown-it + Mermaid，离线可用）
├── login.html                   登录页（由 nginx /api/login 服务端校验）
├── README.md                    本文件
├── handoff.json                 图表交付、校验与人工阅读记录
├── 10-module-architecture.md    模块划分与模块间调用依据（图 01—06）
├── 01-…09-*.md                  关键链路设计文档（接口、时序、事务与错误语义、证据表）
├── 10-…15-*-modules.html        模块架构图（Archify architecture）
├── 00-…09-*.html                链路总览、端口关系与调用时序图（Archify architecture/sequence）
├── diagrams/                    Archify 图源 JSON
├── receipts/                    每张图的 validate / deliver / visual-check 回执与截图
├── deploy/                      nginx 配置样例与发布说明
└── assets/
    ├── rest-inventory-<service>.md      脚本生成的接口清单
    ├── interface-impl-inventory.md       接口/实现/抽象类关系清单
    ├── call-graph-and-dispatch.md        接口→实现→注入点与分派形态图谱
    ├── error-code-inventory.md           按模块的错误码首次出现位置（脚本生成）
    ├── scripts/                          清点脚本与 rebuild-diagram.sh
    └── vendor/                           markdown-it、Mermaid（固定版本，含 LICENSE）
```

## 6 当前状态（2026-09-14）

- 模块架构：6 张 Archify 模块图（系统全景 + platform 2 张 + ingestion/admin/analytics 各 1 张），全部通过 showcase 校验（9/9 检查、0 诊断）、deliver 交付与四档桌面尺寸包含检查，已人工查看截图；依据见 `10-module-architecture.md`。
- 关键链路：9 篇链路设计文档与 11 张链路图（2 架构/关系 + 9 时序），均通过 showcase 校验；482 处带前缀的 `文件:行号` 引用已脚本核对（handoff.json）。
- 链路文档内嵌的 Mermaid 由 `doc.html` 在浏览器渲染；渲染失败会在图下方提示。
- 模块图按 `import` 与构造注入统计关系，未区分运行期开关（Kafka、OpenMetadata 同步等）。

## 7 Archify 图表与再生成

图源在 `diagrams/`，回执在 `receipts/`。单张图重建（校验 → 交付 → 尺寸检查，并把回执移入 `receipts/`）：

```bash
assets/scripts/rebuild-diagram.sh architecture 10-system-modules
assets/scripts/rebuild-diagram.sh sequence 01-modeling-publish
```

新增图后在 `index.html` 导航中加一项（`data-source` 指向图源，`data-doc` 指向设计文档），并更新 `handoff.json`。

## 8 维护方式

1. 基线变更后重跑 `assets/scripts/` 中的清点脚本，更新文档中的行号与 SHA；模块结构变化时同步更新模块图与 `10-module-architecture.md`。
2. 只修改有证据变化的内容；新增模块间关系必须在设计文档中写明依据。
3. 每张图不超过 12 个模块，次要依赖写入图下方卡片，避免退化为全量依赖图。
