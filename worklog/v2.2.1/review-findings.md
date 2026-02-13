# v2.2.1 代码审查结论（基于当前全量改动）

审查范围：`dts-admin`、`dts-ingestion`、`dts-platform`、`dts-platform-webapp` 当前工作区改动。

编译结果：
- `mvn -f source/dts-admin/pom.xml -DskipTests compile` ✅
- `mvn -f source/dts-ingestion/pom.xml -DskipTests compile` ✅
- `mvn -f source/dts-platform/pom.xml -DskipTests compile` ✅
- `pnpm -C source/dts-platform-webapp build` ✅

## 1. 发现项（按严重度）

### [中] RV-001 QueryDataset 作用域查询使用“精确 owner_dept 匹配”，可能导致可见性误判
- 位置：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/QueryDatasetService.java:67`
- 说明：列表查询直接走 `findByOwnerDeptIgnoreCase...` 精确匹配，未复用 `DepartmentUtils.matches` 的归一化策略。若 `dept_code` 存在格式差异（前缀、层级、大小写变体），可能出现“同部门数据集看不到”。
- 影响：项目内数据集可见性不稳定，表现为“偶发空列表”。
- 建议：改为先按候选集查询再用 `DepartmentUtils.matches` 过滤，或引入归一化字段索引。

### [中] RV-002 QueryDataset/BI Link 管理权限从“维护者全局”收紧为“仅 ADMIN/OP_ADMIN 全局”，需产品确认
- 位置：
  - `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/QueryDatasetService.java:61`
  - `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/visualization/BiReportLinkService.java:330`
- 说明：当前全局管理判断仅 `ADMIN/OP_ADMIN`，不再覆盖 `INST_DATA_OWNER/DEPT_DATA_OWNER` 等维护者角色。此变更可能与既有授权预期冲突。
- 影响：角色回归风险（部分维护者看不到/改不了历史资产）。
- 建议：对齐权限矩阵后统一改造（保留最小权限原则但避免隐式回归）。

### [低] RV-003 P3 QA 状态已标记 done，但缺少长期运行证据
- 位置：`worklog/v2.2.0/platform-elt-p3-issues.md`
- 说明：24h 稳定性、Addax/Airbyte 对照等已形成清单，但未见同仓证据（报告或脚本产物）。
- 影响：发布评审时缺少验证闭环。
- 建议：在 v2.2.1 增加“执行记录产物目录 + 报告模板 + 验证门禁”。

## 2. 仍需补的测试
- 跨项目隔离接口集成测试（`/api/sql/query-datasets`、`/api/reports`）。
- 血缘 `impact` 接口的 `projectName` 过滤回归测试。
- 角色矩阵回归（ADMIN/OP_ADMIN/INST_DATA_OWNER/DEPT_DATA_OWNER/EMPLOYEE）。

