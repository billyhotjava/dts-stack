# F3：维度与四类表直接建模

**优先级**：P0
**状态**：IN_PROGRESS
**依赖**：F1、F2-T02
**目标**：让用户不经过业务对象即可登记业务维度并创建四类逻辑模型；`DimensionDefinition`、ModelSpec、ModelImplementation 和物理资产各自成为所属阶段的唯一事实源。

## Task

| Task | 优先级 | 状态 | 依赖 | 输出 |
|---|---|---|---|---|
| [T01-建立无业务对象的ModelSpec契约](T01-建立无业务对象的ModelSpec契约.md) | P0 | DONE | F1-T02/T03 | ModelSpec v2/API/持久化契约 |
| [T02-设计维度目录与维度表](T02-设计维度目录与维度表.md) | P0 | IN_PROGRESS | T01 | 业务维度目录与逻辑维度表边界纠偏 |
| [T03-设计明细表与粒度时间语义](T03-设计明细表与粒度时间语义.md) | P0 | DONE | T01、F2-T03 | FACT 编辑流 |
| [T04-设计汇总表与应用表](T04-设计汇总表与应用表.md) | P0 | DONE | T01/T03 | SUMMARY/APPLICATION 编辑流 |
| [T05-统一字段来源标准关系与门禁](T05-统一字段来源标准关系与门禁.md) | P0 | DONE | T02/T03/T04 | 字段设计与分阶段 gate |
| [T06-拆分上游输入与目标模型并后移来源门禁](T06-拆分上游输入与目标模型并后移来源门禁.md) | P0 | IN_PROGRESS | F2-T03、T01/T03/T05、F6-T06 | FACT 草稿后移输入门禁与双来源路径 |
| [T07-建立模型类型与分层依赖矩阵并收敛ODS入口](T07-建立模型类型与分层依赖矩阵并收敛ODS入口.md) | P0 | IN_PROGRESS | F2-T02/T03、T01/T06、F6-T02 | 四类模型目标层、上游层和 ODS/STG 技术入口 |
| [T08-拆分业务维度与逻辑维度表并建立四层契约](T08-拆分业务维度与逻辑维度表并建立四层契约.md) | P0 | IN_PROGRESS | T01/T02/T07 | DimensionDefinition、稳定引用与兼容迁移 |
| [T09-实现统一模型输入与API-Landing物化闭环](T09-实现统一模型输入与API-Landing物化闭环.md) | P0 | IN_PROGRESS | T06/T07/T08、F6-T06 | 三种实现输入、Landing 资产、物化和血缘 |
| [T10-重构轻量新建与三阶段模型详情页](T10-重构轻量新建与三阶段模型详情页.md) | P0 | IN_PROGRESS | T08/T09、F4-T02/T03/T04 | 轻量新建、三阶段详情和安全返回 |

## 完成标准

- [x] ModelSpec 新写契约不存在 objectId 硬依赖。
- [ ] 维度目录的数据源是独立 `DimensionDefinition`，DIMENSION ModelSpec 只通过稳定 ID 引用。
- [x] 四类表各有独立必填规则、默认层级和产物说明。
- [x] 标准、来源、维度关系和指标只保存稳定引用。
- [x] 保存草稿、进入实现、提交发布使用不同且可解释的门禁。
- [ ] FACT 草稿不强制选择具体物理表；进入实现前满足有效物理来源或锁定 revision 的上游 ModelSpec。
- [ ] “目标数仓分层”和“上游来源分层”在契约、界面和人工验收中清楚分离。
- [ ] ODS_RAW/ODS_STANDARDIZED/STG 只从数据接入和技术实现入口建设，不作为四类 ModelSpec 的目标层。
- [ ] DIMENSION/FACT→DWD、SUMMARY→DWS、APPLICATION→ADS 的目标层和允许上游矩阵由前后端共同强制。
- [ ] ModelImplementation 只接受物理资产、上游模型或受控生成器三种主输入。
- [ ] API 通过现有采集任务生成并登记 Landing 物理资产后进入统一建模链。
- [ ] 模型 UI 使用轻量新建和逻辑设计/数据实现/物理资产三阶段详情。

## 关闭证据（2026-07-19）

