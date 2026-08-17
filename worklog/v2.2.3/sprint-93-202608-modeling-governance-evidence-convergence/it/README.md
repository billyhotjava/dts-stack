# Sprint-93 集成验收台账

**当前结论**：E2E_COMPLETE_WITH_EXTERNAL_GAPS。代码、部署、集中 E2E 和回滚演练已完成；IT-01～06、08～10、12 PASS，IT-07、11、13 因真实数据/账号/浏览器条件不足保持 PARTIAL。完整证据见 [`evidence/20260817-final-e2e.md`](evidence/20260817-final-e2e.md)。

| IT | 验收切片 | 关键断言 | 状态 |
|---|---|---|---|
| IT-01 | 首次物化登记资产 | ModelSpec → candidate → dispatch → observation → 单一 AssetKey/datasetId/projection | PASS（受控样本） |
| IT-02 | 发布推进同一资产 | 发布不创建第二 dataset；publication/serving 状态按证据推进 | PASS（受控样本） |
| IT-03 | 存量补齐 | preview/apply/rollback；歧义 fail-closed；回滚不覆盖漂移版本 | PASS（真实 apply/rollback/reapply） |
| IT-04 | 服务投影消费 | outbox → SYNCED；超时/失败重试；旧 version 不覆盖新 ref | PASS（Analytics 故障注入与恢复） |
| IT-05 | QualityEvidence Port | pass/fail/running/missing/expired/mismatch 全部结构化判定 | PASS（自动化状态矩阵 + 真实成功运行） |
| IT-06 | 发布质量 UI | 工程质量与治理质量分栏；候选钉定 rule/version/binding/run/checksum | PASS（受控样本） |
| IT-07 | 模型血缘 | ODS→DWD→DWS→ADS 表级和字段级边可查，来源/验证/有效期正确 | PARTIAL（DWD→DWS→ADS 表级证据通过；真实 ODS/字段输入缺失） |
| IT-08 | OM 故障降级 | OM 不可用时资产仍可查，技术同步显示失败且可重试 | PASS（真实停止/恢复 OpenMetadata） |
| IT-09 | 治理页面一致性 | 概览、目录、详情数字/状态/业务归属数据域一致，深链可达 | PASS（当前可见范围 49 条，分页、详情和质量状态复验通过） |
| IT-10 | 二次物化 | 模型数和资产数不增加；candidate/attempt/observation 增加；servingRef 推进 | PASS（受控样本） |
| IT-11 | 权限与审计 | xiezm 所级管理员正向通过；部门越权失败；审计动作有分类 | PARTIAL（xiezm 正向与审计分类通过；无部门账号执行负向） |
| IT-12 | 发布/回滚/可运维 | 镜像、迁移、feature flag、告警、runbook、回滚演练完整 | PASS（状态读开关关闭/恢复，历史证据不丢失） |
| IT-13 | Chrome 95 集中验收 | 真实登录；空/加载/错误/成功；console/network 无未解释异常 | PARTIAL（legacy build、Chrome 150 双视口和三态通过；现场缺 Chrome 95） |

## 集中执行顺序

1. 固定 commit/image、数据库快照和样本 ID。
2. 运行后端契约、迁移和故障注入 IT。
3. 部署受影响服务并验证健康、outbox 和 projection。
4. 使用 xiezm 在 Chrome 完成 IT-01～11；最后执行二次物化。
5. 切换 Chrome 95 完成 IT-13。
6. 执行 rollback 演练，再恢复并复核数据一致性。

任何失败只做针对性修复和重跑，不重新执行无关全套测试。

## 2026-08-17 部署后真实 Chrome smoke（修复前基线）

