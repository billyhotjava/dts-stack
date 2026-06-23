# Sprint-2 连接 · 实现进度（原型）

**状态**: IN_PROGRESS（2026-06-23）

| Feature | 状态 | 说明 |
|---|---|---|
| F1 数据源 | ✅ DONE | 列表(CompactTable, 平台共享/本部门归属 + 权限语义) · 详情抽屉 · 新建/编辑 modal · 连通测试(回写状态) |
| F2 连接器与驱动 | ✅ DONE | 连接器注册 tab + JDBC 驱动 tab（平台层，只读列表） |
| F3 接入与调度 | 🚧 简版 | 接入与调度 tab 为能力占位，后续完善 |

## 部门为主模型落地
- 连接阶段 scoped 到当前部门：`dataSourceService.listForDepartment(deptId)` = 平台共享 + 本部门本地。
- 平台共享源（PLM/ERP/QMIS）对部门**只读**（编辑/删除禁用）；本部门本地源可增删改。
- 物理接入/密钥在平台/部门层统一管控（表单不明文录入密钥）。

## 验证（Playwright + 构建）
| 项 | 结果 | 证据 |
|---|---|---|
| 数据源列表 + 归属/权限 | PASS | `it/datasources.png`（平台源 编辑/删除 禁用） |
| 连通测试回写状态 | PASS | `it/conn-test-fail.png`（销售本地台账 File 无连接信息→异常 + 时间更新） |
| 4 tab 切换（数据源/连接器/驱动/接入调度） | PASS | 无障碍快照 tablist |
| CompactTable 10 条/页 | PASS | 分页"共 4 条 · 10 条/页" |
| tsc + chrome95 构建 | PASS | 产物零 oklch/:has/容器查询/subgrid |

## 待办（S2 收尾）
- F3 接入变更审批 + 采集任务调度 真实化
- 连接器/驱动的新增/上传写操作
- 包体代码分割（gzip 已 ~370KB）
