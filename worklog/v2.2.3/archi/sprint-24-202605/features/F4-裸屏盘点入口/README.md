# F4: 裸屏盘点入口

**优先级**: P1
**状态**: READY

## 目标

给管理员一个明确的入口，能列出所有 `classification IS NULL` 的"裸屏"，便于运维联系 owner 补登。F3 解决"未来不再产生新裸屏"，F4 解决"存量裸屏怎么收敛"。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 后端 `GET /api/screens/admin/unclassified` 端点（仅 OP_ADMIN / superuser） | P1 | READY | - |
| T02 | 端点返回 [{ id, name, creatorId, creatorEmail, createdAt, lastVisitedAt }] | P1 | READY | T01 |
| T03 | 前端管理页加「裸屏盘点」面板，展示列表 + 一键跳转编辑器去补登 | P1 | READY | T02 |
| T04 | 端点本身写审计 `screen.compliance.audit_unclassified` | P1 | READY | T01 |

## 完成标准

- [ ] `GET /api/screens/admin/unclassified` 实现：
  - 仅 OP_ADMIN / superuser 可调，其它角色 403。
  - 返回所有 `archived=false AND classification IS NULL` 的大屏数组。
  - 含字段：`id / name / creatorId / creatorEmail / createdAt / lastVisitedAt`（lastVisitedAt 走 BiReportVisit 关联，可为空）。
  - 端点本身 hit 一次写一条审计 `screen.compliance.audit_unclassified`，记录调用者与命中行数。
- [ ] 前端管理页（/screens/admin 或类似）新增「裸屏盘点」入口：
  - 表格展示，每行一个跳转按钮直接打开对应大屏编辑器。
  - 显示 count（如 3 张未设密级）。
  - 列表为空时显示「全部大屏均已设置密级 ✓」。
- [ ] 单元测试：
  - 普通用户 403。
  - OP_ADMIN 拿到列表。
  - 已设密级的大屏不出现在结果里。

## 关键文件

- 改：`source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ScreenResource.java`（参考已有 `/admin/backfill-grants:2418`）
- 改：`source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/repository/AnalyticsScreenRepository.java`（新增 `findByArchivedFalseAndClassificationIsNull`）
- 新增：前端管理页相应 panel 组件
- 新增：对应单元测试

## 注意

- **不做静默回填**：盘点出来后由 owner 自行补登，不在后端跑批回填默认密级（避免误判）。
- 端点要求 OP_ADMIN 是为了与现有 `/admin/backfill-grants` 一致；如果后续运营层需要看，可以放宽到 INST_DATA_OWNER 等角色，本 sprint 不做。
- lastVisitedAt 关联 `bi_report_visit` 比较慢；若性能不达标，先返回 null（前端不强依赖该字段）。
