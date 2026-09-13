# Sprint-72 密级生命周期设计

## 1. 核心模型

密级分为三类事实：

| 类型 | 含义 | 可变性 |
|------|------|--------|
| declaredLevel | 源字段、文件、接入配置或人工创建时声明的密级 | 封存后不可覆盖 |
| detectedLevel | 敏感识别、规则扫描等发现的密级 | 只能追加，不能删除历史或降低 |
| effectiveLevel | 当前所有来源、识别和上游继承的最高密级 | 只能单调升高 |

统一计算：

```text
candidate = max(declaredLevel, detectedLevel, upstreamEffectiveLevels)
effectiveLevel = max(previousEffectiveLevel, candidate)
```

人工操作只能增加一个 `manualFloor`，不能直接写 `effectiveLevel`。最终计算加入：

```text
effectiveLevel = max(previousEffectiveLevel, declaredLevel,
                     detectedLevel, manualFloor, upstreamEffectiveLevels)
```

## 2. 建议持久化

新增统一密级快照，使用既有 `CatalogAssetType + CatalogAssetKey` 标识资产；字段使用稳定 column key：

```text
catalog_classification_snapshot
  id
  subject_type             ASSET | COLUMN | FILE | SCREEN_COMPONENT
  subject_key
  asset_type
  declared_level
  detected_level
  manual_floor
  effective_level
  origin_type              SOURCE_FIELD | FILE | MANUAL | RULE_SCAN | INHERITED
  origin_ref
  sealed_at
  version
  checksum
  propagation_status       SEALED | PENDING_CLASSIFICATION | PENDING_LINEAGE | EFFECTIVE | ERROR
  updated_at
```

新增追加式事件：

```text
catalog_classification_event
  id
  subject_type
  subject_key
  event_type               DECLARED | SEALED | DETECTED | INHERITED | RAISED | RECOMPUTED
  previous_effective_level
  candidate_level
  resulting_effective_level
  trigger_type             INGESTION | OPENLINEAGE | DBT | MANUAL_RAISE | SCAN | MIGRATION
  trigger_ref
  evidence_json
  actor
  occurred_at
```

约束：

- 数据库 check 约束限制四级编码。
- 更新快照必须使用版本/CAS 或行锁，保证并发时仍取最高值。
- 事件表只允许 INSERT，不提供 UPDATE/DELETE 业务 API。
- 现有 `classification` 字段由投影桥写入，迁移期双读对账但不双向覆盖。

## 3. 字段与资产传播

### 字段

- 源字段声明密级后在接入预检封存。
- 文件级密级是文件内全部字段的最低密级。
- 派生字段有字段血缘时，取所有输入字段最高密级。
- 只有表级血缘时，派生字段和资产均取全部上游资产最高密级。
- 无法解析 SQL/UDF/表达式时，不允许猜测更低值，进入 `PENDING_LINEAGE` 并阻断发布。

### 资产

- 数据集/表有效密级为所有字段、文件下限、上游资产和人工下限的最高值。
- ODS、DWD、DIM、DWS、ADS 均使用同一规则，不因分层发生降密。
- 聚合、去重、脱敏和匿名化不自动降低密级。
- 已发布版本保存当时的密级快照，但访问裁决仍读取当前最高有效密级，避免上游升密后旧版本继续低密访问。

## 4. 接入阶段

### JDBC/MySQL/达梦/PostgreSQL

1. Schema 探测读取源端可用的字段密级元数据或配置映射。
2. 接入向导展示表/字段声明和平台映射结果。
3. 未提供密级时标记 `PENDING_CLASSIFICATION`，只允许结构/样本在受控隔离区预检。
4. 用户确认至少一个有效密级后，准入服务生成封存证据。
5. ODS 首次落盘事务必须携带 seal id；没有 seal id 则 fail closed。

### Excel/CSV

1. 上传前选择文件密级，或导入带受支持密级列的模板。
2. 解析阶段允许逐字段升密，不能把字段设置为低于文件密级。
3. 原文件、解析 CSV、错误文件和 ODS 副本共享同一密级来源链。
4. 文件临时区到期清理、归档或销毁均写生命周期事件。

### API/流式

- platform 到 ingestion 的任务契约携带 seal id、资产/字段密级快照版本和 checksum。
- ingestion 不自行发明默认密级；缺失或版本不匹配时拒绝生产写入。
- checkpoint/retry 复用同一 seal，不因重跑生成更低密级。

## 5. 建模与血缘

