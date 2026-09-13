# 预期产物与所有权

## 1. 产物链

| 阶段 | 产物 | 主键/版本 | 唯一所有者 | 下游消费者 | 禁止行为 |
|---|---|---|---|---|---|
| 计划 | WarehousePlanHeader | planId/version | modeling warehouse plan | 工作台、StageProjection | 页面/session 自造计划 |
| 业务分类 | DomainBinding | planId+domainId/version | 计划持引用，catalog 持正文 | 模型创建器、权限 | 复制分类正文或另建 semantic domain |
| 来源 | SourceBinding | planId+sourceRef/version | 计划持引用，接入/目录持正文 | ModelSpec、门禁 | 映射到业务对象 |
| ODS 原始/标准化表 | Catalog asset + ingestion mapping/run | assetId/version + runId | 数据接入/目录 | WarehousePlan 来源盘点、DWD 模型 | 创建为四类 ModelSpec 或由建模页复制物理表正文 |
| STG 技术节点 | SQL/dbt/调度 artifact | artifactId+checksum+runId | 实现/调度 | 编译、运行、血缘 | 进入业务模型台账或作为独立业务发布物 |
| 策略 | PlanningPolicy | planId/version | WarehousePlan | 四类表默认和验证 | 每页各存一份分层规则 |
| 数据标准正文 | DataElement/ReferenceCode/MeasurementUnit/NamingTerm | standard ID/version | standards/governance | 模型字段、质量、指标 | 在模型或计划复制正文 |
| 维度 | DIMENSION ModelSpec | modelSpecId/revision | modeling | FACT/SUMMARY/APPLICATION | 再写业务对象/独立维度真值 |
| 四类表 | ModelSpec（DIMENSION/FACT→DWD、SUMMARY→DWS、APPLICATION→ADS） | modelSpecId/revision | modeling | dbt、质量、资产、指标 | SQL 页面创建第二模型；以 ODS/STG 作为目标层 |
| 字段标准引用 | StandardBinding | modelSpecId+field+standard version | 标准正文由标准模块；模型持引用 | release gate、schema | 复制标准正文或静默跟随最新版 |
| 实现 | SQL/schema/test/doc artifact | artifactId+model revision+checksum | modeling/dbt | compile/test/publish | artifact 无 model owner |
| 运行 | PipelineRun | runId+model revision | modeling/ops | 运维、StageProjection | 仅显示外部 runId 无回链 |
| 发布 | Review/Release record | releaseId+model revision | model lifecycle | 资产、BI、血缘 | 以页面点击标记发布 |
| 资产/血缘 | Asset/Lineage reference | external ID/version | catalog/lineage | 消费与影响分析 | 模型模块复制资产正文 |
| 指标 | MetricDefinition/ref | metricId/version | 指标模块 | BI、报表、服务 | businessObjectCode 作为锚点 |

## 2. 四类表具体产物

ODS_RAW/ODS_STANDARDIZED 是接入层物理资产，STG 是实现过程中的技术产物，均不属于本节四类 ModelSpec。已有 ODS 表经元数据同步和来源盘点成为 `sourceRefs`；尚未产生的 ODS 表先由接入任务创建，运行并同步后再纳入计划。历史 ODS/STG ModelSpec 的专属分类/只读迁移仍待 F3-T07 实现；目标是保留审计、血缘和迁移证据，不继续写入。

### 维度表

- 维度键、属性、层级、历史策略；
- 来源或生成策略；
- 标准绑定；
- 可复用 modelSpecId；
- SQL/schema/tests 和发布资产。
- 目标层固定 DWD；物理上游允许 ODS_RAW/ODS_STANDARDIZED/STG，以及当前计划已确认的存量或外部管理 DWD 资产；generationStrategy 可作为受控替代或组合输入。

### 明细表

- 一行含义、粒度键、事实形态和时间语义；
- 上游输入可以是计划持有的主/关联物理来源及 JOIN，也可以是锁定 revision 的上游 ModelSpec；
- 目标数仓分层属于当前 ModelSpec，物理来源分层属于 SourceBinding，不互相推导；
- 维度引用和字段标准；
- 构建/质量/发布/运行证据。
- 目标层固定 DWD；上游只允许技术层 sourceRefs 或锁定 revision 的 FACT@DWD，不反向依赖 DWS/ADS；DIMENSION 通过 dimensionRefs 引用。

### 汇总表

- 上游模型和版本；
- 分组维度、聚合粒度、周期、度量/指标引用；
- 刷新策略和漂移状态；
- DWS 类实现和发布记录。
- 目标层固定 DWS；至少依赖一个锁定 revision、CURRENT、无环的 DIMENSION/FACT@DWD 或 SUMMARY@DWS。

### 应用表

- 消费目标、上游模型、输出粒度和字段契约；
- 刷新/SLA、权限和服务/报表引用；
- ADS 类实现、发布和消费证据。
- 目标层固定 ADS；至少依赖一个锁定 revision、CURRENT、无环的任意合法 DWD/DWS/ADS 四类 ModelSpec。

## 3. 完成证据边界

每个 StageProjection COMPLETE 必须引用上述真实产物 ID、版本和 checkedAt。页面访问、展开 Tab、写入 sessionStorage、显示成功 toast 都不是产物，也不能作为完成证据。
