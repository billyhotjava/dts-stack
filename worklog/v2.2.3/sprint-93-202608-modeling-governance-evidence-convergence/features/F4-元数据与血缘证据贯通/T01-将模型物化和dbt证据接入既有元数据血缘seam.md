# T01：将模型物化和 dbt 证据接入既有元数据血缘 seam

**优先级**：P0
**状态**：IMPLEMENTATION_COMPLETE / IT-08_PASS / IT-07_AUTOMATED_PASS / FINAL_E2E_PENDING
**依赖**：Sprint-89 F1/F2、Sprint-90 F1、F1/T01、F0/T01 真实 manifest

## 目标

使用既有 dataset/table/column 和 lineage owner，把模型依赖、物化验证和 dbt 字段映射组成可追溯证据链。

## 技术设计（Contract-first）

- **输入契约**：模型 dependsOn、candidate/revision/implementation pins、physical observation、dbt manifest/run artifact、Sprint-89 schema fingerprint。
- **表级输出**：既有 `catalog_dataset_lineage`；模型依赖先 DECLARED，成功物化后同 evidence 进入 VERIFIED；保留 validFrom/validTo。
- **字段级输出**：既有 `catalog_column_lineage`；只消费候选钉定的 MODEL/STG compiled SQL，记录 confidence/source/evidenceRef；多来源歧义时禁止猜测字段边。
- **多输入限定**：shared parser 同时保留 `alias.column` 限定符和 `FROM/JOIN relation → alias` 映射；同名字段只写入限定符命中的上游，未限定且存在多个 owner 时不写推测边。
- **数据流**：publication/materialization → F1 datasetId → Sprint-90 lineage writer/guard；dbt sync → Sprint-89 stable columns → 同一 writer。
- **能力边界**：数据建模负责 ODS→DWD→DWS→ADS 转换与物化；本 Task 只消费建模证据生成治理投影，不读取或加工 ODS 业务数据。
- **错误路径**：源/目标资产或列无法唯一解析、schema fingerprint stale、人工 VERIFIED 冲突时不写边并记录 skip/issue。
- **OM 降级**：OM cache/mapping 不可用只更新 syncStatus/error；DTS dataset/lineage/quality 继续读取。
- **复用点**：CandidatePublicationRepository 既有表级写入、DbtAssetSyncService/CatalogDbtLineageService、Sprint-90 LineageVerificationGuard；已有 SQL 投影解析提取为唯一 shared parser，禁止第二 parser/owner/table。

## UI 交互规格

- 血缘图显示 `DECLARED/VERIFIED/KNOWN_UNVERIFIED/MANUAL` 和来源。
- 字段 Tab 无边时显示“未生成字段血缘”及具体 skip reason/处理入口。
- 元数据页明确技术采集与业务治理；OM 失败显示可重试同步状态。

## 影响范围

dts-platform modeling/dbt/catalog lineage adapters，复用 Sprint-90 API/UI；dts-platform-webapp 仅兼容消费状态，不重写血缘页面。

## 验证（RED→GREEN）

- [x] ODS→DWD 物理来源与 DWD→DWS→ADS 模型依赖的表/字段写入契约通过自动化集成测试；真实全链 E2E 待集中执行。
- [x] 重放 manifest 不重复边；schema 漂移产生 issue。
- [x] 自动采集不能覆盖人工 VERIFIED。
- [x] OM 失败时 DTS 资产缓存保持不变，目录/详情/本地血缘可降级读取。
- [x] 回滚关闭当前表/字段边，旧有效期边仍可查询。
- [x] 多模型输入同名字段的 alias-aware 路由与歧义抑制已有聚焦契约测试；单物理源 `alias.column` 的原有血缘由 PostgreSQL 集成用例验证，真实 DWD→DWS→ADS 多输入字段边仍随最终 E2E 集中复核。

## Definition of Done

- [x] IT-08 已有真实运行证据；IT-07 写入契约自动化通过，最终真实 E2E 待集中执行。
- [x] 缺少唯一来源或明确字段表达式时不伪造字段边。
- [x] 无新 lineage owner/table；原有解析逻辑已收敛为唯一 shared parser。