- OpenLineage 接收 input/output 时，在血缘边提交成功后触发传播。
- dbt manifest/column lineage 优先提供字段级传播；只存在 model dependency 时退化为资产级最高值。
- ModelSpec revision 和 ReleaseCandidate 绑定密级快照版本/checksum。
- 发布前重新解析所有来源；发现密级快照变更、缺失、循环或候选低于上游时阻断。
- 上游升密事件通过 outbox/队列或可靠任务扇出，下游重算幂等，失败进入可重试队列和治理告警。

## 6. 消费资产

| 消费资产 | 计算来源 |
|----------|----------|
| 指标 | 指标表达式引用的全部模型、字段和上游指标 |
| Card/报表 | 查询、指标、数据集和 join 涉及的全部资产 |
| API | 绑定数据集/模型/字段和查询定义 |
| 数据产品 | 产品包含的 API、数据集、指标、报表 |
| BI Report Link | 上游报表或大屏的当前有效密级 |
| 导出文件 | 查询结果有效密级，写入文件封存事实 |

消费资产允许设置高于计算结果的 `manualFloor`，不允许提交更低值。

## 7. 大屏最高密级

大屏解析范围必须包含：

- 全部页面的所有组件；
- 当前数据源和每级下钻数据源；
- card、metric、dataset、SQL/database、API；
- 模板、自定义插件和运行时绑定的服务；
- 已发布版本与草稿的引用差异。

规则：

```text
screenEffectiveLevel =
  max(screenManualFloor,
      everyComponentResolvedLevel,
      everyDrillLevelResolvedLevel,
      everyPublishedRuntimeDependencyLevel)
```

- `static` 组件不提高密级，但不能抵消其他组件密级。
- SQL 必须解析到 catalog 资产；解析不完整时草稿可保存，发布必须阻断。
- API 必须映射到已登记的 `SvcApi` 或数据产品来源；任意 URL 无法解释密级时阻断发布。
- 上游升密后大屏立即升密，缓存失效；原公开链接、越级共享和导出权限重新评估。
- Sprint-24 的密级选择框改为“人工密级下限”，页面展示“系统计算有效密级”和来源解释。
- 原 `PATCH /api/screens/{id}/classification` 不再允许降低；迁移后应收敛为 raise-floor 命令。

## 8. 生命周期阶段

| 阶段 | 准入/审批 | 密级作用 |
|------|-----------|----------|
| 创建 | 接入或创建审批后生成资产事实 | 声明并封存 |
| 存储 | 首次落盘和新增副本前准入 | 副本继承最高密级 |
| 使用 | 查询/预览/下载授权 | 按有效密级检查人员权限 |
| 共享 | 用户/部门/公开链接/API 授权 | 按当前有效密级重新审批 |
| 归档 | 审批后只读归档 | 密级、血缘和保留策略不变 |
| 临时销毁 | 审批后进入回收站 | 停止访问，保留可恢复数据和证据 |
| 永久销毁 | 双人复核和影响分析后执行 | 删除 DTS 管理副本，保留不可逆证明 |

生命周期工作台统一展示各阶段事实，但不强行把现有使用/共享审批表物理合并；通过统一 projection 聚合，避免大范围破坏稳定审批链。

## 9. 销毁边界

- 临时销毁：软禁用、回收站、保留期、一键恢复、审计。
- 永久销毁：仅对平台管理的 PostgreSQL/文件/对象存储/dbt materialization 适配器执行。
- 外部 JDBC 源：永久销毁只删除 DTS 接入任务、缓存、落地副本和密钥引用；绝不 DROP 源表。
- 执行前必须完成下游影响分析、共享撤销、运行任务停用、快照确认和双人复核。
- 执行后保存 `destructionProof`：对象、范围、适配器、执行人、批准人、时间、结果摘要、校验和、失败重试信息。

## 10. 兼容与迁移

1. 盘点所有旧 `classification` 字段及其写入口。
2. dry-run 生成来源候选和冲突报告，不写库。
3. 以旧字段、字段敏感识别、血缘上游和当前消费引用计算初始事实。
4. 任何计算结果低于旧有效值时保留旧值并记录冲突，绝不回写较低值。
5. 分资产类型启用新读路径，双读对账稳定后冻结旧写路径。
6. 最后将旧字段降为兼容投影；不在本 Sprint 物理删除旧列。

## 11. 失败策略

- 缺密级：`PENDING_CLASSIFICATION`，阻断生产落盘/发布。
- 缺血缘：`PENDING_LINEAGE`，取已知上游最高值并阻断可能产生更低结果的发布。
- 传播失败：保留原有效密级，进入重试队列，不能回退为默认值。
- 未知编码：拒绝，不把未知值当 `PUBLIC`。
- 并发冲突：重读后取最高值再提交。
- 外部服务不可用：fail closed；已发布资产继续按本地最后已知最高密级控制访问。
