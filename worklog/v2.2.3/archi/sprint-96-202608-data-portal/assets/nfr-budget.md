# 非功能预算（Gate G1）

**依据**：`assets/domain-profile.md` + DTS Chrome 95/权限不变量  
**适用范围**：大屏发布态目录、数据门户页面、发布态运行时嵌入

| 维度 | 预算 | 适应度函数（可执行） | 归属 Task | 状态 |
|---|---|---|---|---|
| 数据量级 | 5,000 总大屏、单用户 1,000 可见叶子；页面不依赖一次渲染全部折叠节点 | helper/unit 生成 1,000 叶子并断言稳定树与搜索 | F2/T01 | PASS |
| 查询效率 | 目录列表发布版本批量查询，禁止按大屏 N+1 | Mockito/IT 断言 repository 批量方法一次调用；源码不在 stream 中调用 `findFirst...` | F1/T01 | PASS |
| 索引 | 复用 `idx_analytics_screen_domain_id`、`idx_screen_version_current_published` | G0 `pg_indexes` 断言；无新表/索引迁移 | F1/T01 | PASS |
| 延迟 | 本地 1,000 叶子树构建 <100ms；API P95 目标 <500ms（客户环境待校准） | helper benchmark/unit；运行 smoke 记录 duration | F1/T01、F2/T01 | GAP |
| 并发 | N/A——本 Sprint 无新增写接口 | — | — | N/A |
| 超时/重试 | N/A——浏览器只调用既有同源 API，不新增出站 client | — | — | N/A |
| 幂等性 | 菜单迁移重复启动不重建 menu ID/visibility | Liquibase checksum + contract 检查无 DELETE；迁移 dry-run | F2/T02 | PASS |
| 审计 | 成功展示继续触发一次现有 screen visit tracker | source/unit + Mock E2E 断言 `/api/reports/visit` | F2/T02 | PASS |
| 密级/权限 | 目录排除不可读；详情 403；无发布 409 且不 fallback draft | Analytics IT | F1/T01 | PASS |
| 失败模式 | 主题域失败时平铺大屏；大屏 API 失败显示错误态；0 published 显示空态 | portal unit/source + Mock E2E | F2/T01 | PASS |
| 兼容性 | Chrome >=95；禁止 `:has`、container query、dvh/svh/lvh、toSorted/Object.groupBy | legacy `pnpm build` + 静态检查 + desktop/narrow Playwright | F3/T01 | PASS_WITH_GAP |

## 未达标项处置

| 缺口 | 影响 | 处置 | 关联 Task |
|---|---|---|---|
| 客户容量未知 | API P95 只能给实现预算，不能承诺生产 SLO | 记录运行 smoke；客户环境上线前校准 | F3/T01 |
| Chrome 95 executable 缺失 | 无法关闭真实兼容门禁 | Chrome 150 + legacy build 先验收，最终状态保留 GAP | F3/T01 |
