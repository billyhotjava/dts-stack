# DTS 数据接入交互原型

本地只读原型，用来验证「数据接入」链路的重构方案。不连接任何后端，数据全部为示意。

## 启动

在仓库根目录执行：

```bash
python3 -m http.server 4173 --bind 0.0.0.0 --directory worklog/prototype/dbconn
```

浏览器打开：

```text
http://127.0.0.1:4173/
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
- 密级是独立的准入证据，最终等级取连接下限、文件封条、字段识别和人工判定的最高有效等级。
- 引擎 JSON 是 `ExecutionPlan` 的编译产物，不是用户配置的事实来源。

## 已覆盖的界面

| 界面 | 说明 |
|---|---|
| 接入概览 | 使用传统 Table 汇总三类接入，不使用统计卡片或连接卡片 |
| 数据库接入 | 独立入口；新建时只选择数据库连接器，不再混入 API / 文件 |
| API 接入 | 独立入口；直接进入 HTTP API 连接配置，不再重复选择来源类型 |
| 离线文件接入 | 独立入口；直接进入不可变 Artifact 上传与解析链路 |
| 默认策略 | 统一维护版本化平台基线、可覆盖层级与任务覆盖边界 |
| 三步向导 | 来源连接 / 资源定义 / 策略准入，发布时冻结有效配置快照 |
| 接入详情 / 运维 | 生效/草稿 Revision、运行历史、结构漂移、文件预检或运行后异常数据、变更记录 |
| 连接器与运行时 | 驱动、连接器契约、执行适配器与替代引擎评估下沉系统管理 |
| 设计说明 | 架构决策、现状对照和原型边界；字段迁移盘点不进入日常菜单 |

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

**做法**：主流程只留必须决策项，其余压成一行继承摘要 + 「高级覆盖」。减负不是砍功能，是把决策权从「必填」降级为「可改」。

第③步默认只展示必须确认的策略和发布门禁；重试、脏数据、并发等运行参数收进“平台默认”摘要。
减负不是删除能力，而是把业务决策、平台默认和引擎编译参数分层。

### 默认参数采用四层解析，不做二选一

```text
ConnectorContract 硬约束
  → 版本化平台默认
  → Connection / Source 稀疏覆盖
  → Task Revision 稀疏覆盖
  → 发布时冻结 EffectiveConfig 快照
