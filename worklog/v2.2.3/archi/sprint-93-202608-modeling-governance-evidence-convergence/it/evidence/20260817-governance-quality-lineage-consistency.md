# 2026-08-17 治理质量、血缘与页面口径复验

## 验收范围

- 时间：2026-08-17 08:10～08:19 CST。
- 账号：`xiezm`，从真实登录页进入受保护页面。
- 浏览器：本地 Chrome 150；本轮不替代 Chrome 95 的 IT-13。
- 受控样本：模型 `c427e226-253d-36f0-af86-03ca39f4a0a4`（`biz_ads_budget_kpi_v2`），dataset `49cdcdfe-325c-46e5-a461-d2d8de9c274d`。
- 操作边界：只读页面、GET API 和只读 SQL；未触发质量运行、发布、再次物化、故障注入、回滚或数据修改。

## 结果摘要

| IT | 结果 | 真实断言 |
|---|---|---|
| IT-05 | FAIL（受控样本） | 治理质量运行实际为 0，语义投影为 `QUALITY_EVIDENCE_MISSING`，但发布候选仍返回 `QUALITY_RUN=PASSED`。 |
| IT-06 | FAIL（受控样本） | 发布界面只有合并后的“质量检查：已完成”，未分开展示工程验证和治理数据质量，也没有 rule/version/binding/run/checksum。 |
| IT-07 | PARTIAL | 可查 DWD→DWS→ADS 的 3 条 `VERIFIED` 表级边；缺 ODS 节点，字段级边为 0。 |
| IT-09 | FAIL | 无筛选时同一账号同时看到 308、49、53 三个资产总量；目录第 1、2 页均返回空 `content`；详情内部状态也互相矛盾。 |
| IT-11 | PARTIAL | xiezm 可进入资产、质量、血缘和元数据页面且请求为 200；部门越权负向和审计分类仍未执行。 |

## IT-05/06：工程质量与治理质量仍未分离

1. 资产详情“质量与 SLA”显示：
   - 发布前状态：`待质量校验`；
   - 健康分：`100 / HEALTHY`；
   - 总运行、通过、失败均为 `0`。
2. 质量报告选择同一 dataset 后，正确显示“暂无有效检测结果”“该资产在所选周期内没有可用于评分的质量运行”。
3. 数据库只读对账：`gov_quality_run` 对该 dataset 的记录数为 `0`。
4. 语义投影对同一 AssetKey 返回：
   - `quality_gate_passed=NULL`；
   - `eligibility_decision=CONDITIONAL`；
   - 原因：`QUALITY_EVIDENCE_MISSING`、`ACCESS_EVIDENCE_MISSING`。
5. 发布候选 workspace 却返回 `evidence[type=QUALITY_RUN].state=PASSED`；界面显示“质量检查：已完成”和“5/7 项证据已通过”，没有治理质量证据明细。

结论：当前 `QUALITY_RUN` 仍表示工程侧检查或历史合并结论，不能作为治理数据质量已通过的证据。资产详情把“无运行”计算成 100/HEALTHY 也是错误的成功态。

- 截图：[资产详情 0 次运行却显示 HEALTHY](20260817-asset-quality-zero-runs.png)
- 截图：[质量报告明确无治理质量证据](20260817-quality-report-no-evidence.png)
- 截图：[发布界面仍合并质量结论](20260817-release-quality-conflated.png)

## IT-07：表级链路可查，ODS 与字段链路缺失

`GET /api/catalog/lineage/impact?datasetId=49cdcdfe-325c-46e5-a461-d2d8de9c274d&direction=BOTH&depth=3` 返回 200：

- `nodeCount=4`、`edgeCount=3`、`columnLineageCount=0`；
- 节点：`biz_dwd_budget_v2` → `biz_dws_budget_v2` → `biz_ads_budget_kpi_v2` → `biz_ads_budget_derived_v2`；
- 3 条边均为 `MODEL_DEPENDENCY / VERIFIED`；
- 没有 ODS 节点，`columnLineages=[]`。

