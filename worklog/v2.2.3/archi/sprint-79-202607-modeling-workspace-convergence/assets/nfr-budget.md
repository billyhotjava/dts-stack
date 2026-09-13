# 非功能预算（Gate G1）

**依据**：`assets/domain-profile.md` + DTS 领域不变量  
**适用范围**：统一建模工作台、关系图投影、兼容观测和发布短流程

| 维度 | 预算 | 适应度函数（可执行） | 归属 Task | 状态 |
|---|---|---|---|---|
| 浏览器 | Chrome 95 完成核心 happy path，无不支持语法 | Chrome95 Playwright smoke + console error 断言 | F0/T01、全部 UI Task | GAP |
| 页面结构 | 新增/改造单文件 ≤800 行，超限页先拆分 | `wc -l` 守卫；新增超限即失败 | F1/T01、F2/T01 | PASS |
| 首屏请求 | 进入指定 module 不得加载其他六模块业务数据；单模块首轮业务请求 ≤6 | Playwright route counter | F1/T01 | PENDING |
| 列表 | 默认 10 条/页；pageSize 变化重置第 1 页 | 组件/契约测试 | F1/T02、F3/T01 | PENDING |
| 关系图 | 首次最多返回 500 nodes / 1000 edges；超限返回摘要与筛选提示 | API IT 断言上限和 `truncated=true` | F3/T02 | PENDING |
| 超时 | 建模 API 复用 `withModelingRequestTimeout`；图谱投影服务端 ≤10s fail-fast | 静态契约 + 超时故障注入 | F1/T02、F3/T02 | PENDING |
| 并发 | ModelSpec/Dimension/Plan/Candidate 写入继续使用 If-Match；重复 intent 幂等 | 409/412 冲突 IT + 重放断言 | F2/T02、F4/T01 | PENDING |
| 租户/权限 | 跨租户不可见；越权写返回 403；前端不提交 tenant/caller | 授权 IT | F0/T02、F4/T01 | PENDING |
| 审计 | 旧路由访问和删除动作必须有非“未分类”记录 | PostgreSQL + 审计字典 IT | F0/T02、F5/T02 | PENDING |
| 失败恢复 | URL 刷新恢复 plan/module/asset；API 失败保留上下文并提供重试 | 浏览器四态走查 | F1/T01 | PENDING |
| 发布安全 | 快捷入口不得直接 approve/publish；未 PUBLISHED 禁生成运行 | Candidate 命令契约测试 | F4/T01、F4/T02 | PENDING |
| 删除安全 | 兼容路由/表需两版本零访问 + dry-run + 回滚证据 | retirement gate 脚本返回红/绿 | F5/T01、F5/T02 | PENDING |
| 数据规模 | 客户生产规模未知，不设虚假 P95 | F0 客户画像后追加预算 | F0/T02 | GAP |

## 未达标项处置

| 缺口 | 影响 | 处置 | 关联 Task |
|---|---|---|---|
| 认证浏览器基线未复验 | 所有 UI DoD 不可关闭 | 恢复测试账号/会话并固化一次性 smoke | F0/T01 |
| 客户数据规模未知 | 无法批准关系图和列表生产预算 | 客户环境只读画像后更新本文件 | F0/T02 |
