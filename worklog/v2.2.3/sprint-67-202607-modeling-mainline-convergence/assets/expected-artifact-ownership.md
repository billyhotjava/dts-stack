# 预期产物与所有权

## 1. 产物链

| 阶段 | 产物 | 主键/版本 | 唯一所有者 | 下游消费者 | 禁止行为 |
|---|---|---|---|---|---|
| 计划 | WarehousePlanHeader | planId/version | modeling warehouse plan | 工作台、StageProjection | 页面/session 自造计划 |
| 业务分类 | DomainBinding | planId+domainId/version | 计划持引用，catalog 持正文 | 模型创建器、权限 | 复制分类正文或另建 semantic domain |
| 来源 | SourceBinding | planId+sourceRef/version | 计划持引用，接入/目录持正文 | ModelSpec、门禁 | 映射到业务对象 |
| 策略 | PlanningPolicy | planId/version | WarehousePlan | 四类表默认和验证 | 每页各存一份分层规则 |
| 数据标准正文 | DataElement/ReferenceCode/MeasurementUnit/NamingTerm | standard ID/version | standards/governance | 模型字段、质量、指标 | 在模型或计划复制正文 |
| 维度 | DIMENSION ModelSpec | modelSpecId/revision | modeling | FACT/SUMMARY/APPLICATION | 再写业务对象/独立维度真值 |
| 四类表 | ModelSpec | modelSpecId/revision | modeling | dbt、质量、资产、指标 | SQL 页面创建第二模型 |
| 字段标准引用 | StandardBinding | modelSpecId+field+standard version | 标准正文由标准模块；模型持引用 | release gate、schema | 复制标准正文或静默跟随最新版 |
| 实现 | SQL/schema/test/doc artifact | artifactId+model revision+checksum | modeling/dbt | compile/test/publish | artifact 无 model owner |
| 运行 | PipelineRun | runId+model revision | modeling/ops | 运维、StageProjection | 仅显示外部 runId 无回链 |
| 发布 | Review/Release record | releaseId+model revision | model lifecycle | 资产、BI、血缘 | 以页面点击标记发布 |
| 资产/血缘 | Asset/Lineage reference | external ID/version | catalog/lineage | 消费与影响分析 | 模型模块复制资产正文 |
| 指标 | MetricDefinition/ref | metricId/version | 指标模块 | BI、报表、服务 | businessObjectCode 作为锚点 |

## 2. 四类表具体产物

### 维度表

- 维度键、属性、层级、历史策略；
- 来源或生成策略；
- 标准绑定；
- 可复用 modelSpecId；
- SQL/schema/tests 和发布资产。

### 明细表

- 一行含义、粒度键、事实形态和时间语义；
- 主/关联来源及 JOIN；
- 维度引用和字段标准；
- 构建/质量/发布/运行证据。

### 汇总表

- 上游模型和版本；
- 分组维度、聚合粒度、周期、度量/指标引用；
- 刷新策略和漂移状态；
- DWS 类实现和发布记录。

### 应用表

- 消费目标、上游模型、输出粒度和字段契约；
- 刷新/SLA、权限和服务/报表引用；
- ADS 类实现、发布和消费证据。

## 3. 完成证据边界

每个 StageProjection COMPLETE 必须引用上述真实产物 ID、版本和 checkedAt。页面访问、展开 Tab、写入 sessionStorage、显示成功 toast 都不是产物，也不能作为完成证据。
