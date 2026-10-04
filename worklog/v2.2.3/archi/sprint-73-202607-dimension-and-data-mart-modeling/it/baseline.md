# 交付基线探针结果 (Gate G0)

**探针日期**: 2026-07-26  
**环境**: 当前 v2.2.3 本地运行部署  
**结论**: PASS_WITH_GAPS

| # | 探针 | 结果 | 证据 | 阻断项 |
|---|------|------|------|--------|
| P1 | 可运行实例 | ✅ | `v223-dts-platform-1` healthy；容器内 `GET :8081/management/health` → `UP` | - |
| P2 | 登录路径 | ⚠️ 未执行 | 本次不持有真实测试账号；没有伪造通过 | F0/T01 |
| P3 | 迁移状态 | ✅ | WarehousePlan business scope、DimensionDefinition ledger/ref 三个 changeset 均 `EXECUTED` | - |
| P4 | 真实数据 | ⚠️ 当前环境已画像 | 6 分类、503 资产、2 计划、1 维度定义、1 维度表；非客户生产数据 | F0/T02 |
| P5 | API 验收工具 | ⚠️ 未执行认证写 API | 只做数据库和健康只读探针 | F0/T01 |
| P6 | UI 验收工具 | ⚠️ 未执行 | 未运行 Chrome95 登录/截图 | F0/T01 |
| P7 | 构建与测试命令 | ⚠️ 按用户要求延期 | 本轮只建 Sprint，不编译、不构建容器 | F0/T01 |
| P8 | 外部依赖 | ✅/⚠️ | DataWorks 官方文档可访问；人员目录和资产注册真实链路待测 | F0/T01 |

## 阻断项与处置

| 阻断 | 影响哪些 Feature | 处置 | 归属 Task |
|------|------------------|------|-----------|
| 真实认证/API/Chrome95 未通过 | F2～F5 的 UI/端到端 DoD | 实施启动前恢复测试账号、API 请求和浏览器截图链 | F0/T01 |
| 生产数据画像缺失 | F2～F4 的容量、索引和迁移判定 | 在客户生产脱敏副本执行只读 SQL 和迁移 dry-run | F0/T02 |
| GitNexus 索引落后 6 commits | 所有符号影响分析 | 实施前执行 `npx gitnexus analyze` | F0/T01 |

## 本 Sprint 验收路径约定

- 后端验收：focused unit/contract → PostgreSQL IT → clean DB Liquibase → 迁移 dry-run。
- UI 验收：真实登录 → 业务分类/数据集市 → 建设计划业务范围 → 维度目录 → 维度表详情 → 发布后资产台账。
- 浏览器：Chrome 95，记录空/加载/错误/成功四态。
- 证据落地：`it/IT-01`～`IT-06`，不得用 TODO 或 mock 截图关闭 Sprint。
