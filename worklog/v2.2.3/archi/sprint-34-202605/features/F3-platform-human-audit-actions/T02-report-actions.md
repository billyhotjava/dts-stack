# T02: 报表查看/管理动作修复

**优先级**: P0
**状态**: DONE
**依赖**: F2

## 目标

让查看报表、修改报表、新建报表、删除报表稳定记录为数据可视化/报表管理动作。

## 技术设计

- 使用可读 actionCode：`REPORT_VIEW`、`REPORT_CREATE`、`REPORT_UPDATE`、`REPORT_DELETE`。
- 前端打开报表前确保访问记录不会被导航中断；后端保留 visit endpoint 记录。
- DB catalog moduleName 使用“数据可视化”，operationName 使用“查看报表”等中文。

## 影响范围

- `ReportsResource.java`
- `reportsService.ts`
- dts-admin DB action seed

## 验证

- [x] 查看报表记录为业务端审计/数据大屏/查看大屏。
- [x] 修改报表记录为业务端审计/数据大屏/修改大屏。

## 完成标准

- [x] 不再出现 raw `vis.reports.*` 操作内容。
