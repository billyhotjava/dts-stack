# T01: Jinja2 SQL 模板集（5类）

**优先级**: P0
**状态**: READY
**依赖**: F1/T02

## 目标
创建 5 类 SQL 模板文件，供 DbtIndicatorGenerator 渲染。

## 技术设计

### 模板存放位置
`services/dts-dbt/macros/indicator_templates/` 目录下，作为字符串资源由 Java 读取。

### 5 类模板

#### 1. simple_aggregate.sql.j2（SUM/COUNT/AVG/MAX/MIN）
```sql
-- 自动生成：{{ indicator_name }} ({{ indicator_code }})
-- 生成时间：{{ generated_at }}
-- 模板：simple_aggregate

SELECT
{% for dim in dimension_fields %}
    {{ dim.field }},
{% endfor %}
    {{ time_grain }}({{ date_column }}) AS report_period,
    {{ aggregation_type }}({{ measure_field }}) AS {{ indicator_code }}
FROM {{ source_ref }}
{% for j in joins %}
{{ j.type }} JOIN {{ ref(j.table) }} AS {{ j.alias }} ON {{ j.on }}
{% endfor %}
WHERE 1=1
{% if static_filter %}
    AND {{ static_filter }}
{% endif %}
GROUP BY
{% for dim in dimension_fields %}
    {{ dim.field }},
{% endfor %}
    {{ time_grain }}({{ date_column }})
```

#### 2. ratio.sql.j2（RATIO）
```sql
SELECT
{% for dim in dimension_fields %}
    {{ dim.field }},
{% endfor %}
    {{ time_grain }}({{ date_column }}) AS report_period,
    ({{ numerator_expression }}) AS _numerator,
    ({{ denominator_expression }}) AS _denominator,
    CASE
        WHEN ({{ denominator_expression }}) = 0 THEN NULL
        ELSE ROUND(
            ({{ numerator_expression }})::numeric / ({{ denominator_expression }})::numeric,
            {{ precision_scale }}
        )
    END AS {{ indicator_code }}
FROM {{ source_ref }}
{% for j in joins %}
{{ j.type }} JOIN {{ ref(j.table) }} AS {{ j.alias }} ON {{ j.on }}
{% endfor %}
WHERE 1=1
{% if static_filter %}
    AND {{ static_filter }}
{% endif %}
GROUP BY
{% for dim in dimension_fields %}
    {{ dim.field }},
{% endfor %}
    {{ time_grain }}({{ date_column }})
```

#### 3. derived.sql.j2（衍生指标）
```sql
SELECT
{% for dim in dimension_fields %}
    {{ dim.field }},
{% endfor %}
    report_period,
    {{ expression_sql }} AS {{ indicator_code }}
FROM {{ ref(dependency_model) }}
```

#### 4. window.sql.j2（追加到基础模型）
```sql
SELECT
    *,
{% if window_function == 'YOY' %}
    LAG({{ indicator_code }}, {{ periods_back }}) OVER (
        PARTITION BY {{ partition_by }} ORDER BY report_period
    ) AS {{ indicator_code }}_prev_year,
    CASE
        WHEN LAG({{ indicator_code }}, {{ periods_back }}) OVER (...) = 0 THEN NULL
        ELSE ROUND(({{ indicator_code }} - LAG(...)) / NULLIF(LAG(...), 0), 4)
    END AS {{ indicator_code }}_yoy
{% elif window_function == 'MOM' %}
    -- 环比逻辑
{% elif window_function == 'YTD' %}
    SUM({{ indicator_code }}) OVER (
        PARTITION BY {{ partition_by }}, EXTRACT(YEAR FROM report_period)
        ORDER BY report_period
    ) AS {{ indicator_code }}_ytd
{% endif %}
FROM {{ ref(base_model) }}
```

#### 5. custom.sql.j2（直接使用 expression_sql）
```sql
-- 自动生成：{{ indicator_name }} ({{ indicator_code }})
{{ expression_sql }}
```

## 影响范围
| 文件 | 改动 |
|------|------|
| 新增 5 个 `.sql.j2` 模板文件 | 放在 macros/indicator_templates/ 或 Java resources |

## 验证
- [ ] 模板语法正确
- [ ] 占位符覆盖所有字段
