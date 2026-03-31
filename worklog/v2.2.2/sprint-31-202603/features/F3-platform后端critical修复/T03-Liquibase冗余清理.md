# T03: Liquibase 冗余建表/删表清理

**严重度**: Critical
**文件**: `source/dts-platform/src/main/resources/config/liquibase/master.xml`
**关联**:
- `changelog/20260316_01_project_cockpit_import.xml`（建表）
- `changelog/20260316_02_topic_binding_center.xml`（建表）
- `changelog/20260328_02_drop_temporary_foundation_features.xml`（删表）

## 问题

master.xml 先 include 建表迁移（20260316_01/02），再 include 删表迁移（20260328_02）。
- 全新部署：白白创建再删除，浪费 DATABASECHANGELOG 条目
- 已有部署：表会被删除（如有数据则丢失）

## 修复方案

从 master.xml 中同时移除建表和删表这三条 include：
```xml
<!-- 移除 -->
<include file="changelog/20260316_01_project_cockpit_import.xml" .../>
<include file="changelog/20260316_02_topic_binding_center.xml" .../>
<include file="changelog/20260328_02_drop_temporary_foundation_features.xml" .../>
```

如有已部署环境执行过 20260316_01/02 但未执行 20260328_02，需保留删表迁移，仅移除建表。
需确认部署情况后决定。

## 验证

- 全新环境 Liquibase 执行无冗余建/删操作
- 已有环境无 checksum 校验错误
