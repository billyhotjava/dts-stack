# 运维手册（Gate G4）

**功能**：数据门户  **负责人**：DTS 平台/分析服务运维  **风险等级**：中

## 1. 这是什么 / 正常状态

“数据门户”是已登录用户消费已发布大屏的统一入口：`dts-platform-webapp` 读取治理主题域和 `dts-analytics` 发布目录，左侧生成菜单树，右侧以同源 iframe 加载当前发布版本。

正常时：`/bi/portal` 可打开；目录只含当前用户可读且 `current_published=true` 的非归档大屏；点击叶子后 URL 为 `/bi/portal/{screenId}`，详情请求带 `mode=published&fallbackDraft=false`。主题域接口单独失败时门户降级为“未归类”，不阻断大屏消费。

## 2. 健康检查

| 检查项 | 命令/端点 | 期望结果 |
|---|---|---|
| 容器状态 | `docker compose -f docker-compose-app.yml ps dts-admin dts-analytics dts-platform-webapp` | 三个服务 running/healthy |
| 门户静态入口 | 登录后打开 `/#/bi/portal` | 页面出现“数据门户”；无发布项时出现可操作空态 |
| 发布目录 | 登录态 `GET /bi/api/screens?publishedOnly=true` | 200 数组；每项 `publishedVersionNo > 0` |
| 发布详情 | 登录态 `GET /bi/api/screens/{id}?mode=published&fallbackDraft=false` | 有权且已发布为 200/sourceMode=published；无发布版本为 409 |
| 主题域降级 | 暂时模拟 domain API 失败的验收环境 | 大屏仍显示在“未归类”，目录 API 不受影响 |
| 菜单一致性 | 查询 `portal_menu` 的 `sys.nav.portal.biScreens` | 恰好一条、title=数据门户、externalLink=/bi/portal，visibility 数不变 |

## 3. 告警建议

| 告警 | 条件与阈值 | 级别 | 含义 | 处置 |
|---|---|---|---|---|
| 发布目录失败 | `/api/screens?publishedOnly=true` 5 分钟窗口 5xx 比例 > 2% 且请求数 >= 20 | P1 | 用户无法进入门户或刷新目录 | 查同窗口 request id 和 dts-analytics 日志；按故障场景 A 处置 |
| 发布详情失败 | `mode=published` 5 分钟窗口 409+5xx 比例 > 5% 且请求数 >= 20 | P1 | 菜单与发布版本可能漂移 | 抽查 screenId/current_published；按故障场景 B 处置 |
| 门户空目录异常 | 有效 published screen 数 > 0 时，10 分钟内目录响应连续 3 次为 0（按同一角色样本账户） | P2 | 权限头、会话或目录过滤异常 | 对比管理页、访问 ACL 和平台转发身份 |
| 主题域降级持续 | domain tree 失败持续 15 分钟 | P2 | 菜单失去业务分组但仍可消费 | 检查 dts-platform catalog；恢复后刷新即可 |

同一根因的目录/详情告警按 request id 与服务实例聚合，15 分钟内抑制重复通知。

## 4. 关键日志与追踪

| 字段 | 用途 | 示例 |
|---|---|---|
| request id / correlation id | 串联 webapp 代理、platform、analytics | 响应头或错误页请求号 |
| screenId | 定位大屏与发布版本 | `101` |
| mode / fallbackDraft | 判断是否走严格发布态 | `published / false` |
| platform user/role 摘要 | 定位访问判定 | 用户标识、角色编码；不得记录令牌 |
| HTTP status / duration | 判断权限、发布缺失与延迟 | `200 / 128ms` |

禁止记录：访问令牌、Cookie、密码、大屏数据内容、组件查询结果、SECRET/CONFIDENTIAL 业务明细。审计访问继续复用现有 screen visit tracker，不用应用日志代替合规审计。

## 5. 故障处置

| 场景 | 症状 | 立即动作 | 恢复动作 | 可回滚 |
|---|---|---|---|---|
| A. dts-analytics/目录上游不可用 | 门户错误态，可点击重新加载 | 确认 analytics 容器、代理和会话；不要反复重启全栈 | 恢复 analytics 后点“重新加载”；必要时仅回滚 analytics 镜像 | 是 |
| B. 发布目录与版本异常 | 左树有叶子但 iframe 409，或详情误读 draft | 记录 screenId；检查 `analytics_screen_version.current_published`，禁止临时改成 fallback draft | 由大屏 owner 重新校验/发布，或回滚本次应用版本 | 是 |
| C. 主题域脏数据/缺失 | 大量大屏进入“未归类” | 保持降级可用；比对 domainId 与治理域 ID | 修复治理绑定后刷新；不要由门户自动回写 domainId | 不需回滚 |
| D. 菜单迁移卡住/重复 | dts-admin 启动失败，提示 target/snapshot 数异常 | 停止重复启动；保留 snapshot 和日志 | 查明重复 menu 或旧 snapshot，再按 release plan 人工决定前向修复/rollback | 是，需证据 |

## 6. 容量与归档

- 当前只读画像：2 个有效大屏、0 个当前发布大屏、7 个治理主题域。
- 设计预算：总大屏 5,000、单用户可见叶子 1,000；1,000 叶子 helper 测试通过。
- 本地目录树构建预算 <100ms；生产 API P95 目标 <500ms，需在客户环境校准。
- 门户不新增持久数据；发布版本归档/清理由既有大屏生命周期负责。本 Sprint 不自动清理版本。
- 当单用户可见叶子持续超过 1,000 或目录 P95 连续 15 分钟超过 500ms，应评估服务端分页/增量树，而不是提高浏览器一次性渲染上限。

## 7. 禁止操作

- 不得为让门户“有内容”而擅自发布客户草稿。
- 不得把 published 详情改成 fallback draft 规避 409。
- 不得删除/重建 `portal_menu` 或 `portal_menu_visibility`；必须保留 menu ID 和授权绑定。
- 不得用 `BiReportLink` 小时级镜像替换实时发布目录 owner。
- 不得在未确认 snapshot 与后续修改漂移前强行回滚菜单 metadata。
