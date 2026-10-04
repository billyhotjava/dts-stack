# F4：模型工作台性能与交付状态稳定性

**优先级**：P0  **状态**：IN_PROGRESS  **日期**：2026-09-08

## 目标与现场归因
用户进入模型工作台时能先看到并操作模型列表；交付状态逐行到达，单个失败不影响其余模型；状态查询不能长期占住连接并等待另一条连接。保留 F3 建模完成与数据运营分离的业务判定。

现场基线为源码/部署 checkout `200812f71094a78020d866a79ee9257be7ba02d4`，容器具体制品身份在 T04 核验，不以 checkout 替代镜像身份。现场 Hikari total=10/active=10/idle=0，等待线程约 9–14，30000ms 后超时；PG 快照 8 条 idle in transaction 约 34s，最后 SQL 为 modeling_warehouse_plan owner 查询。浏览器已登录工作台，17 条记录、第一页10条的三列均显示交付状态读取失败。不能把该证据当成 SQL 慢扫描或 CPU 不足。

完整调用链：列表逐模型 GET delivery-status → ModelDeliveryStatusQueryService.get 的只读外层事务 → authoring/workspace/权限读取 → CandidateQualityRuleContextService.context(NOT_SUPPORTED，挂起外层) → DefaultLakeDatasetGuard → DefaultDestinationSyncService.checkDefaultDestinationStatus(REQUIRES_NEW) → admin 数据湖读取及本地镜像同步。外层事务挂起并不释放其连接，构成并发连接饥饿风险；该因果链仍需事务代理回归及运行并发复验。

## 冻结契约与复用边界
- UI：`/data-modeling/dimensions/workbench`，既有列表/筛选/分页/编辑器；不新增菜单。
- GET `/api/modeling/model-specs/{id}/delivery-status`；id UUID；environment 可选 string，candidateId 可选 UUID。返回现有 ApiResponse<DeliveryStatusView>，保留 modelSpecId UUID、modelRevision int、modelChecksum string、candidate、workspace、steps[]、actions[]、modelingResult、dataPrimaryAction。错误/权限/候选版本语义不变。
- 列表仍为每页10条；状态并发上限2，逐行 loading/success/error；翻页或修订改变后旧请求不得覆盖新行。既有50秒客户端超时保持，禁止自动无限重试。
- 数据复用 modeling_model_spec、modeling_warehouse_plan、既有候选/创作/质量/目录仓储；无 schema、索引、事件或写接口变更，不改权限/密级、不扩大池容量、不创建平行状态台账。
- 聚合状态为多个已提交快照的身份绑定投影；保留 revision/checksum/environment/candidate 匹配，不承诺多个外部系统的原子快照。

## UI/UX 与四态
布局保持：工具栏/筛选 → 每页10行列表 → 原编辑器。
空：沿用空列表；加载：列表可见，各模型状态独立等待；成功：每条返回立即展示；失败：只在失败模型标注读取失败，成功行保留。翻页不回填上页状态；刷新可重试同版本失败。模型详情与新建继续使用原字段和版本检查；编辑资料未就绪时不得凭空采用默认值保存。
走查：进入工作台→查看首屏→翻页→返回第一页→刷新→编辑模型→返回列表；另注入一条失败、快速翻页、同模型 revision 更新。Chrome95 下无新语法/API依赖。

## Task 与依赖
| Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|
| T01 交付聚合事务边界 | P0 | IN_PROGRESS | 现有契约/现场账本 |
| T02 列表状态限流与逐行隔离 | P0 | IN_PROGRESS | 现有 delivery-status 契约 |
| T03 首屏与编辑辅助加载分离 | P1 | IN_PROGRESS | T02；原 loadModelWorkbenchContext 契约 |
| T04 正式验证与运行性能验收 | P0 | IN_PROGRESS | T01–T03 源码进入 Git |

## DoR 与 Gate
- [x] 契约、页面入口、复用 owner、错误路径、依赖及测试目标固定。
- [x] 现场已登录17条真实模型；容器/数据库可访问；沿用 Sprint 已有正式 Maven/Vitest/发布入口。
- [x] GitNexus get 上游1个 REST调用方，LOW；列表组件图未报告调用方，LOW，实际 React 页面消费由源码确认。
- [ ] 新制品、并发时延与 Chrome95 完整验收（T04），不得用旧基线关闭本 Feature。

## 性能预算与可执行验证
| 项目 | 预算/检查 |
|---|---|
| 聚合事务 | Spring事务代理测试断言调用下游时无聚合事务；10并发实测无30s池等待 |
| 状态并发 | 延迟Promise组件测试断言同时最多2个；完成一个才补一个 |
| 失败隔离 | 组件测试一条reject，其余成功状态立即保留；迟到响应不串页 |
| 首屏 | 延迟辅助接口测试证明列表先显示；编辑不能使用未加载资料 |
| 运行目标 | 同一17条模型、10并发、至少3轮；状态P95目标<3s、首屏列表目标<3s；记录实值，未达标继续归因 |
| 权限与身份 | 既有 ModelDeliveryStatusQueryServiceTest 回归，跨模型/旧revision不得误判成功 |

## DoD
- [ ] 事务与UI回归通过；正式构建/镜像身份归档（用户要求直接替换，不出补丁包）。
- [ ] IT-25–IT-28 真实证据，Chrome95及窄视口四态走查。
- [ ] 每阶段分开报告，未测不标 DONE；无迁移所以迁移验收N/A。
