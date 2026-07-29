# 财务 Demo 验收清单

## 1. 证据分级

```text
文档和 CSV 已准备
≠ UI 对象已保存
≠ revision 已确认
≠ 编译通过
≠ 质量通过
≠ 物理表已生成
≠ 平台调度成功
≠ 权限和消费已验收
```

每个结论附实际 ID、revision、run ID、时间、操作人和证据路径。

## 2. 对象对账

| 对象 | 期望 | 完成 | 实际 ID/说明 |
|---|---:|---|---|
| 财务 Demo 数据源 | 1 | [ ] | |
| 源表 | 3 | [ ] | |
| 入湖任务 | 1 或 3 | [ ] | |
| Demo ODS | 3 | [ ] | |
| 业务域 | 1 | [ ] | |
| 业务过程 | 1 | [ ] | |
| 数据集市 | 1 CURRENT | [ ] | |
| 建设计划 | 1 | [ ] | |
| 已确认来源 | 3 CURRENT | [ ] | |
| 业务术语 | 8 | [ ] | |
| 数据元 | 9 | [ ] | |
| 公共码表 | 3 | [ ] | |
| 复用单位 | 2 | [ ] | |
| 业务维度定义 | 3 CURRENT | [ ] | |
| DIMENSION ModelSpec | 3 | [ ] | |
| FACT ModelSpec | 1 | [ ] | |
| SUMMARY ModelSpec | 1 | [ ] | |
| APPLICATION ModelSpec | 1 | [ ] | |
| 质量规则 | 12 | [ ] | |
| 指标 | 8 PUBLISHED | [ ] | |
| BI/API/数据产品 | 各 1 | [ ] | |

实际信息同步到 `assets/demo-object-register.csv`。

## 3. G1 数据源与入湖

- [ ] 源端账号只能访问 `it_fin_demo_src`。
- [ ] 连接测试成功。
- [ ] Schema 探测只纳入 3 张财务 Demo 表。
- [ ] 字段数为 7/7/12，类型识别正确。
- [ ] 基线入湖产生真实 run ID。
- [ ] ODS 行数为 3/4/8。
- [ ] ODS 保留平台导入审计字段。
- [ ] 客户 ODS 定义、行数和更新时间未变化。
- [ ] 入湖失败可从实例页定位日志和恢复动作。

证据：`evidence/01-source-ingestion.md`。

## 4. G2 元数据、业务分类与规划

- [ ] 3 张 ODS 有正式目录资产 ID。
- [ ] 表字段元数据可查看。
- [ ] owner 和责任部门来自实际目录。
- [ ] 公开密级和业务标签分别维护。
- [ ] 业务域、过程和集市均使用稳定 ASCII 编码。
- [ ] 数据集市为 CURRENT。
- [ ] 建设计划只纳入财务 Demo 范围。
- [ ] 3 个 ODS 来源结论为“确认纳入”。
- [ ] 来源 revision/freshness 为 CURRENT。
- [ ] 未创建 ODS/STG 类型的额外 ModelSpec。

证据：`evidence/02-planning-metadata.md`。

## 5. G3 标准与主数据

- [ ] 8 个术语有编码、定义、owner 和版本。
- [ ] 9 个数据元有稳定 ID/version、类型和约束。
- [ ] 复用 CNY、PERCENT，或记录不能复用的原因。
- [ ] 码表 `standard_code` 全部为 ASCII。
- [ ] 人员/人员费/PERSONNEL 等别名收敛到同一标准码。
- [ ] 硬管控/HARD 和软管控/SOFT 收敛正确。
- [ ] 未识别码值产生 UNKNOWN 并触发阻断，不静默归为其他。
- [ ] 成本中心和科目编码非空、唯一。
- [ ] 成本中心更名不产生新编码或重复行。
- [ ] 密级词汇未进入业务标签或码表。
- [ ] 同编码客户内容未被 Demo 升级覆盖。

证据：`evidence/03-standards-master-data.md`。

## 6. G4 维度、模型和版本关系

- [ ] 3 个维度定义为 CURRENT。
- [ ] 日期维度使用受控 DATE_DIMENSION 或记录实际复用维度。
- [ ] 成本中心和预算科目为 TYPE1。
- [ ] 周期历史只保存在预算事实。
- [ ] FACT 粒度是预算明细＋快照日期。
- [ ] FACT 粒度键唯一命中 `budget_snapshot_id`。
- [ ] `snapshot_date` 是 TIME 字段。
- [ ] FACT 事实形态/时间语义为 PERIODIC_SNAPSHOT/SNAPSHOT_DATE。
- [ ] SUMMARY 只引用锁定 revision 的事实和维度。
- [ ] APPLICATION 填写消费场景并锁定汇总 revision。
- [ ] 6 个 dbt 节点绑定相同业务 ModelSpec 的精确 revision。
- [ ] 没有第二套模型台账。
- [ ] KEY/MEASURE 的数据元、单位和密级绑定完整。

