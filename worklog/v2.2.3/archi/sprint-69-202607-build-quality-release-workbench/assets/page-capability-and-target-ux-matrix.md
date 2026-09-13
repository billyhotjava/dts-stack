# Sprint-69 页面能力与目标 UX 矩阵

| 页面 | 路径 | 当前能力 | 当前判定 | Sprint-69 目标 | 主 API | 风险 |
|---|---|---|---|---|---|---|
| 数据建设工作台 | `/modeling/workbench` | 九站只读投影、首要阻塞、下一步 | REAL/PARTIAL | 第六步展示当前候选和真实发布状态 | warehouse-plan stage projection | READY 被误报完成 |
| 计划实现与验证 | `/modeling/plans/:planId/implementation` | 说明卡、跳转 SQL/dbt | PARTIAL | 计划级交付工作台、候选范围、构建/质量/审核/发布/回滚 | release candidates + projection | 当前无业务闭环 |
| 模型详情 | `/modeling/models/:modelSpecId` | 实现门禁、进入 SQL/dbt | PARTIAL | 当前候选、证据时间线、revision 漂移和修复入口 | stage-gates + lifecycle | timeline API 未消费 |
| 高级建模 | `/studio/sql-modeling` | 编译、测试、构建、发布、日志 | REAL/RISK | 只做高级实现和执行，锁定 candidate entry，移除自动审批发布 | ETL dbt + model lifecycle | 多模型 run 可能回写单模型；页面过载 |
| 质量管控 | `/governance/rules` | 规则、模板、dry-run、运行 | REAL | 维护规则 owner，支持 candidate/model 上下文过滤 | governance quality | 规则结果未绑定 revision |
| 质量报告 | `/governance/quality` | 通用质量报告 | REAL/PARTIAL | 从候选质量摘要跳转到相同 run/rule 详情 | governance quality reports | 预发布模型可能尚无正式资产 |
| 发布治理 | `/ops/release-governance` | 平台级聚合门禁，写动作禁用 | DUPLICATE | 保持平台健康聚合，只链接计划交付工作台 | sprint27 release governance | 不得成为第二发布主线 |
| 运行实例 | `/ops/instances` | 运行列表、日志、返回模型 | REAL | 接收 candidateId/modelSpecId/revision/runId 精确上下文 | ops instances | 修复路径必须 fail closed |

## 目标页面主区

| 区域 | 默认内容 | 可操作动作 |
|---|---|---|
| 页头 | 计划、环境、当前候选、状态、首要阻塞 | 新建候选、刷新、查看计划 |
| 候选范围 | 模型类型/层/revision/checksum/漂移 | 添加、移除、刷新候选版本 |
| 构建 | artifact、external run、selector、target | 开始构建、查看日志、返回模型 |
| 质量 | 通过率、BLOCKER/WARN、失败规则 | 运行质量、查看失败样本、进入规则 owner |
| 审核发布 | 提交人、审批人、评论、注册步骤 | 提交、批准/驳回、发布、重试 |
| 历史 | 候选、发布、回滚、操作者时间线 | 查看详情、对比、回滚 |

## UI 硬门禁

1. 页面只显示一个与服务端 `nextAction` 对应的主要按钮。
2. 不允许通过隐藏按钮规避服务端角色、ETag、revision 或状态机。
3. 读取失败保留已加载内容；UNKNOWN、STALE、PARTIAL 必须可区分。
4. mutation 期间锁定相关控件，防止重复提交；幂等仍由服务端保证。
5. 390px 下候选范围和门禁使用卡片/抽屉，不依赖横向大表。
6. Chrome 95 不使用不受支持的语法、CSS 或运行时 API。
7. 所有失败都提供精确修复入口，不显示内部英文 blocker 作为客户主文案。
