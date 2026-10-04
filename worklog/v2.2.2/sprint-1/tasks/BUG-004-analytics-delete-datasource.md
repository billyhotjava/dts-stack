# BUG-004: Analytics 数据源无法删除

- **优先级**: P0
- **状态**: TODO

## 问题

dts-analytics-webapp 升级后无法删除数据源。

## 排查方向

1. `DatabaseResource.delete()` 权限校验是否变更
2. `ExternalDatabaseDataSourceRegistry` 连接池缓存未清理（仅 @PreDestroy 才关闭）
3. 前端删除 API 调用是否匹配新版路径

## 涉及文件

- `source/dts-analytics/src/main/java/.../DatabaseResource.java` — delete 接口（396-407 行）
- `source/dts-analytics/src/main/java/.../ExternalDatabaseDataSourceRegistry.java` — 连接池管理
- `source/dts-analytics-webapp/modern/` — 数据源管理页面

## 交付标准

- [ ] 数据源可正常删除
- [ ] 删除后连接池正确释放
