# F5: 治理运营三模块重构

**优先级**: P0
**状态**: IN_PROGRESS

## 目标

治理运营下三个入口（质量管控 `/governance/rules`、质量报告 `/governance/quality`、分级分类 `/security/data-security`）当前界面骨架在（分别 922/411/582 行，均接 platformApi），但功能链路不完整。逐模块审计缺口 → TDD 补齐到可用。

## 约束

- TDD：先契约/单测（RED）再实现（GREEN）；后端补缺口的服务必须带单测。
- 复用既有组件与范式（CompactTable、EmptyState、useGovernanceManageAccess）。
- 不重写整页，缺什么补什么——以"用户能走完一条完整业务动线"为验收口径。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 三模块现状审计与 API 缺口登记（T01 文档） | P0 | IN_PROGRESS（静态完成，运行时验证待重建） | - |
| T02 | 质量管控完善（规则 CRUD→试跑→绑定数据集→启停 动线闭环） | P0 | IN_PROGRESS（执行后查看报告前端闭环完成；真实执行回写待补证） | T01 |
| T03 | 质量报告完善（评分/趋势/规则命中明细，与管控页互跳） | P0 | IN_PROGRESS（报告页深链数据集上下文完成；真实评分/趋势待补证） | T01 |
| T04 | 分级分类完善（密级台账、批量定级、与资产台账联动） | P0 | IN_PROGRESS（资产台账联动 + 批量导入/导出前端闭环完成；真实后端联动待补证） | T01 |

## 进展记录

- 2026-07-04：T02/T03 交叉闭环完成。质量规则执行成功后自动切到 `质量报告` 并携带 `datasetId`；质量报告页支持 `datasetId` 深链并在默认数据湖接口失败时保留上下文。TDD 契约见 `source/dts-platform-webapp/src/pages/governance/QualityRulesReportFlow.source-contract.test.ts`，截图见 `../../assets/it-15-f5-quality-report-deeplink.png`。
- 2026-07-04：T04 第一条闭环完成。资产台账行级新增 `分级分类` 入口，可直达 `/security/data-security?tab=datasetSecurity&datasetId={assetId}`；分级分类页支持 `tab/datasetId` 深链并激活 `数据集安全字段`。TDD 契约见 `source/dts-platform-webapp/src/pages/security/F5DataSecurityLinkage.source-contract.test.ts`，截图见 `../../assets/it-13-f5-data-security-deeplink.png`。
- 2026-07-04：T04 第二条闭环完成。分类映射页签新增 `批量导入` / `导出映射`，CSV 导入复用 `importClassificationMapping`，导出复用 `exportClassificationMapping` 并下载 CSV。截图见 `../../assets/it-14-f5-classification-batch.png`。
- 2026-07-04：运行时 smoke 将本地 `/api/*` 空 500 定位为 3001 Vite dev proxy 默认目标 `localhost:18082` 未暴露；已用宿主转发并重启 3001，curl 现返回后端 401（未登录），说明链路已到 platform。真实数据回写、质量执行和批量定级仍需有效登录会话与业务数据补证。

## 完成标准

- [ ] 审计文档明确每模块"已有/缺失/mock"清单与后端缺口
- [ ] 每模块至少一条端到端业务动线可走通，配 source-contract 测试
- [ ] 后端新增/修改逻辑单测覆盖，全部绿