- 详细过程与结果：[`evidence/20260817-post-deploy-real-chrome-smoke.md`](evidence/20260817-post-deploy-real-chrome-smoke.md)。
- xiezm 的分析卡片、新建问题、分析看板创建与删除不再返回 403；创建 200、删除 204。
- 当时阻断不是权限，而是模型到 Analytics 语义投影为空；同时，同一 public 物理关系仍存在物理观察与语义投影两个资产身份。该结论已由下方主链复验更新。
- 本轮浏览器为 Chrome 150；该修复前快照中 IT-13 仍为 PENDING，最终集中复验已更新为 PARTIAL，见本页顶部台账。

## 2026-08-17 建模、资产与 BI 主链复验

- 详细过程与结果：[`evidence/20260817-modeling-asset-bi-chain-rerun.md`](evidence/20260817-modeling-asset-bi-chain-rerun.md)。
- `biz_ads_budget_kpi_v2` 已恢复显示“已物化”，物化 API 返回 `PUBLISHED / BUILT / VERIFIED`。
- 同一 public 关系的目录搜索收敛为 1 条规范资产，详情为 `ACTIVE / GOVERNED`，contract 为 `consumable=true`。
- BI 看板继续读取 `2026-08-12 / 1,834.38`；模型、资产、看板页面 console error=0。
- 当前证据为受控样本和 Chrome 150；全量统计、故障注入、权限负向、回滚演练和 Chrome 95 不在本次通过范围。

## 2026-08-17 治理质量、血缘与页面口径复验

- 详细过程与结果：[`evidence/20260817-governance-quality-lineage-consistency.md`](evidence/20260817-governance-quality-lineage-consistency.md)。
- xiezm 的资产、质量、血缘和元数据只读入口均可达且无 403；所级管理员正向读取继续通过。
- 质量主链未闭合：同一资产没有治理质量运行，质量报告显示“暂无有效检测结果”，但资产详情显示 `100/HEALTHY`，发布候选仍把 `QUALITY_RUN` 判为 `PASSED`。
- 血缘仅完成 DWD→DWS→ADS 的 3 条表级 `VERIFIED` 边；ODS 和字段级边缺失，切换字段血缘还会丢失 datasetId。
- 治理页面无筛选口径分别为 308、49、53；资产目录第 1、2 页均为 `total=53` 但 `content=[]`，IT-09 从 PARTIAL 更新为 FAIL。
- 本轮仍为 Chrome 150，只读测试；OM 故障注入、部门越权负向、回滚演练和 Chrome 95 未执行。

## 2026-08-17 修复后集中复验

- 修复后证据追加在 [`evidence/20260817-governance-quality-lineage-consistency.md`](evidence/20260817-governance-quality-lineage-consistency.md) 的“修复后复验”章节；原失败证据继续保留为基线。
- 资产概览、目录和域统计统一到当前账号可见资产集合，总量均为 49；目录第 1、2 页各返回 10 条真实记录，不再出现 `total>0/content=[]`。
- 无治理质量运行时显示“待质量校验”，不再伪造 `100/HEALTHY`；候选发布界面已拆分“工程验证”和“治理数据质量”，缺证据时明确阻断并展示 dataset、规则版本、绑定、运行和 checksum。
- “配置质量规则”深链保留 datasetId，真实点击进入质量规则目录且无 403。
- DTS 原生资产详情显示 `GOVERNED / DTS_NATIVE`，不再被 OpenMetadata 映射待确认状态阻断。
- 血缘 API 返回 7 个节点、6 条 `VERIFIED` 关系，并以 `PARTIAL + UPSTREAM_SOURCE_OR_ODS_EVIDENCE_MISSING + COLUMN_LINEAGE_EVIDENCE_MISSING` 明确暴露剩余数据证据缺口；不再虚构 ODS 或字段边。
- 非默认筛选 `UPSTREAM/depth=5` 在“血缘图谱”与“字段血缘”间切换后完整保留；`BOTH/depth=3` 作为默认值会从规范化 URL 省略，但语义不变。
- 900px 视口下发布弹窗和血缘页无页面级横向溢出；Chrome 控制台 error=0，当前关键 API 均为 200。真实浏览器仍为 Chrome 150，因此 IT-13 只更新为 PARTIAL。