血缘图页面能够展示表级 canvas，并明确显示“当前无字段血缘数据”。从带 datasetId 的血缘图切换“字段血缘”后，URL 变成 `/catalog/lineage/columns`，丢失原 datasetId，并自动选择了另一资产 `biz_ads_project_follow_up_kpi_v2`；深链上下文未保持。

- 截图：[资产详情表级血缘](20260817-asset-lineage-dwd-dws-ads.png)
- 截图：[完整血缘图无字段边](20260817-lineage-graph-no-columns.png)
- 截图：[切换字段血缘后丢失原 dataset 上下文](20260817-lineage-field-context-lost.png)

## IT-09：全局统计、目录分页和详情状态不一致

### 无筛选统计

| 位置/契约 | 数字 | 证据 |
|---|---:|---|
| 资产概览左侧“全部资产” | 308 | `GET /api/catalog/domains/tree?withStats=true`，`stats.all.total=308`，且 `freshness=STALE`、`approximate=true`。 |
| 资产概览主统计 | 49 | `GET /api/catalog/assets-v2/overview`，`total=49`、`scanned=49`、`truncated=false`。 |
| 数据资产目录 | 53 | `GET /api/catalog/assets-v2?page=0&size=10`，`total=53`。 |
| 元数据管理 | 215 | 页面显示“共 215 条”；`governance-intake` 请求为 200。 |

数据库对账显示：`catalog_dataset` 全量 367、enabled 216、`catalog_asset_semantic_projection` 308、统计投影求和 308。由此确认这些页面在消费不同事实集合，不是同一可见范围下的可比统计。

### 目录分页失效

- 第 1 页：`content=[] / total=53 / page=0 / size=10 / returned=0`；
- 第 2 页：`content=[] / total=53 / page=1 / size=10 / returned=0`；
- 页面同时显示“共 53 条”和“暂无数据”，无法从目录进入任何详情。

### 详情状态冲突

同一 `biz_ads_budget_kpi_v2` 详情页同时显示：

- 治理状态区域：`待确认` 与 `GOVERNED`；
- 当前治理待办：`资产映射需要人工确认`；
- 概览映射状态：`DTS_NATIVE`；
- 语义投影：`VERIFIED / GOVERNED / PUBLISHED / HEALTHY / ACTIVE`。

数据库不存在该 dataset 的 `catalog_asset_mapping` 行；DTS 原生资产不应因缺少 OpenMetadata 映射而被标记为“待确认”。

- 截图：[概览 308 与主统计 49](20260817-overview-scope-count-mismatch.png)
- 截图：[目录共 53 条但本页空](20260817-catalog-total-empty-page.png)

## 权限、网络与控制台

- xiezm 可直接访问资产详情、资产概览、数据资产目录、质量报告、血缘图和元数据管理，未出现 403。
- 关键 GET 请求均为 200。
- 当前血缘页面 console error=0、warning=0；元数据管理 console error=0。
- 本结果仅证明所级管理员正向读取路径；部门账号越权负向、审计动作分类、OM 故障降级、Chrome 95 仍保持未验收。

## 阻断结论

继续执行故障注入或回滚之前，应先修复以下主链问题，否则后续验收无法得到可信结论：

1. 统一资产概览、目录和域统计的事实集合、可见范围与 `asOf`。
2. 修复 assets-v2 分页 `total>0` 但 `content=[]`。
3. 将发布面板的工程验证与治理数据质量拆分，并以钉定的治理运行证据 fail-closed。
4. 无治理质量运行时不得显示 100/HEALTHY。
5. 补齐 ODS→DWD 表级边和字段级血缘，并在视图切换时保留 datasetId。
6. DTS 原生资产不应被 OpenMetadata 映射待确认状态阻断。

## 修复后复验（2026-08-17 09:56～10:21 CST）

### 交付与自动化验证

- 后端聚焦测试共 113 个用例通过，随后完整 Maven package 通过；新增状态矩阵覆盖治理质量证据的 pass、fail、running、missing、expired 和 checksum mismatch。
- 前端 source-contract、Vitest 和生产构建通过；质量规则深链修正后聚焦 Vitest 30/30 通过。
- Chrome 95 目标的 legacy production Docker 构建通过；实际浏览器验收仍使用 Chrome 150，不能替代真实 Chrome 95。
- 仅重建并重启 `dts-platform` 与 `dts-platform-webapp`；最终镜像分别为 `6ff38d8f...` 和 `c3e4e7b3...`，服务健康、首页返回 200。