```

- “默认策略”统一界面只维护平台基线、覆盖权限和版本，不复制每个任务。
- 连接只保存端点相关偏离项；任务只保存调度、重试等被允许的偏离项。
- 密级准入、Secret 引用、ODS 目标和破坏性结构漂移不开放成普通任务参数。
- 平台默认升级只影响后续草稿；已发布 Revision 不静默跟随，避免同一版本重跑结果改变。

## 参数归属结论

`param-map.js` 保留现网字段迁移盘点。历史文档中的字段总数与分类小计存在口径差异，
因此本页不再把某个总数当作新架构基线；落地前需从实际 DTO/schema 生成一次可复现清单。归属原则如下：

| 归属 | 含义 |
|---|---|---|
| 连接器契约 | 端点、TLS、鉴权等可复用连接属性 |
| 来源资源 | 表/字段、API endpoint/recordPath、文件版本/解析规则 |
| Pipeline Revision | 同步方式、调度、checkpoint、目标策略引用 |
| 平台配置 | 湖凭据、命名规范、默认配额、运行时绑定 |
| 准入证据 | 连接下限、文件封条、字段识别、规则结果、风险确认或外部决定引用 |
| 编译产物 | Addax/HTTP/File/CDC 运行参数，只读且可重建 |

三组最值得先处理：

1. **`writerJdbcUrls` / `writerUsername` / `writerPassword`** —— 用户建任务时要手填**数据湖**的地址、
   账号、密码，每个任务各存一份。轮换一次要改 N 个任务，且普通数据工程师必须知道写入口令。
2. **`readerConfig` + `readerExtraConfig` + `writerConfig` + `writerExtraConfig`** ——
   四个 JSON 兜底框。"config 之外还要有 extraConfig"本身就说明第一个 config 不够用又不敢改。
3. **`taskConcurrency` / `sourceConcurrency` / `projectConcurrency`** —— 三个并发数都让用户填。
   后两个是护源库、护集群的闸门，应当是配额而非输入项。

## 数据质量规则统一，但触发时机不同

接入模块不维护第二套非空、唯一、字典、格式和范围规则。任务 Revision 只冻结数据质量服务解析返回的
`GovRuleBinding` 快照及其 `GovRuleVersion` 引用，规则 SQL、阈值、字段绑定和 `GovQualityRun` 结果仍由数据质量模块管理。

| 接入方式 | 质量规则触发时机 | 页面 | 默认处置 |
|---|---|---|---|
| 离线文件 | 文件解析进入 staging 后、发布文件 Revision 前 | 文件预检 | 阻断发布，允许形成修正后的解析快照 |
| 数据库 | 同步批次写入 staging 后 | 异常数据 | 当前记录失败计数与样例；目标为异常隔离、正常行提交 |
| API | 响应标准化并写入 staging 后 | 异常数据 | 当前记录失败计数与样例；目标为异常隔离后推进 cursor |

连接、TLS、鉴权、文件加密、恶意内容扫描、格式解析和分页游标属于接入技术门禁，不是数据内容质量规则；
两者可以共同决定是否继续执行，但不能塞进同一套规则定义。

现有正式能力已经具备 `GovRuleVersion → GovRuleBinding → QualityRunService →
QualityDatasetStatementExecutor → GovQualityRun / GovQualityFailingRow` 执行链。本轮原型只复用这一 seam。
正式接线仍需扩展现有触发请求以携带 ingestion execution / staging batch 引用，并让文件暂存数据形成可绑定的
默认湖临时数据集；不得另建一套接入质量运行表。现有执行器只保存受上限保护的失败样例，完整异常隔离、
正常/异常分流与重放属于后端扩展目标，原型不会把它伪装成已经可用的能力。

## 准入与审批边界

默认发布链路不依赖组织审批。`AdmissionDecision` 是规则计算与审计证据，不是审批单：

| 结果 | 含义 | 发布动作 |
|---|---|---|
| `READY` | 密级证据、目标兼容性和运行时门禁均通过 | 具备现有写权限的维护人员确认后直接发布 |
| `CONFIRMATION_REQUIRED` | 存在字段删除、无主键退化或高级覆盖等可接受风险 | 填写影响、接受原因和回退方式，并绑定当前 Revision 与风险指纹后发布 |
| `BLOCKED` | 文件封条/安全扫描、驱动、连接、目标密级兼容性等硬规则失败 | 不可绕过，修复条件后重新计算 |
| `PENDING_EXTERNAL_APPROVAL` | 客户已显式绑定 OA/BPM，且策略要求组织授权 | 等待外部决定引用，不在 DTS 内伪造审批人 |
| `ADMITTED` | 指定 Revision 的准入决定已经生效 | 作为运行和审计的不可变引用 |

高密级本身不等于“需要审批”；关键是目标区域能否承载该等级、发布者是否具备当前权限，以及证据是否完整。
提高密级可直接记录，降低密级默认阻断。客户后续建立审批体系时，通过可选 `WorkflowBinding`
把外部决定接入 `AdmissionDecision`，不改变 Connection、Resource、Revision 与 ExecutionPlan 的主模型。
风险确认记录绑定 `revisionId + resourceKeys + outcome + reasons`；资源或风险集合变化后，旧确认自动失效。
`ADMITTED` 只属于已生效 Revision，不能再次作为发布前置状态。

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

1. **接入概览** —— 用表格查看三类接入的状态、准入和最近运行。
2. 从左侧菜单分别进入 **数据库接入、API 接入、离线文件接入**，确认三类来源不再共用 Tab。
3. 分别新建 **MySQL、HTTP / REST API、Excel / CSV**，比较三类来源在第②步的差异。
4. API 中确认分页和增量游标属于 endpoint 资源，而不是 Base URL 连接。
5. 文件中确认 Artifact 的哈希、封条、安全扫描、解析预览和 Revision 字段差异。
6. 第③步查看默认继承、高级覆盖和准入依据；硬阻断不可发布。
7. **默认策略**确认统一基线与任务稀疏覆盖的边界。
8. **接入详情**查看生效 Revision、待发布草稿、Run 引用版本及分维度状态。

## 文件结构

```text
index.html          外壳
prototype-data.js   连接器 schema 与示意数据
schema-form.js      schema 驱动的表单渲染器（核心机制）
quality-checks.js   统一质量规则版本引用、文件预检与运行后异常数据
resource-steps.js   数据库 / API / 文件的资源步骤
admission-policy.js Revision 级准入决定、风险指纹与发布门禁
access-pages.js     Table 概览、三类独立入口与统一默认策略
wizard-runtime.js   平台默认、任务稀疏覆盖与有效配置摘要
wizard.js           三步向导外壳、来源分流、准入与发布
runtime-strategy.js 执行适配器边界与引擎决策矩阵
views.js            详情 / 连接器管理 / 设计说明
app.js              左侧树形菜单与路由
styles.css          DataWorks 式工作区与 DTS 导航样式
```

`schema-form.js` 只负责可复用连接属性；`resource-steps.js` 承载数据库、API、文件不可强行统一的资源语义。
连接器契约最终应由后端版本化 contract 提供，原型静态数据不能成为第三份事实源。

## 本原型刻意没有处理的问题

1. **服务边界**：`dts-platform` 与 `dts-ingestion` 存在平台代理与运行时反向取连接信息的双向调用，
   属于后端拓扑，需单独立项。
2. **文件体积**：`AddaxJobService` 3284 行、`IngestionTaskResource` 2903 行、
   `IngestionTaskService` 2865 行，均远超项目 800 行上限。落地时会大量触碰，建议先拆再改。
3. **存量迁移**：现有任务的 8 个 jsonb 列如何映射到 schema 字段，需要逐连接器的迁移表与回退方案。
4. **权限模型**：原型只能沿用当前 `read/write/export` 粒度，未假装已有独立发布权限；按部门/密级的 ABAC 需另行建设。
5. **质量运行联动**：现有 `QualityRunTriggerRequest` 只能按 rule / binding / dataset 触发，尚未携带
   ingestion execution 或 staging batch 引用；当前执行器还受默认湖数据集边界约束，需要扩展既有服务完成联动。

## 已知偏差

- 交互为演示性质：连接测试、样例请求、文件预检和发布结果均为固定示意。
- 三类资源分别使用固定 demo 数据，不会真实扫描数据库、调用 API 或上传文件。
- “发布 Revision”只演示状态与契约，不会创建 DAG、Addax 作业或 dbt 模型。
- 详情页的运行/漂移/变更数据为固定示意，与列表卡片上的计数不完全一致。
