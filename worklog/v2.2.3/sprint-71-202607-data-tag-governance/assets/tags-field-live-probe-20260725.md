# `CatalogDataset.tags` 现网只读探查（2026-07-25）

## 结论

- `catalog_dataset` 总记录数：`503`
- `tags` 非空白记录数：`0`
- 逗号、全角逗号、分号、全角分号样本数：均为 `0`
- 按 `[,，;；\s]+` 解析后的 token 总数、去重 token 数：均为 `0`

因此当前环境没有可用于推断真实分隔符的非空样本。迁移工具仍需用受控
fixture 覆盖逗号、全角逗号、分号、全角分号和空白混用，但不得把 fixture
表述为现网数据。

## 探查方式

在运行中的 `dts_platform` PostgreSQL 数据库内开启只读事务，执行：

```sql
BEGIN READ ONLY;

SELECT
    count(*) AS dataset_count,
    count(*) FILTER (WHERE nullif(btrim(tags), '') IS NOT NULL)
        AS nonblank_tags_count
FROM catalog_dataset;

SELECT
    count(*) FILTER (WHERE tags LIKE '%,%') AS comma_rows,
    count(*) FILTER (WHERE tags LIKE '%;%') AS semicolon_rows,
    count(*) FILTER (WHERE tags LIKE '%，%') AS fullwidth_comma_rows,
    count(*) FILTER (WHERE tags LIKE '%；%') AS fullwidth_semicolon_rows
FROM catalog_dataset
WHERE nullif(btrim(tags), '') IS NOT NULL;

WITH tokens AS (
    SELECT btrim(value) AS value
    FROM catalog_dataset
    CROSS JOIN LATERAL regexp_split_to_table(tags, E'[,，;；\\s]+') value
    WHERE nullif(btrim(tags), '') IS NOT NULL
)
SELECT
    count(*) FILTER (WHERE value <> '') AS token_occurrences,
    count(DISTINCT value) FILTER (WHERE value <> '') AS distinct_tokens
FROM tokens;

ROLLBACK;
```

实际输出：

```text
dataset_count | nonblank_tags_count
--------------+--------------------
503           | 0

comma_rows | semicolon_rows | fullwidth_comma_rows | fullwidth_semicolon_rows
-----------+----------------+----------------------+-------------------------
0          | 0              | 0                    | 0

token_occurrences | distinct_tokens
------------------+----------------
0                 | 0
```

探查全程为只读事务，最后显式回滚；未修改运行库。
