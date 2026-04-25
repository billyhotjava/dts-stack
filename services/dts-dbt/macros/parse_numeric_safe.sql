{% macro parse_numeric_safe(expr) -%}
(
  case
    when {{ nullif_placeholder(expr) }} is null then null

    -- 去除千分位逗号后，标准数值: 123, -456.78
    when regexp_replace({{ nullif_placeholder(expr) }}, ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace({{ nullif_placeholder(expr) }}, ',', '', 'g')::numeric

    -- 百分号: 72.4% → 72.4（保留原始数值，不除以100）
    when regexp_replace({{ nullif_placeholder(expr) }}, '[,%]', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace({{ nullif_placeholder(expr) }}, '[,%]', '', 'g')::numeric

    -- 带中文单位: 28天, 36.8亿, 12个 → 提取前面的数值
    when regexp_replace({{ nullif_placeholder(expr) }}, '[^\d.\-]', '', 'g') ~ '^-?\d+(\.\d+)?$'
         and {{ nullif_placeholder(expr) }} ~ '^\s*-?\d+\.?\d*\s*[^\d.\s]'
      then regexp_replace({{ nullif_placeholder(expr) }}, '[^\d.\-]', '', 'g')::numeric

    -- 科学计数法: 1.23E+05, 1.23e5, 1.23E-3
    when upper({{ nullif_placeholder(expr) }}) ~ '^-?\d+(\.\d+)?[Ee][+\-]?\d+$'
      then {{ nullif_placeholder(expr) }}::double precision::numeric

    -- 全角数字转半角: ０１２３ → 0123
    when translate({{ nullif_placeholder(expr) }}, '０１２３４５６７８９．', '0123456789.') ~ '^-?\d+(\.\d+)?$'
      then translate({{ nullif_placeholder(expr) }}, '０１２３４５６７８９．', '0123456789.')::numeric

    else null
  end
)
{%- endmacro %}
