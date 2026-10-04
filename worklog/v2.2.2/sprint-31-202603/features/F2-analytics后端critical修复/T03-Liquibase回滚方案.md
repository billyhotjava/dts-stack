# T03: Liquibase 0041 drop_screen_acl 回滚方案

**严重度**: Critical
**文件**: `source/dts-analytics/src/main/resources/config/liquibase/changelog/0041_drop_screen_acl.xml`

## 问题

`0041_drop_screen_acl.xml` 执行 `<dropTable>` 删除 `analytics_screen_acl`，但无 rollback 定义。
如果平台权限系统未完全就绪时执行此迁移，ACL 数据无法恢复。

## 修复方案

在 `<changeSet>` 中添加 rollback，重建表结构（不含数据）：

```xml
<changeSet id="0041-drop-screen-acl" author="dts">
    <preConditions onFail="MARK_RAN">
        <tableExists tableName="analytics_screen_acl"/>
    </preConditions>
    <dropTable tableName="analytics_screen_acl"/>
    <rollback>
        <createTable tableName="analytics_screen_acl">
            <!-- 恢复原表结构 -->
        </createTable>
    </rollback>
</changeSet>
```

注意：rollback 只能恢复表结构，数据已在 0039 迁移到平台权限体系，不可恢复。
需在 rollback 注释中明确说明。

## 验证

- 确认迁移正向执行成功
- 确认 `liquibase rollbackCount 1` 可恢复表结构
