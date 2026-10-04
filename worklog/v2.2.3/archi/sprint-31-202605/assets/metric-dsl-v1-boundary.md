# 指标 DSL v1 边界

## 支持公式

| 类型 | 说明 |
|---|---|
| sum | 数值求和 |
| count | 计数 |
| count_distinct | 去重计数 |
| avg / min / max | 常规聚合 |
| count_if | 条件计数 |
| sum_if | 条件求和 |
| ratio | 分子/分母，必须处理除零 |
| case_when | 受控分支表达式 |
| date_trunc | 时间粒度归一 |

## 安全边界

- 不接受合作方任意 SQL。
- 字段引用必须来自 platform asset schema contract。
- 预览必须带 limit、timeout 和权限检查。
- ratio 分母为 0 时返回 null 或显式错误，不允许静默给 0。
- 生成的 DWS/ADS/dbt artifact 只是候选，最终发布必须走 platform release gate。
