# T06: 已删除 Liquibase 文件兼容性检查

**模块**: Platform 后端
**文件**: `source/dts-platform/src/main/resources/config/liquibase/master.xml` 及相关 changelog

## 问题

多个 Liquibase XML 文件（20260226 ~ 20260308）从 master.xml 和磁盘删除。
如果任何生产/测试库已执行过这些 changeset，Liquibase 启动时会因找不到文件而校验失败。

## 修复方案

1. 检查所有已部署环境的 `DATABASECHANGELOG` 表，确认是否有已执行的被删 changeset
2. 如有：恢复文件到磁盘（仅文件，不加回 master.xml），或添加清理迁移删除 DATABASECHANGELOG 中的对应条目
3. 如无：安全，无需操作

## 当前行动

标记为 NEEDS_VERIFICATION — 需检查部署环境。