证据：`evidence/04-model-design.md`。

## 7. G5 基线编译、质量和物化

- [ ] dbt parse/compile 成功。
- [ ] 基线 dbt 测试全部通过。
- [ ] 质量模块执行结果与 dbt 规则一致。
- [ ] 执行错误未显示为质量通过。
- [ ] DWD 行数为 730/3/4/8。
- [ ] DWS 和 ADS 各 4 行。
- [ ] CC-RD、CC-QA 抽样金额和 `BH-*` 与设计一致。
- [ ] 物化成功有平台 run ID、候选 revision、物理表和时间证据。
- [ ] 若命中 profile lease 契约缺口，明确记录平台物化未通过，未用本地结果替代。
- [ ] 失败候选和日志可追溯，没有伪造成功状态。

证据：`evidence/05-build-materialization.md`。

## 8. G6 脏数据阻断

- [ ] 已运行 `02-source-dirty-cases.sql`。
- [ ] 入湖后 ODS 行数为 3/4/9。
- [ ] 科目类别/管控类型规则失败。
- [ ] 成本中心引用规则失败。
- [ ] 预算正数规则失败。
- [ ] 非负金额规则失败。
- [ ] 应付边界规则失败。
- [ ] 预测边界规则失败。
- [ ] 财年规则失败。
- [ ] 下游 DWS/ADS 的新 revision 被阻断或跳过。
- [ ] 如保留 last-good 表，页面明确显示旧快照时间和质量状态。
- [ ] BI/API/数据产品没有把失败批次标为可用。
- [ ] 失败 run ID、失败行和错误分类保留。

证据：`evidence/06-quality-gates.md`。

## 9. G7 修复和增量恢复

- [ ] 已运行 `03-source-remediation-and-increment.sql`。
- [ ] 源/ODS 行数为 3/4/12。
- [ ] 质量规则恢复通过。
- [ ] DWD 事实 12 行，DWS/ADS 各 6 行。
- [ ] `CC-RD` TYPE1 名称更新成功。
- [ ] `BA-SERVICE` 新别名映射为 BAC-SERVICE/BCT-SOFT。
- [ ] 2026-08-04 CC-RD 为 BH-GREEN。
- [ ] 2026-08-04 CC-QA 为 BH-AMBER。
- [ ] 失败→修复运行链和审计链连续。

证据：`evidence/06-quality-gates.md`。

## 10. G8 指标口径

- [ ] 6 个原子指标先发布。
- [ ] 2 个派生指标只依赖已发布治理指标。
- [ ] 所有指标锁定 DWS ModelSpec ID/revision/字段。
- [ ] CNY/PERCENT 单位版本正确。
- [ ] 时间字段为 `snapshot_date`。
- [ ] 应付金额未重复加到实际发生额。
- [ ] 执行率 = 实际/预算。
- [ ] 占用率 = (实际+承诺)/预算。
- [ ] 分母为 0 时没有除零错误。
- [ ] 指标 owner、责任部门和版本可审计。

证据：`evidence/07-metrics.md`。

## 11. G9 资产、血缘、权限和消费

- [ ] ADS 是 BI/API/数据产品唯一默认数据入口。
- [ ] ADS 有 owner、责任部门、公开密级和业务标签。
- [ ] `GOVERNED` 只在门禁证据完整后添加。
- [ ] 表级血缘覆盖源→ODS→DWD→DWS→ADS→消费。
- [ ] 关键金额和比率有字段级血缘。
- [ ] 管理员可 read/write/export。
- [ ] 查看者只能 read/export。
- [ ] 无授权用户访问资产、BI、API、产品被拒绝。
- [ ] API 过滤参数不允许任意 SQL。
- [ ] 权限拒绝有 401/403 或等价审计证据。
- [ ] 数据产品包含 8 个指标、质量状态和 owner。

证据：`evidence/08-consumption-permission-lineage.md`。

## 12. G10 运行和审计

- [ ] 至少有基线、失败、修复 3 组 run ID。
- [ ] 每次运行记录操作者、开始/结束时间和状态。
- [ ] 入湖、标准发布、模型发布、质量执行、授权和消费动作可审计。
- [ ] 错误日志已脱敏，不含密码、Token 或连接串。
- [ ] 当前权限仅按 read/write/export 表述。
- [ ] 自动验收包不可用时，手工证据 9 个文件齐全。
- [ ] 未把文档或本地容器验证写成当前 DTS 租户 UI 验收。

证据：`evidence/09-ops-audit.md`。

## 13. 最终结论

只有 G1～G10 全部通过，才能写：

```text
财务 Demo 已在指定 DTS 租户完成端到端验收。
```

如果平台物化仍受已知缺口阻断，应写：

```text
财务 Demo 的设计、源数据、dbt 制品和手工配置材料已就绪；
平台最终物化及其下游运行验收尚未通过。
```
