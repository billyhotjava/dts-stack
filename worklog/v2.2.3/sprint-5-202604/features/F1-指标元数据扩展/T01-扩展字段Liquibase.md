# T01: gov_indicator_definition 扩展字段 Liquibase

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标
为现有 gov_indicator_definition 表新增计算定义、维度粒度、数据绑定、业务属性、LLM 预留等字段。

## 技术设计

### 新增字段（ALTER TABLE）

```sql
-- 计算定义
ADD COLUMN aggregation_type     VARCHAR(16) DEFAULT 'SUM',
ADD COLUMN measure_field        VARCHAR(200),
ADD COLUMN numerator_expression TEXT,
ADD COLUMN denominator_expression TEXT,
ADD COLUMN static_filter        TEXT,
ADD COLUMN dynamic_filter_config JSONB,
ADD COLUMN is_derived           BOOLEAN DEFAULT false,
ADD COLUMN dependency_indicators JSONB,        -- ["code1","code2"]
ADD COLUMN window_function      VARCHAR(16) DEFAULT 'NONE', -- NONE/YOY/MOM/YTD/ROLLING_AVG

-- 维度与粒度
ADD COLUMN dimension_fields     JSONB,         -- [{field,display_name,control_type}]
ADD COLUMN date_column          VARCHAR(200),
ADD COLUMN time_grain           VARCHAR(16) DEFAULT 'MONTH',
ADD COLUMN granularity          VARCHAR(64),

-- 数据绑定
ADD COLUMN source_table         VARCHAR(200),
ADD COLUMN join_config          JSONB,         -- [{table,alias,on,type}]
ADD COLUMN source_layer         VARCHAR(8) DEFAULT 'ODS',
ADD COLUMN target_layer         VARCHAR(8) DEFAULT 'ADS',
ADD COLUMN target_model_name    VARCHAR(200),

-- 业务属性
ADD COLUMN unit                 VARCHAR(32),
ADD COLUMN precision_scale      INTEGER DEFAULT 2,
ADD COLUMN threshold_min        NUMERIC,
ADD COLUMN threshold_max        NUMERIC,
ADD COLUMN direction            VARCHAR(16) DEFAULT 'HIGHER_BETTER',
ADD COLUMN business_owner       VARCHAR(64),
ADD COLUMN data_privacy         VARCHAR(16) DEFAULT 'INTERNAL',

-- LLM 预留
ADD COLUMN llm_generated        BOOLEAN DEFAULT false,
ADD COLUMN llm_confidence       REAL,
ADD COLUMN llm_source_ref       TEXT,
ADD COLUMN human_verified       BOOLEAN DEFAULT false,

-- 管理
ADD COLUMN domain               VARCHAR(32),
ADD COLUMN icon                 VARCHAR(64),
ADD COLUMN display_order        INTEGER DEFAULT 0,
ADD COLUMN template_id          UUID
```

### 索引
```sql
CREATE INDEX idx_gid_domain ON gov_indicator_definition(domain);
CREATE INDEX idx_gid_template ON gov_indicator_definition(template_id);
```

## 影响范围
| 文件 | 改动 |
|------|------|
| 新增 Liquibase changelog | ALTER TABLE 添加字段 |

## 验证
- [ ] 迁移成功，现有数据不受影响
- [ ] 所有新字段默认值正确
