# T01: gov_quality_run 新增 rows_total + Liquibase

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标
为 gov_quality_run 表添加 rows_total 字段，存储检测时目标表的总行数。

## 技术设计

### Liquibase changelog
```xml
<addColumn tableName="gov_quality_run">
    <column name="rows_total" type="integer" defaultValue="0">
        <constraints nullable="true"/>
    </column>
</addColumn>
```

### 实体
在 `GovQualityRun.java` 中添加：
```java
@Column(name = "rows_total")
private Integer rowsTotal;
// getter/setter
```

## 影响范围
| 文件 | 改动 |
|------|------|
| 新增 Liquibase changelog | ALTER TABLE |
| 修改 `GovQualityRun.java` | 新增字段 |

## 验证
- [ ] 迁移成功，现有 run 记录 rows_total 为 0/null
- [ ] 实体映射正确