### IT-05/06：治理质量证据 fail-closed，发布界面完成分栏

1. 真实样本仍没有已发布规则对应的治理质量运行，系统现在返回 `MISSING` 并阻断治理质量门禁，不再把工程侧 `QUALITY_RUN` 当成治理质量通过。
2. 资产详情“质量与 SLA”显示总运行/通过/失败均为 0、状态为“待质量校验”，不再显示 `100/HEALTHY`。
3. 发布界面分别展示“工程验证：已完成”和“治理数据质量：需处理”；治理卡片显示 asset ID、规则版本、绑定、运行、完成时间和 checksum，缺失项保持 `—`。
4. “配置质量规则”链接为 `#/governance/rules/catalog?datasetId=e38ef06b-cd58-3b26-a153-bd8bf7c774b8`；真实点击进入质量规则页面，无 403、console error=0。
5. 自动化测试证明候选保存时钉定治理证据快照，后续发布与 reconcile 使用钉定证据校验，不根据新运行静默改写旧候选。

- 截图：[发布质量双栏和缺证据门禁](20260817-post-fix-release-quality-evidence.png)
- 截图：[900px 发布弹窗](20260817-post-fix-release-quality-900px.png)

### IT-07：已有表级关系可信，缺失证据显式降级

`GET /api/catalog/lineage/impact?datasetId=49cdcdfe-325c-46e5-a461-d2d8de9c274d&direction=BOTH&depth=3&withJobs=true&withColumns=true` 返回 200：

- `nodeCount=7`、`edgeCount=6`，其中 4 个 dataset、3 个 job；6 条边均为 `MODEL_DEPENDENCY / VERIFIED`；
- `columnLineageCount=0`，没有 ODS 或 source 节点；
- `lineageEvidence.state=PARTIAL`；
- 原因码为 `UPSTREAM_SOURCE_OR_ODS_EVIDENCE_MISSING`、`COLUMN_LINEAGE_EVIDENCE_MISSING`。

页面同步显示“血缘证据不完整”，只呈现已有证据，不补造 ODS 节点或字段边。使用 `direction=UPSTREAM&depth=5` 从图谱切换到字段血缘后，URL 仍保留 datasetId、direction 和 depth。`BOTH/3` 是默认筛选，会被 URL 规范化省略，但目标页仍使用相同语义。

- 截图：[字段血缘缺口和显式证据说明](20260817-post-fix-lineage-evidence.png)
- 截图：[900px 血缘页面](20260817-post-fix-lineage-900px.png)

IT-07 仍为 PARTIAL：系统已做到证据诚实和导航连续，但当前生产样本本身还没有 ODS→DWD 与字段级证据，不能把产品降级能力当成数据链已补齐。

### IT-09：可见范围、分页和 DTS 原生状态已收敛

- xiezm 的资产概览左侧“全部资产”和主统计均为 49，六个数据域统计合计为 49。
- 数据资产目录显示“共 49 条”；第 1 页和第 2 页均返回 10 条真实记录，对应 `/api/catalog/assets-v2?page=0|1&size=10` 均为 200，响应 `total=49`。
- `biz_ads_budget_kpi_v2` 详情为 `GOVERNED / DTS_NATIVE`，业务归属为“研究所业务 / 财务管理域”，不再出现“待确认”或“资产映射需要人工确认”。
- 质量规则深链和资产详情入口均可达，无 403。

### 浏览器、窄屏、网络和剩余边界

- 1366×768 和 900×700 两档视口均完成检查；900px 下 `documentElement.scrollWidth=viewport.width`，发布弹窗边界位于视口内，血缘主区无页面级横向溢出。
- 当前血缘页面 console error=0；session、menu、dataset list 和 lineage impact 请求全部返回 200。
- 未执行 OM 故障注入、部门越权负向、回滚演练和真实 Chrome 95；这些状态保持原台账结论。
