# BUG-003: Excel 入湖修改字段后旧表未 DROP

- **优先级**: P0
- **状态**: TODO

## 问题

编辑 Excel 入湖任务修改了字段后，ODS 目标表结构未更新（旧列仍在），Addax 报字段错误。

## 根因

`ExcelImportService` 重新上传时只做了 DELETE 行数据，未 DROP + 重建目标表。

## 方案

检测到 schema 变化（列名/列数不同）时，在 Addax 任务执行前先 DROP TABLE IF EXISTS 再重建。

## 涉及文件

- `source/dts-platform/src/main/java/.../ExcelImportService.java`
- `source/dts-platform/src/main/java/.../DefaultDestinationSyncService.java`

## 交付标准

- [ ] 修改字段后重新上传 Excel，ODS 表结构正确更新
- [ ] Addax 任务正常执行，不报字段错误
