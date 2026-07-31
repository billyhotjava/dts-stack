# DTS 数据接入交互原型

本地只读原型，用来验证「数据接入」链路的重构方案。不连接任何后端，数据全部为示意。

## 启动

在仓库根目录执行：

```bash
python3 -m http.server 4174 --directory worklog/prototype
```

浏览器打开：

```text
http://127.0.0.1:4174/dbconn/
```

## 为什么做这个原型

现网把"把一个库接进湖"这件事拆在 **3 个顶级模块、6 个菜单**里：

| 菜单 | 路径 | 性质 |
|---|---|---|
| 连接器目录 | `/foundation/connectors` | 管理员一年动一次 |
| 数据源管理 | `/foundation/data-sources` | 每次都要 |
| 驱动管理 | `/foundation/jdbc-drivers` | 管理员一年动一次 |
| 数据源结构采集 | `/catalog/metadata` | 跳到「数据治理」 |
| 数据入湖配置 | `/explore/etl/transform` | 跳到「数据开发」 |
| 接入变更记录 | `/foundation/access-changes` | 审计 |

同时前端有多组裸 JSON 文本框要用户手写，后端 `IngestionTask` 也以多个 jsonb 列承载配置。
本原型论证的是：这不只是 UI 问题，而是连接契约、资源模型和版本化执行计划缺位。

## 本轮目标架构

用户仍只看到“一条数据接入”，但这个页面只是 `AccessWorkspace` 聚合视图，不把底层对象物理合并：

```text
ConnectorDefinition
  ├─ ConnectionProfile ── SourceResource（表 / API 资源）
  └─ SourceArtifact（一次上传的文件及版本）
          │
          ▼
AccessPlan ── PipelineRevision ── AdmissionDecision
                       │
                       ├─ ExecutionPlan ── ExecutionAdapter
                       └─ Run / Checkpoint / SchemaSnapshot / Drift
```

- 数据库和 API 复用 `ConnectionProfile`，一个连接可以服务多个接入计划。
- 离线文件是带哈希、封条和生命周期的 `SourceArtifact`，不伪装成长连接。
- 编辑已发布计划时创建新 Revision；发布前，旧 Revision 继续生效。
- 密级是独立的准入证据，最终等级取连接下限、文件封条、字段识别和人工审批的最高有效等级。
- 引擎 JSON 是 `ExecutionPlan` 的编译产物，不是用户配置的事实来源。

## 已覆盖的界面

| 界面 | 说明 | 截图 |
|---|---|---|
| 数据接入（列表） | 连接、资源、计划的统一工作视图；生命周期、健康、准入、最近执行分栏展示 | [prototype-list.png](./prototype-list.png) |
| 向导 ① 来源 | 连接器选择器（含驱动/运行时状态）+ schema 驱动表单 + 来源预检 | [prototype-wizard-api.png](./prototype-wizard-api.png) |
| 向导 ② 资源 | 数据库选表；API 定义 endpoint/分页/游标；文件解析、预览与字段差异 | [prototype-wizard-tables.png](./prototype-wizard-tables.png) |
| 向导 ③ 发布 | 同步策略、密级准入依据、发布前检查、引擎无关 ExecutionPlan | [prototype-wizard-policy.png](./prototype-wizard-policy.png) |
| 接入详情 / 运维 | 生效/草稿 Revision、运行历史、结构漂移、落地预检、变更记录、只读配置 | [prototype-detail.png](./prototype-detail.png) |
| 参数归属 · 按字段 | 现网字段迁移清单，可按归属筛选；不是新架构的配置基线 | [prototype-params.png](./prototype-params.png) |
| 参数归属 · 按现网屏幕对照 | 「数据源」「运行治理策略」两屏逐字段对照，含已核实的真实行为 | [prototype-screen-map.png](./prototype-screen-map.png) |
| 连接器与运行时 | 驱动、连接器契约、执行适配器与替代引擎评估下沉系统管理 | — |
| 设计说明 | 六个决策 + 现状对照 + 本原型未处理的问题 | [prototype-notes.png](./prototype-notes.png) |

## 减负结论：区分「必须决策」与「有正确默认」

现网把不同层级的字段平铺成必填或可填，用户必须对每一项做判断。数据库批量接入中，
真正**没有默认可言**的主要是 5 项；API 和文件会把“选表”替换为各自的资源定义或文件解析确认：

| 必须由创建者决策 | 为什么没有默认 |
|---|---|
| 连接参数 | 只有创建者知道源在哪 |
| 接哪些资源 | 数据库表、API endpoint 或文件版本属于业务范围 |
| 同步方式 | 可推荐，但后果重大需确认 |
| 调度频率 | 业务时效要求 |
| 接入名称 | 标识 |

其余字段应尽量由连接器契约推导、采用可解释的平台默认，或由平台托管。密级不能作为普通继承字段随意降级；
它由准入证据计算并冻结。湖地址、凭据和表名规范属于平台策略，任务只保存引用。

**做法**：主流程只留必须决策项，其余压成一行 chips 摘要 + 「查看并调整」。减负不是砍功能，是把决策权从「必填」降级为「可改」。

第③步默认只展示必须确认的策略和发布门禁；重试、脏数据、并发等运行参数收进“平台默认”摘要。
减负不是删除能力，而是把业务决策、平台默认和引擎编译参数分层。

## 参数归属结论

