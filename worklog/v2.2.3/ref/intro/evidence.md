# DTS 图示的源码依据与限制

日期：2026-09-12。源码提交：`2e91f12f5b3709f9892dff4d88d4689b1dabfc02`。路径和行号对应此提交，后续代码变更时应复核。

本轮为源码、配置和项目约定的文档梳理。没有请求业务写接口、读取业务数据、构建产品或操作容器。GitNexus 索引已在前一轮刷新到本提交；概念查询没有返回流程，具体类 context 可以定位符号，因此本轮关系依据以当前文件为准，不把空查询当成不存在实现。

## 证据源

| 编号 | 当前文件 | 支持的事实 |
|---|---|---|
| E01 | [父 POM](../../../source/pom.xml#L20) | common、platform、ingestion、metrics、admin、analytics 的模块声明 |
| E02 | [标准 Compose](../../../docker-compose-app.yml#L6) | 管理、平台、分析、接入及身份、调度、元数据、存储、消息、代理依赖的配置声明 |
| E03 | [平台前端路由](../../../source/dts-platform-webapp/src/routes/sections/dashboard/static-routes.tsx#L44) | 平台直接导入分析、图表和看板页面 |
| E04 | [平台配置](../../../source/dts-platform/src/main/resources/config/application.yml#L303)、[接入客户端](../../../source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/ingestion/IngestionServiceClient.java#L60) | 平台到 ingestion 和 admin 的出站配置，以及接入调用客户端 |
| E05 | [接入任务执行](../../../source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/IngestionTaskService.java#L1244) | Airflow 开启时传递作业路径、任务和执行标识；检查触发结果并记录执行状态 |
| E06 | [接入任务创建及元数据调用](../../../source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/web/rest/IngestionTaskResource.java#L626)、[OpenMetadata 适配器](../../../source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/openmetadata/OpenMetadataAdapter.java#L52) | reader/writer 与任务衔接；血缘注册及元数据采集受配置和请求条件控制 |
| E07 | [模型物理输入解析](../../../source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelImplementationDependencySnapshotResolver.java#L136) | 模型声明来源、输入绑定和已解析版本必须一致，拒绝遗漏或陈旧绑定 |
| E08 | [物化计划](../../../source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelMaterializationPlanService.java#L57)、[物化分发](../../../source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelMaterializationDispatchService.java#L277) | 解析模型依赖和关系、检查执行条件、准备项目并通过网关提交 Airflow 构建 |
| E09 | [模型 serving 投影](../../../source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/serving/CatalogModelServingService.java#L59)、[平台事件分发](../../../source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/event/PlatformEventKafkaDispatcher.java#L38) | 发布／物理就绪投影及事件路径；Kafka 发送由开关控制，失败写失败记录 |
| E10 | [语义同步](../../../source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/serving/CatalogModelSemanticSyncService.java#L115)、[重试规则](../../../source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/serving/CatalogModelSemanticSyncService.java#L251) | 领取租约候选、发布语义、数据集投影、按版本记录结果；异常决定下一次尝试时间或终止 |
| E11 | [语义发布客户端](../../../source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/serving/AnalyticsSemanticPublishClient.java#L48) | 检查开关、地址与服务令牌；用 HTTP POST 发布，转换远端或网络错误 |
| E12 | [QueryDataset 投影](../../../source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/serving/ModelQueryDatasetProjectionService.java#L64) | 检查物理资产与 DWS／ADS 层，派生密级、组装 READY 契约、保存 PUBLISHED 版本及归档旧版本 |
| E13 | [分析契约客户端](../../../source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/analysis/PlatformAnalysisDatasetContractClient.java#L52) | 优先读取缓存；未命中时请求平台，核对 ID、版本、校验和与 PUBLISHED 状态 |
| E14 | [管理入口权限](../../../source/dts-admin/src/main/java/com/yuzhi/dts/admin/config/SecurityConfiguration.java#L81) | `/api/admin/**` 按三员角色集合及兼容角色别名放行 |
| E15 | [创建变更](../../../source/dts-admin/src/main/java/com/yuzhi/dts/admin/web/rest/AdminApiResource.java#L2000)、[提交变更](../../../source/dts-admin/src/main/java/com/yuzhi/dts/admin/web/rest/AdminApiResource.java#L2074) | 创建 DRAFT；提交置 PENDING，保存、记录审计并尝试通知 |
| E16 | [同意并执行](../../../source/dts-admin/src/main/java/com/yuzhi/dts/admin/web/rest/AdminApiResource.java#L2138)、[拒绝](../../../source/dts-admin/src/main/java/com/yuzhi/dts/admin/web/rest/AdminApiResource.java#L2208)、[执行分派](../../../source/dts-admin/src/main/java/com/yuzhi/dts/admin/web/rest/AdminApiResource.java#L3381) | 同意设置 APPROVED 并执行；支持资源的成功应用可置 APPLIED；异常置 FAILED；拒绝置 REJECTED，随后记录相应结果 |
| E17 | [项目 AGENTS.md](../../../AGENTS.md#L1)、[正式构建入口说明](../../../builds/dts-build.sh#L70) | 开发、构建测试与部署职责边界，以及构建／打包入口 |
| E18 | [PostgreSQL 目录同步](../../../source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/infra/PostgresCatalogSyncService.java#L113)、[接入资产展示投影](../../../source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/ingestion/IngestionFlowProjectionService.java#L65) | 目录同步与读取既有数据集投影是独立能力，不等同于接入写入成功 |

## 01 架构总览

| 图中关系或归组 | 依据 | 解释 |
|---|---|---|
| 用户入口 → 平台 | E02、E03 | 将 Traefik、管理前端和平台前端聚合为入口；只绘制业务主线，管理和分析的其他浏览器调用省略 |
| 平台 → 接入 | E04、E05 | 平台负责业务控制，接入服务负责执行任务；出站地址不证明调用在运行环境成功 |
| 接入 → 调度与计算 | E05、E08 | 右侧聚合 Airflow／Addax／dbt。接入样本走 Airflow 与 Addax；模型物化由平台直接走构建网关，不能把图中的聚合节点解释为 ingestion 直接调用全部工具 |
| 平台 → 管理 → Keycloak | E02、E04、E14 | 表示组织、权限及身份管理的依赖方向；不是完整的登录或令牌校验时序 |
| 平台 → 分析 | E10、E11 | 主关系为语义发布；分析端反向读取平台契约详见图 04 |
| 接入 → OpenMetadata | E06 | 可调用注册血缘与元数据采集；存在启用条件和跳过结果 |
| 平台 → Kafka | E09 | 平台事件经调度分发；链路是否开启、消费端与失败恢复不由本图证明 |
| 共享依赖卡片 | E01、E02 | PostgreSQL 被多项服务依赖，不代表共用业务表；OpenMetadata 的 Elasticsearch 依赖与公共代码 common 不展开连线 |

标准 Compose 未声明独立 `dts-metrics` 服务，父 POM 仍有该模块，构建入口将其示例标为 legacy。不能把源目录数量等同于部署服务数量。模型的 dbt 构建入口与接入任务的 Addax 路径也不能合并为一条统一执行管道。

## 02 数据业务主流程

| 关系 | 依据 | 约束 |
|---|---|---|
| 来源 → 接入 → 目标物理数据 | E05、E06 | 表示数据读取和目标写入能力；数据库、API 与文件不是相同的实现分支 |
| 物理数据 → 目录资产 | E18 | 图中归入“接入物理资产”节点；目录同步／登记是独立步骤，写入成功不证明已经登记可见 |
| 已绑定资产 → 模型输入 | E07 | 使用已声明来源及解析版本；不表示自动为接入数据创建模型 |
| 模型与物化 → 模型物理产物 | E08、E09 | 受计划、依赖、项目制品和运行结果控制；源码有路径不证明本次已生成实际产物 |
| DWS／ADS 产物 → QueryDataset | E10、E12 | 通过 serving 候选与语义同步衔接；物理资产必须满足相应启用与层级条件 |
| QueryDataset → 分析 | E13 | 表示发布契约供分析消费；真正的业务数据查询、权限撤销与看板结果需要具体用例验证 |
| 接入 → 元数据目录 | E06 | 箭头表示元数据／血缘信息，不表示将源业务数据复制到 OpenMetadata |

本图采用“能力衔接”的阅读顺序。来源绑定、模型物化、语义发布和用户选择分析数据集各自有前提，不承诺无人工操作的一键全流程。质量检查和密级治理是各对象的独立约束，不能仅凭图中连线认定已通过。

## 03 权限与通用审批

| 图中路径 | 依据 | 当前行为 |
|---|---|---|
| DRAFT → PENDING | E15 | 创建与提交是两个入口；图示为常规使用顺序 |
| PENDING → APPROVED → 执行 | E16 | 同意接口写决定人、时间和理由，并立即调用应用分派 |
| 执行 → 成功结果 → 审计 | E16 | 已支持资源分支可设置 APPLIED；结果保存后记录审批审计并尝试通知 |
| 执行 → FAILED → 审计 | E16 | 捕获执行异常并保存原因，事务模板尝试回滚，再记录结果；外部身份系统副作用不因此获得分布式回滚保证 |
| 拒绝 → REJECTED → 审计 | E16 | 拒绝保存决定与理由，并写审计 |
| 三员集合管理入口 | E14 | 入口角色集合不等于每个资源动作都有专门授权 |

当前通用同意／拒绝入口未见统一的合法前态检查、禁止自审检查和并发决定防护。图中 PENDING 后分叉是常规流程表达，不能理解为其他前态已被后端强制拒绝。未支持资源类型的分派结果也不能一概标成 APPLIED。

本图不覆盖 Keycloak 专用审批、所有业务密级策略和所有内部服务入口。后续应由管理／安全维护者核对角色 × 资源 × 动作 × 入口矩阵，再针对自审、非法前态、重复决定、并发与外部副作用设计验证。

## 04 模型发布到分析时序

1. 同步服务从 serving 台账领取到期候选与租约（E10）。前置的模型发布／物理就绪投影见 E09。
2. 同步服务调用分析端语义发布客户端；客户端要求有效配置并带服务凭据发出 HTTP POST（E11）。
3. 远端发布调用返回后，同步服务才调用查询数据集投影（E10）。这一顺序没有跨服务分布式事务保护。
4. 投影检查资产启用和 DWS／ADS 层，生成或复用契约及发布版本（E12）。不符合层级／启用条件时可以跳过；图中展示满足条件的成功路径。
5. 同步服务按模型版本记录同步结果（E10）。异常记录错误和重试时间；永久错误或重试耗尽停止自动重试。
6. 后续分析请求在契约缓存未命中时，向平台 GET 指定版本契约（E13）。缓存命中不发生图中的 HTTP 请求。
7. 返回契约后，由**分析端客户端**核对数据集 ID、版本、校验和和 PUBLISHED 状态；不匹配会拒绝当前引用（E13）。图中返回箭头将“收到响应和客户端校验”压缩为一个阅读步骤。

四个参与者是内部职责与服务的组合，不能理解为四个独立微服务。“Serving 台账、语义同步、查询数据集”均属于 platform 的相关职责；“分析服务”是 analytics。

需进一步验证的场景：远端语义发布成功而本地投影失败、重复同步、租约过期、撤销发布、权限撤销和缓存有效期。这些列为验证主题，不表示本轮已复现缺陷或已验收。

## 05 团队交付流程

依据 E17：开发目录修改、静态检查、评审后 commit／push；构建测试目录确认分支和工作区，`git pull --ff-only` 并核对 SHA，再使用正式入口测试、构建、打包。正式容器按部署目录的配置发布，真实页面验收单独记录。

图中六个节点是交接阶段归组，不表示构建命令内部总是“先测试后编译”，也不表示每次变更必须全量重建。检查与构建范围应与变更相称。运行目录按实际正式部署配置确认，本图不凭历史记录填写一个未经核实的运行位置。

源码／测试、正式制品、容器部署、真实页面验收分别留证；关联提交 SHA、镜像 ID／digest、版本清单与包校验和。图示本身不属于正式 DTS 产品交付包。

## 本轮验证范围

自动图形校验和浏览器尺寸检查用于证明这些图可生成、可打开和可阅读，不能证明业务关系已经经过运行验收。机器生成的 `.visual-check.json` 中 `visualReview` 固定为 `pending`；人工阅读结论单独放在 `handoff.json`，保持原始机器回执不被改写。

全部待确认项保留在上述对应主题内；没有给未知的团队成员指定责任，也没有新增部署、可用性或安全合规承诺。
