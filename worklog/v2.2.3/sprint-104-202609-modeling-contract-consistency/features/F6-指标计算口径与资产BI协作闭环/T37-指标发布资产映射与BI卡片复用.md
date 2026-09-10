# T37：指标发布资产映射与BI卡片复用

**优先级**：P1  
**状态**：DRAFT  
**依赖**：T32、T33、T34、T35、T36

## 目标

发布指标可被 BI 卡片按固定版本引用，并追溯资产和看板。

## 技术设计与契约

- **契约引用**：C48–C54；K65、K66，见 [统一契约与账本](../../assests/F6-metric-asset-bi-contract.md)。
- **通用契约**：数据流与复用、错误路径、存储与兼容、UI 落点见 [任务通用契约](README.md#任务通用契约)；本文件只写差异。
- **本任务输入/输出**：入参 K65 投影记录与重试命令，出参投影状态与错误；BI 侧按 K66 绑定 `{indicatorId,indicatorVersion}`。GET 零副作用。
- **方案**：在 C48 的 serving 投影链上扩展，按 T32 冻结结论依次实施三处结构工作——派生/复合指标的投影覆盖（C50 当前零通路，不是「补映射」量级）、版本随投影传递（C51，扩展 wire 契约或平台侧解析）、发布到投影的触发与幂等键（C52）。平台定义为公共口径 owner，BI 为版本化消费投影；不接 dts-metrics 注册链（C54）。注册幂等与失败独立重试沿用 C53 的状态与 CAS，禁止重复资产/卡片；已有卡片不自动升级口径。资产与卡片双向追溯，继承权限/密级，缓存按权限上下文隔离；发布、注册与重试记审计事件。
- **本任务差异**：投影状态落 `modeling_catalog_model_serving_projection`（C53），不落 `gov_indicator_run`；须先按 T32 冻结结论处理派生/复合零通路（C50）、版本位缺失（C51）与触发源（C52）三处结构缺口。

## UI 交互与影响范围

IndicatorService、CatalogModelSemanticSyncCommandService、CatalogModelSemanticPayloadFactory、CatalogModelSemanticIndicatorReadAdapter、CatalogModelSemanticContract、CatalogModelServingProjectionRepository、AnalyticsMetric、SemanticQueryService、CardResource、DashboardResource、资产详情。本任务具名控制项的状态反馈要求见 [任务通用契约](README.md#任务通用契约)。

## 验证与验收

同一指标两张卡片同条件结果一致；不同时间/地区生效；派生指标可被卡片引用（C50 修复后）；发布 v2 后旧卡片保留 v1，显式升级展示影响；注册失败显示待处理且模型仍完成；重复重试无重复；发布/注册/重试审计事件落库；权限撤销后无结果/缓存泄漏。

映射 **IT-41**，见 [F6 验收清单](../../it/F6-指标资产BI验收.md)。实现任务先用对应缺陷场景建立失败证据，再进行一次成组验证，不重复全仓检查。编译性测试只在 deploy 经 Git 同步后执行；验收样本仅用于隔离测试，不预置为客户页面演示数据。

## Definition of Ready

- [ ] T32 已冻结本任务 API/DTO/表列/迁移、错误和并发约束。
- [ ] 依赖产物可用，真实数据、登录与所需角色可用。
- [ ] 本任务验收场景、预算和 UI 落点明确且可执行。

## Definition of Done

- [ ] 契约实现及对应静态/专项验证通过；如有迁移完成正式验证。
- [ ] 具名 UI 操作与异常路径有真实证据，涉及页面另验 Chrome95。
- [ ] IT-41 留存请求/响应、版本、结果、提交与截图，未执行项明确记录。
- [ ] 不以规划、按钮存在或历史通过结果替代当前完整切片验收。
