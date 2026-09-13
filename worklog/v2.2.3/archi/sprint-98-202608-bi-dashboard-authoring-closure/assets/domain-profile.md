# 领域画像（Gate G0）

**勘察日期**：2026-08-21；**数据来源**：当前运行 `dts_analytics` PostgreSQL
**结论**：可据此设计；真实登录态和 Chrome 95 仍是验收缺口。

## 统一语言

| 术语 | 定义 | 禁用/映射 | 出处 |
|---|---|---|---|
| 治理分析 | `card_type=analysis` 且有当前已发布 revision 的分析资产 | 旧 `question` 不称治理分析 | Sprint-94/95、现网发布门禁 |
| 看板组件 | 看板对一个治理分析的布局与交互绑定 | UI 使用“分析组件”，不暴露 dashcard | 现网页面 |
| 发布范围 | 至少一个可见部门或可见角色 | 不称 audience，不要求客户手写编码 | 数据门户/报表登记 |
| 发布 | 钉定 dashboard/analysis revision 并异步注册业务入口 | 保存草稿不等于发布 | Sprint-94 publication contract |

## 业务不变量

| # | 不变量 | 级别 | 违反后果 |
|---|---|---|---|
| I1 | 看板发布只能引用已发布治理分析 revision | 治理硬门禁 | 依赖不可复现、可绕过数据集契约 |
| I2 | 发布范围至少含部门或角色 | 权限硬门禁 | 数据门户无法安全判定消费者 |
| I3 | 发布校验必须针对用户看见的已保存草稿 | 业务正确性 | 页面与服务端组件数/布局不一致 |
| I4 | 已发布看板不可原位修改 | 版本硬门禁 | 已发布快照被静默改写 |
| I5 | 布局和组件查询定义分属 DashboardCard 与 Analysis | 架构不变量 | 产生第二套查询/图表 owner |

## 真实数据画像

| 指标 | 实测值 | 查询摘要 |
|---|---:|---|
| 有效看板 | 2 | `analytics_dashboard where archived=false` |
| 看板组件 | 2 | `analytics_dashboard_card` |
| 有效 Card | 4 | `analytics_card where archived=false` |
| analysis Card | 3 | `card_type='analysis'` |
| 可发布治理分析 | 1 | analysis + PUBLISHED + revision |
| 不合格组件 | 1 | 缺失卡片或非 published analysis |
| 空/非法布局 | 0 / 0 | row/col/size 均合法 |
| 布局范围 | width 3～6，height 4 | 当前组件统计 |

**直接影响**：资源库必须有空态和“先发布分析”指引；历史不合格组件不能隐身；本 Sprint 无批量迁移或新索引需求。

## 外部边界

| 系统 | 契约 | 失败降级 |
|---|---|---|
| dts-platform 目录 | `/api/directory/orgs|roles` | 发布抽屉显示目录错误并禁止确认；不退回自由文本 |
| dts-platform 报表登记 | publication outbox → report registration | 发布 revision 保留为 PENDING/FAILED，可重试注册 |

## 合规要求

| 要求 | 验收硬门槛 |
|---|---|
| 部门/角色范围、发布密级不得绕过 | 是 |
| 不得降低依赖最高密级 | 是 |
| 发布钉定 revision/checksum/query budget | 是 |
