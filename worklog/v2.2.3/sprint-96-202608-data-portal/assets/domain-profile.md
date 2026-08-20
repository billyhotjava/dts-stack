# 领域画像（Gate G0）

**勘察日期**：2026-08-20  
**来源**：客户需求表述、现有 UI/接口、运行库只读 SQL  
**结论**：可据此实现主题域分组的发布态门户；运行库当前无已发布大屏，真实填充态验收保留 GAP。

## 1. 统一语言

| 术语 | 定义 | 禁用/不等于 | 出处 |
|---|---|---|---|
| 数据门户 | 面向业务用户消费已发布大屏的统一入口 | 大屏管理、资产门户、公开分享页 | 客户需求 |
| 门户目录 | 主题域节点与大屏叶子组成的左侧导航树 | dts-admin 全局角色菜单 | 客户需求 + DTS 菜单边界 |
| 大屏叶子 | 以稳定 screenId 指向一块大屏，显示大屏名称 | URL 字符串、public UUID、固定 versionId | 客户需求 |
| 当前发布版本 | `analytics_screen_version.current_published=true` 的唯一消费快照 | 草稿、最新编辑状态 | 现有发布契约 |
| 大屏管理 | 创建、编辑、发布、授权和归档大屏的工作台 | 数据门户 | 现有 `/bi/screens` |

## 2. 业务不变量

| # | 不变量 | 强制级别 | 违反后果 | 出处 |
|---|---|---|---|---|
| I1 | 门户目录只包含当前用户可读、非归档且存在当前发布版本的大屏 | 合规硬约束 | 草稿或越权内容泄漏 | ScreenPermissionService + 发布契约 |
| I2 | 目录隐藏不替代详情授权，加载发布内容时必须重新鉴权 | 合规硬约束 | 猜测 ID 越权 | DTS D4 |
| I3 | classification 与主题域分离；主题域只负责业务分组 | 合规硬约束 | 密级控制被业务分类绕过 | DTS D1 |
| I4 | screenId 稳定，重新发布/回滚不改变门户深链 | 业务规则 | 菜单链接随版本失效 | 客户导航诉求 |
| I5 | 草稿名称不能成为发布内容读取的依据；消费内容只取 currentPublished | 业务规则 | 编辑中内容提前曝光 | 发布契约 |
| I6 | 全局菜单只有一个数据门户入口，不按大屏动态增删角色菜单 | 架构规则 | 角色绑定漂移和 destructive reseed | DTS B2 + 历史事故 |

## 3. 真实数据画像

| 指标 | 实测值 | 查询/命令 |
|---|---:|---|
| 有效大屏 | 2 | `analytics_screen where archived=false` |
| 已归档大屏 | 0 | `analytics_screen where archived=true` |
| 当前已发布大屏 | 0 | `analytics_screen_version where current_published=true` |
| 未分配主题域 | 0 | `domain_id is null or blank` |
| 未设密级 | 0 | `classification is null or blank` |
| 密级分布 | SECRET=2 | `group by classification` |
| 治理主题域 | 7；最大深度 2 | `catalog_domain` recursive CTE |
| 大屏涉及主题域 | 2 | `count(distinct domain_id)` |
| 重复 currentPublished | 0 | `group by screen_id having count(*)>1` |
| 启用 screen 镜像 | 2；均指向 preview | `bi_report_link code like 'screen-%'` |

**对设计的直接影响**：

- 当前真实状态正好要求门户有清晰空态，不能把镜像链接误当发布态。
- 7 个主题域、深度 2 可由 Ant Tree 同步呈现；不需要分页，但目录搜索必须可用。
- `domain_id` 和 `(screen_id,current_published)` 已有索引；目录服务应批量水合发布版本，禁止逐屏查询。
- 本地数量不能外推客户容量；NFR 以 5,000 个大屏/1,000 个可见叶子作为实现预算与测试边界。

## 4. 外部边界

| 系统 | 契约 | 可用性 | 失败降级 |
|---|---|---|---|
| dts-analytics | 大屏、发布版本、权限与 published runtime | 当前 healthy | 目录错误态；禁止显示镜像兜底 |
| dts-platform | `/catalog/domains/tree` 主题域 | 当前 healthy | 退化为“未归类/全部大屏”平铺，不影响大屏权限 |
| dts-admin | 唯一全局菜单及角色可见性 | 当前 healthy | 旧 `/bi/screens` 管理路由仍可直达 |
| 浏览器 | Chrome 95 下限 | 当前只有 Chrome 150 | 构建 + 禁用特性静态检查；Chrome 95 留 GAP |

## 5. 合规要求

| 条款 | 要求 | 是否验收硬门槛 |
|---|---|---|
| 权限 | 目录与详情均 fail-closed，401/403 不降级为公开链接 | 是 |
| 密级 | 沿用 ScreenPermissionService/classification，不用主题域替代 | 是 |
| 审计 | 复用 `useScreenVisitTracker` 记录门户消费 | 是 |
| 角色菜单 | 原 menu ID 更新，禁止清空 `portal_menu_visibility` | 是 |

## 未决问题

- 客户若要求任意文件夹、别名、手工排序或多套门户，需要后续独立 Sprint 建模；本 Sprint 不预埋空表。
- 运行库无已发布大屏，真实填充态需客户发布后再做最终角色验收。
