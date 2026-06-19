# T02: JDBC 驱动管理

**优先级**: P1
**状态**: READY
**依赖**: S1

## 目标

落地 `JdbcDriversPage` JDBC 驱动管理列表，接 mock。

## 技术设计

- 文件：`app/src/stages/connect/JdbcDriversPage.tsx`（对齐现网 `pages/foundation/JdbcDriversPage.tsx`）。
- 表格：CompactTable，默认 10 条/页；列：驱动名、驱动类名（如 `com.mysql.cj.jdbc.Driver`）、版本、来源/上传状态、更新时间、操作。
- 上传/删除在原型阶段做 UI 占位 + mock 反馈（不做真实文件落盘）；上传交互走 AntD `Upload`，mock 返回成功/失败 `Result`。
- mock service：`jdbcDriversService`（`listJdbcDrivers`，可选 `uploadJdbcDriver` mock 占位）。

## 影响范围

- 新增 `app/src/stages/connect/JdbcDriversPage.tsx`
- 新增/扩展 `app/src/mock/services/jdbcDriversService.ts`（`listJdbcDrivers`）
- 阶段① 路由：`connect/jdbc-drivers`

## 验证

- [ ] 列表默认 10 条/页，分页行为符合统一约定。
- [ ] 驱动类名等长字段不溢出（Swiss 密度下截断/换行处理）。
- [ ] 上传占位走 mock，成功/失败有反馈，不静默吞错。
- [ ] Chrome 95：无禁用 CSS 特性。

## 完成标准

- [ ] 页面可路由可访问，CompactTable 列齐全。
- [ ] 经 `jdbcDriversService` 取数，上传为 mock 占位且有显式反馈。
