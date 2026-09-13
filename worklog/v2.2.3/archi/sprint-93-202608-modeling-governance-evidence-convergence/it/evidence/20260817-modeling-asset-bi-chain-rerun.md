# 2026-08-17 建模、资产与 BI 链路复验

## 结论

使用真实账号 `xiezm` 对 `biz_ads_budget_kpi_v2` 样本复验后，模型物化、资产登记、治理投影和 BI 指标消费主链已贯通：模型工作台显示已物化，目录中只有一个规范资产，资产生命周期与治理状态一致，分析看板仍能读取指标计算值。

本次只证明该受控样本及其两次物化记录。存量批量补齐、OpenMetadata 故障降级、质量证据桥接、部门角色越权、发布回滚演练和 Chrome 95 仍按独立 IT 执行，不能据此宣告 Sprint-93 全部完成。

## 固定环境与样本

- 操作者：`xiezm`，所级数据管理员。
- 浏览器：Chrome 150.0.0.0；本次不是 Chrome 95 证据。
- 最终 Git：`f7446501d fix(modeling): preserve durable materialization status`，已推送 `origin/v2.2.3`。
- 后端镜像：`dts-platform:1.0.0` = `sha256:7def02c2bc35709a164a813e8dd114f39c9d209bbd9a5426b2cae8e98cbcf3a7`。
- 回滚镜像：`dts-platform:rollback-20260817-materialization-status-pre`。
- 建模计划：`b1d7bcdc-887e-495e-8b93-8ae0d0ad5870`。
- 模型：`c427e226-253d-36f0-af86-03ca39f4a0a4` / `biz_ads_budget_kpi_v2`。
- 当前发布候选：`6f63baf3-46e1-46fe-ac3b-34d950e484df`。
- 已取消且未构建候选：`453f50a0-e3aa-4b37-b609-52d65b9591b7`。
- 规范资产：`49cdcdfe-325c-46e5-a461-d2d8de9c274d`。
- 规范 AssetKey：`source:a0000000-0000-0000-0000-000000000001/schema:public/table:biz_ads_budget_kpi_v2`。

本链路对应提交依次为：`424b8abcf`（治理建模到 BI）、`ba2de0e88`（已发布执行绑定）、`90efc161a`（退役被删除来源的重复资产）、`df1435b67`（目录生命周期与治理投影）和 `f7446501d`（耐久物化状态读取）。

## 本轮修复

模型物化状态读模型原先只按候选创建时间倒序。较新的 `CANCELLED` 候选没有 implementation、dispatch、pipeline 或 relation observation，却覆盖了较早的已发布证据，导致工作台把真实已物化模型显示为“待重新物化”。

修复后，`CANCELLED`、`REJECTED` 候选排在可服务候选之后，仅在不存在其他候选时作为兜底返回。`BUILDING`、`BUILD_FAILED`、`PUBLISHED`、`STALE` 和 `ROLLED_BACK` 等业务状态仍按时间排序，不影响进行中、失败、过期和回滚状态展示。

## 自动化与运行态证据

| 检查 | 结果 |
|---|---|
| `ModelMaterializationBatchContractTest` | 4/4 通过；新增“取消候选不得遮挡耐久物化证据”契约。 |
| `./mvnw -q -DskipTests package` | 通过。 |
| GitNexus staged detect | LOW，3 个文件，无受影响执行流。 |
| 容器 | 仅重建 `dts-platform`，最终 `running / healthy`。 |
| 真实库候选排序 | `PUBLISHED v8`（有 dispatch）→ `STALE v8`（有 dispatch）→ `CANCELLED v2`（无 dispatch）。 |
| 二次物化证据 | 2 个有 dispatch 的候选、2 个 dispatch group、2 个 BUILT run、2 个 verified/existing observation。 |
| 规范资产身份 | 当前启用记录 1 条；datasetId 为 `49cdcdfe-...`。 |
| 语义轴 | `ACTIVE / GOVERNED / PUBLISHED / HEALTHY`。 |

资产的 `eligibility_decision` 仍为 `CONDITIONAL`，原因是 `QUALITY_EVIDENCE_MISSING` 和 `ACCESS_EVIDENCE_MISSING`。这是后续质量与访问证据的待办，不再是资产缺少数据域、责任人或密级；目录 contract 已返回 `consumable=true`、`missingGovernanceFields=[]`。

## 真实 Chrome 断言

| 操作 | 结果 |
|---|---|
| 刷新模型工作台并定位 `biz_ads_budget_kpi_v2` | 显示 `PUBLISHED / r1 / 已物化`，提供“物化历史 / 再次物化”。 |
| 读取物化状态 API | 200；候选 `6f63baf3-...`，实现 r2，`BUILT / VERIFIED`，目标 `biadmin.public.biz_ads_budget_kpi_v2`。 |
| 资产目录按模型名搜索 | 共 1 条；不再出现同一 public 关系的重复资产。 |
| 打开资产详情 | 生命周期 `ACTIVE`，治理状态 `GOVERNED`，业务归属数据域“研究所业务 / 财务管理域”。 |
| 读取资产 contract | 200；`consumable=true`，缺失治理字段为空。 |
| 打开 `E2E_PJM_20260817_预算执行看板` | 卡片 `E2E_PJM_20260817_预算剩余可用` 返回 `2026-08-12 / 1,834.38`。 |
| 浏览器收尾 | 上述模型、资产和看板页面均为 console error 0、warning 0。 |

## IT 状态更新

- IT-01：PASS（受控样本）。物化证据只落到一个规范资产。
- IT-02：PASS（受控样本）。发布推进同一资产，生命周期与语义轴一致。
- IT-04：PARTIAL。真实 BI 消费已通过；outbox 故障重试与旧版本覆盖保护仍待故障注入。
- IT-09：PARTIAL。受控样本目录/详情一致；全局概览与目录统计尚未重新全量对账。
- IT-10：PASS（受控样本）。两次物化产生独立执行证据，逻辑模型和规范资产没有重复创建。
- IT-12：PARTIAL。新镜像、健康检查和回滚镜像齐备；真实回滚演练未执行。
- IT-13：PENDING。必须在 Chrome 95 单独执行。

## 截图

- [模型再次物化成功](20260817-modeling-online-rerun.png)
- [再次物化后的看板结果](20260817-operational-rerun-dashboard.png)
- [规范资产为 ACTIVE / GOVERNED](20260817-asset-active-governed.png)
- [目录搜索收敛为单一资产](20260817-asset-search-converged.png)
- [BI 看板读取语义计算结果](20260817-dashboard-semantic-result.png)
- [取消候选不再遮挡已物化状态](20260817-materialization-status-restored.png)
