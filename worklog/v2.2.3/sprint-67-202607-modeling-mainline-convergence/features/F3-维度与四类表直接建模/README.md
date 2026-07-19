# F3：维度与四类表直接建模

**优先级**：P0
**状态**：DONE
**依赖**：F1、F2-T02
**目标**：让用户不经过业务对象即可登记维度并直接创建明细表、维度表、汇总表和应用表，ModelSpec 成为唯一模型事实源。

## Task

| Task | 优先级 | 状态 | 依赖 | 输出 |
|---|---|---|---|---|
| [T01-建立无业务对象的ModelSpec契约](T01-建立无业务对象的ModelSpec契约.md) | P0 | DONE | F1-T02/T03 | ModelSpec v2/API/持久化契约 |
| [T02-设计维度目录与维度表](T02-设计维度目录与维度表.md) | P0 | DONE | T01 | DIMENSION 目录与编辑流 |
| [T03-设计明细表与粒度时间语义](T03-设计明细表与粒度时间语义.md) | P0 | DONE | T01、F2-T03 | FACT 编辑流 |
| [T04-设计汇总表与应用表](T04-设计汇总表与应用表.md) | P0 | DONE | T01/T03 | SUMMARY/APPLICATION 编辑流 |
| [T05-统一字段来源标准关系与门禁](T05-统一字段来源标准关系与门禁.md) | P0 | DONE | T02/T03/T04 | 字段设计与分阶段 gate |

## 完成标准

- [x] ModelSpec 新写契约不存在 objectId 硬依赖。
- [x] 维度目录的数据源是 DIMENSION ModelSpec，不是业务对象表。
- [x] 四类表各有独立必填规则、默认层级和产物说明。
- [x] 标准、来源、维度关系和指标只保存稳定引用。
- [x] 保存草稿、进入实现、提交发布使用不同且可解释的门禁。

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
