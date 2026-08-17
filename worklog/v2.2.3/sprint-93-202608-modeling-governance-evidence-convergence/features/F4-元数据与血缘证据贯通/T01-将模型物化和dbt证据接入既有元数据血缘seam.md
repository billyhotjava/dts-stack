# T01：将模型物化和 dbt 证据接入既有元数据血缘 seam

**优先级**：P0
**状态**：IMPLEMENTATION_COMPLETE / IT-08_PASS / IT-07_PARTIAL_DATA_EVIDENCE_GAP
**依赖**：Sprint-89 F1/F2、Sprint-90 F1、F1/T01、F0/T01 真实 manifest

## 目标

使用既有 dataset/table/column 和 lineage owner，把模型依赖、物化验证和 dbt 字段映射组成可追溯证据链。

## 技术设计（Contract-first）

- **输入契约**：模型 dependsOn、candidate/revision/implementation pins、physical observation、dbt manifest/run artifact、Sprint-89 schema fingerprint。
- **表级输出**：既有 `catalog_dataset_lineage`；模型依赖先 DECLARED，成功物化后同 evidence 进入 VERIFIED；保留 validFrom/validTo。
- **字段级输出**：既有 `catalog_column_lineage`；只接受 manifest 明确映射，记录 confidence/source/evidenceRef；解析跳过必须返回 reasonCodes。
- **数据流**：publication/materialization → F1 datasetId → Sprint-90 lineage writer/guard；dbt sync → Sprint-89 stable columns → 同一 writer。
- **错误路径**：源/目标资产或列无法唯一解析、schema fingerprint stale、人工 VERIFIED 冲突时不写边并记录 skip/issue。
- **OM 降级**：OM cache/mapping 不可用只更新 syncStatus/error；DTS dataset/lineage/quality 继续读取。
- **复用点**：CandidatePublicationRepository 既有表级写入、DbtAssetSyncService/CatalogDbtLineageService、Sprint-90 LineageVerificationGuard；禁止新 parser/table。

## UI 交互规格

- 血缘图显示 `DECLARED/VERIFIED/KNOWN_UNVERIFIED/MANUAL` 和来源。
- 字段 Tab 无边时显示“未生成字段血缘”及具体 skip reason/处理入口。
- 元数据页明确技术采集与业务治理；OM 失败显示可重试同步状态。

## 影响范围

dts-platform modeling/dbt/catalog lineage adapters，复用 Sprint-90 API/UI；dts-platform-webapp 仅兼容消费状态，不重写血缘页面。

## 验证（RED→GREEN）

- [ ] ODS→DWD→DWS→ADS 表级/字段级真实边一致。
- [x] 重放 manifest 不重复边；schema 漂移产生 issue。
- [x] 自动采集不能覆盖人工 VERIFIED。
- [x] OM 失败时 DTS 资产缓存保持不变，目录/详情/本地血缘可降级读取。
- [ ] 时间旅行能查回旧有效期边。

## Definition of Done

- [x] IT-07/08 有真实运行证据；IT-07 结果为 PARTIAL。
- [x] 字段级 0 已以明确数据证据缺口登记，未伪称完成。
- [x] 无新 lineage owner/parser/table。