- Java 四类型契约、快照、应用服务、资源与 stage-gate 聚焦回归：55/55 PASS；PostgreSQL Testcontainers 迁移/持久化集成测试 PASS。
- TypeScript 契约与 workbench：33/33 PASS；Chrome 95 legacy production build PASS。
- 真实 `opadmin` 登录、计划 owner 权限、ModelSpec POST、强 ETag CAS PUT、PostgreSQL revision 快照和服务端三阶段门禁链路：1/1 PASS，零非预期 HTTP/浏览器错误。
- Mock Chrome95 证据继续只作为路由、布局和响应式回归；真实联动证据单独记录于 `it/evidence/chrome95/README.md`。

## 复核收口（2026-07-20）

- 补齐 SUMMARY/APPLICATION 直接与间接循环依赖阻断、revision-pinned dependency graph API，以及页面 CURRENT/STALE/UNKNOWN 三态展示。
- 发布门禁不再把仅有密级的字段误判为已落标；度量单位由治理 owner 校验 ACTIVE 与精确版本，无法证明当前性的引用按 UNKNOWN/STALE 失败关闭。
- 字段标准页可从专业模块选择带真实版本的标准并通过强 ETag CAS 保存；未提供版本契约的 owner 选项不可写入，不再硬编码版本 1。
- FACT 三种形态及 4 个合法时间组合、非法 TIME 字段修复路由均有服务端测试。
- 后端相关测试目录共 86 项：统一批次 85 项通过、唯一 fixture 漂移修正后受影响类 7/7 通过；PostgreSQL Testcontainers 集成通过。前端契约 43/43，legacy production build 通过。
- 当前 production bundle 的 Chrome95 受影响场景 2/2 通过；本次新增接口尚未发布到运行容器，真实 auth/API/PostgreSQL 的部署后复验作为 F6 发布门禁，不把 mock 结果声明为 live E2E。

## 人工测试重开（2026-07-22）

首次创建 FACT 时发现原契约把具体物理表作为草稿保存前置条件，并将上游输入分层与目标模型分层混在同一操作路径。新增 T06：草稿可先保存粒度设计；进入实现前再满足有效 `sourceRefs OR dependsOn`，两类同时提供时都要有效。原 T01-T05 的历史关闭证据继续保留，但 F3 在 T06 完成自动化、最终构建和真实联动 E2E 前恢复为 `IN_PROGRESS`。

## 分层依赖复核（2026-07-23）

继续人工测试发现四类模型仍可选择 ODS/STG 作为目标层，导致“接入技术表”和“业务模型”再次混用。新增 T07 冻结 `ODS_RAW/ODS_STANDARDIZED/STG` 为接入/技术层，四类 ModelSpec 只允许 `DIMENSION/FACT→DWD`、`SUMMARY→DWS`、`APPLICATION→ADS`，并明确每类 DRAFT/IMPLEMENTATION/RELEASE 依赖、允许上游和历史 ODS/STG 目标只读边界；专属分类/UI 尚待实现。F3 保持 `IN_PROGRESS`，真实前后端与数据库矩阵闭环前不得宣称完成。

## 四层对象纠偏（2026-07-24）

继续人工测试和成熟产品参考复核确认，T02 将概念维度、逻辑维度表和实现来源混入同一 DIMENSION ModelSpec。T02 因此重新打开，新增 T08-T10，以最小路径拆分 `DimensionDefinition → ModelSpecRevision → ModelImplementation → PhysicalAssetRevision`。API 不直接进入模型运行时，而是复用现有采集任务生成并登记 Landing 物理资产。原 T01-T07 的有效实现和证据继续保留，但不能替代本轮迁移、UI 和真实物化验收。

## T08-T10 实施冻结（2026-07-24）

T08-T10 已进入代码实施后的统一收口阶段：四层持久化与迁移、三种实现输入、implementation 强版本门禁、API Landing/目录血缘、受控 ephemeral STG、轻量新建及三阶段详情均已落码。遵循本轮约束，实施期间不逐项运行测试；待全部静态复核完成后统一执行一次后端、前端与 Chrome 95 验证，因此三项保持 `IN_PROGRESS`，验证前不宣称完成。
