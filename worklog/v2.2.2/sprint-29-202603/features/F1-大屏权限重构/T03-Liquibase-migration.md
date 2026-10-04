# T03: Liquibase migration（加字段、删旧表）

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标
为 analytics_screen 表添加 classification 和 owner_dept_code 字段，清空并删除 analytics_screen_acl 表

## 技术设计

### 0040_screen_classification.xml

```xml
<changeSet id="0040-01-add-classification" author="system">
    <addColumn tableName="analytics_screen">
        <column name="classification" type="varchar(32)"/>
        <column name="owner_dept_code" type="varchar(64)"/>
    </addColumn>
</changeSet>
```

### 0041_drop_screen_acl.xml

```xml
<changeSet id="0041-01-drop-screen-acl" author="system">
    <dropTable tableName="analytics_screen_acl"/>
</changeSet>
```

### master.xml 更新

在 0039 之后添加 0040 和 0041。

## 影响范围
- 新建: `changelog/0040_screen_classification.xml`
- 新建: `changelog/0041_drop_screen_acl.xml`
- 修改: `master.xml`
- 修改: `AnalyticsScreen.java` 实体添加 classification、ownerDeptCode 字段

## 验证
- [ ] Liquibase 迁移成功执行
- [ ] analytics_screen 表有 classification 和 owner_dept_code 列
- [ ] analytics_screen_acl 表已删除
- [ ] 现有大屏数据不受影响

## 完成标准
- [ ] 迁移脚本通过本地测试
- [ ] 实体字段与数据库列一致