`param-map.js` 保留现网字段迁移盘点。历史文档中的字段总数与分类小计存在口径差异，
因此本页不再把某个总数当作新架构基线；落地前需从实际 DTO/schema 生成一次可复现清单。归属原则如下：

| 归属 | 含义 |
|---|---|---|
| 连接器契约 | 端点、TLS、鉴权等可复用连接属性 |
| 来源资源 | 表/字段、API endpoint/recordPath、文件版本/解析规则 |
| Pipeline Revision | 同步方式、调度、checkpoint、目标策略引用 |
| 平台配置 | 湖凭据、命名规范、默认配额、运行时绑定 |
| 准入证据 | 连接下限、文件封条、字段识别、审批决定 |
| 编译产物 | Addax/HTTP/File/CDC 运行参数，只读且可重建 |

三组最值得先处理：

1. **`writerJdbcUrls` / `writerUsername` / `writerPassword`** —— 用户建任务时要手填**数据湖**的地址、
   账号、密码，每个任务各存一份。轮换一次要改 N 个任务，且普通数据工程师必须知道写入口令。
2. **`readerConfig` + `readerExtraConfig` + `writerConfig` + `writerExtraConfig`** ——
   四个 JSON 兜底框。"config 之外还要有 extraConfig"本身就说明第一个 config 不够用又不敢改。
3. **`taskConcurrency` / `sourceConcurrency` / `projectConcurrency`** —— 三个并发数都让用户填。
   后两个是护源库、护集群的闸门，应当是配额而非输入项。

## 执行引擎决策

原型先建立统一的 `ExecutionAdapter` 边界，再决定是否替换具体引擎，避免把 Addax JSON 直接换成另一套引擎 JSON：

| 组件 / 候选 | 本轮定位 |
|---|---|
| Addax | 暂时保留数据库全量、时间戳/主键增量的批量适配器；文件仅在合适时复用 Writer |
| DTS API Runtime | 保留并收敛为声明式 API 资源适配器，checkpoint 属于资源 Revision |
| Native File Runtime | 负责 Artifact、加密解密、解析预览、稀疏单元格与版本差异 |
| Airflow | 只负责编排 Revision 和重试依赖，不保存业务配置真相 |
| dbt | 只负责入湖后的模型、测试和转换，不承担源端连接与抽取 |
| Debezium / Flink CDC | CDC 专用候选；独立验证权限、日志保留、状态恢复和目标语义 |
| SeaTunnel | Addax 的受控 PoC 候选，不在本轮直接替换 |
| Airbyte | 借鉴声明式 Connector/Stream/State 模型，不整体嵌入第二套控制面 |

SeaTunnel PoC 必须用同一份 `ExecutionPlan` 对比 Addax，覆盖 MySQL/PostgreSQL/Oracle/达梦/Inceptor、
千万级全量与增量、断点恢复、结构漂移、脏数据、脱敏日志、离线部署、ARM/Kylin、驱动授权和资源成本。

## 建议的浏览路径

1. **列表页** —— 看"一条接入"作为单一心智对象长什么样。
2. 分别走一遍 **MySQL、HTTP / REST API、Excel / CSV**，比较三类来源在第②步的差异。
3. API 中确认分页和增量游标属于 endpoint 资源，而不是 Base URL 连接。
4. 文件中确认 Artifact 的哈希、封条、安全扫描、解析预览和 Revision 字段差异。
5. 第③步查看准入依据、发布前检查、标准 ExecutionPlan 与可选编译产物。
6. **接入详情**查看生效 Revision、待发布草稿、Run 引用版本及分维度状态。
7. **连接器与运行时**查看当前组件职责与替代引擎 PoC 门槛。

## 文件结构

```text
index.html          外壳
prototype-data.js   连接器 schema 与示意数据
schema-form.js      schema 驱动的表单渲染器（核心机制）
resource-steps.js   数据库 / API / 文件的资源步骤
wizard.js           三步向导外壳、准入与发布
runtime-strategy.js 执行适配器边界与引擎决策矩阵
views.js            列表 / 详情 / 连接器管理 / 设计说明
app.js              路由
styles.css          样式（沿用 dataworks-kimball 的设计变量）
```

`schema-form.js` 只负责可复用连接属性；`resource-steps.js` 承载数据库、API、文件不可强行统一的资源语义。
连接器契约最终应由后端版本化 contract 提供，原型静态数据不能成为第三份事实源。

## 本原型刻意没有处理的问题

1. **服务边界**：`dts-platform` 与 `dts-ingestion` 存在平台代理与运行时反向取连接信息的双向调用，
   属于后端拓扑，需单独立项。
2. **文件体积**：`AddaxJobService` 3284 行、`IngestionTaskResource` 2903 行、
   `IngestionTaskService` 2865 行，均远超项目 800 行上限。落地时会大量触碰，建议先拆再改。
3. **存量迁移**：现有任务的 8 个 jsonb 列如何映射到 schema 字段，需要逐连接器的迁移表与回退方案。
4. **权限模型**：未体现按部门/密级的接入可见性，实际需接 ABAC。

## 已知偏差

- 交互为演示性质：连接测试、样例请求、文件预检和发布结果均为固定示意。
- 三类资源分别使用固定 demo 数据，不会真实扫描数据库、调用 API 或上传文件。
- “发布 Revision”只演示状态与契约，不会创建 DAG、Addax 作业或 dbt 模型。
- 详情页的运行/漂移/变更数据为固定示意，与列表卡片上的计数不完全一致。
