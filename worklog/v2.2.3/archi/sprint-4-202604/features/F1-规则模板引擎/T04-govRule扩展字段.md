# T04: gov_rule 扩展字段

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标
扩展现有 gov_rule 表，支持模板关联、参数快照、处理策略配置。

## 技术设计

### 新增字段

```sql
ALTER TABLE gov_rule ADD COLUMN template_id UUID REFERENCES gov_quality_template(id);
ALTER TABLE gov_rule ADD COLUMN template_params JSONB;
ALTER TABLE gov_rule ADD COLUMN rendered_sql TEXT;
ALTER TABLE gov_rule ADD COLUMN action_on_fail VARCHAR(16) DEFAULT 'ALERT';
ALTER TABLE gov_rule ADD COLUMN auto_trigger BOOLEAN DEFAULT false;
```

### GovRule 实体扩展

```java
@Column(name = "template_id")
private UUID templateId;

@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "template_params", columnDefinition = "jsonb")
private Map<String, Object> templateParams;

@Column(name = "rendered_sql")
private String renderedSql;

@Column(name = "action_on_fail")
private String actionOnFail = "ALERT";

@Column(name = "auto_trigger")
private boolean autoTrigger = false;
```

## 影响范围
| 文件 | 改动 |
|------|------|
| `GovRule.java` | 新增字段 |
| 新增 Liquibase changelog | ALTER TABLE |
| `GovernanceMapper.java` | DTO 映射 |
| `QualityRuleDto.java` | 新增字段 |

## 验证
- [ ] 迁移脚本执行成功
- [ ] 现有规则不受影响（新字段都可为空/有默认值）
- [ ] API 返回新字段

## 完成标准
- [ ] 字段添加
- [ ] 实体、DTO 更新
