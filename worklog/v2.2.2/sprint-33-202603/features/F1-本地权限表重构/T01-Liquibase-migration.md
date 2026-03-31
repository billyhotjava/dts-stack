# T01: Liquibase migration — analytics_screen_access 表

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标

新建 `analytics_screen_access` 表，作为大屏权限的唯一本地存储。

## 技术设计

文件路径：`src/main/resources/config/liquibase/changelog/20260331_01_analytics_screen_access.xml`

```xml
<createTable tableName="analytics_screen_access">
    <column name="id" type="BIGINT" autoIncrement="true">
        <constraints primaryKey="true" nullable="false"/>
    </column>
    <column name="screen_id" type="BIGINT">
        <constraints nullable="false"
                     foreignKeyName="fk_screen_access_screen"
                     references="analytics_screen(id)"
                     deleteCascade="true"/>
    </column>
    <column name="grantee_type" type="VARCHAR(10)">
        <constraints nullable="false"/>
    </column>
    <!-- USER: analytics user.id 字符串化数字; ROLE: 角色名字符串 -->
    <column name="grantee_id" type="VARCHAR(255)">
        <constraints nullable="false"/>
    </column>
    <!-- OWNER | MANAGER | VIEWER -->
    <column name="permission" type="VARCHAR(10)">
        <constraints nullable="false"/>
    </column>
    <!-- 操作人 analytics user.id，系统自动创建时可为 null -->
    <column name="granted_by" type="BIGINT"/>
    <column name="created_at" type="TIMESTAMPTZ" defaultValueComputed="now()">
        <constraints nullable="false"/>
    </column>
</createTable>

<!-- 唯一约束：同一 (screen, granteeType, granteeId) 只有一条记录 -->
<addUniqueConstraint
    tableName="analytics_screen_access"
    columnNames="screen_id, grantee_type, grantee_id"
    constraintName="uq_screen_access_grantee"/>

<!-- 列表查询索引（按大屏查权限列表） -->
<createIndex tableName="analytics_screen_access"
             indexName="idx_screen_access_screen_id">
    <column name="screen_id"/>
</createIndex>

<!-- 用户权限查询索引（按 grantee 查可访问大屏） -->
<createIndex tableName="analytics_screen_access"
             indexName="idx_screen_access_grantee">
    <column name="grantee_type"/>
    <column name="grantee_id"/>
</createIndex>
```

## 影响范围

- 新建: `config/liquibase/changelog/20260331_01_analytics_screen_access.xml`
- 修改: `config/liquibase/master.xml`（引入新 changelog）

## 验证

- [ ] `mvn liquibase:update` 无报错
- [ ] 表结构与上述 DDL 一致
- [ ] UNIQUE 约束阻止重复 (screen_id, grantee_type, grantee_id)

## 完成标准

- [ ] 表在 PostgreSQL 中创建成功
- [ ] 两个索引存在
- [ ] `ON DELETE CASCADE` 生效（删大屏时权限记录随之删除）
