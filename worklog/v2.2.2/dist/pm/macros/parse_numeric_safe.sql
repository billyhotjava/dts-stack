{% macro parse_numeric_safe(expr) -%}
(
  case
    when {{ nullif_placeholder(expr) }} is null then null
    when regexp_replace({{ nullif_placeholder(expr) }}, ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace({{ nullif_placeholder(expr) }}, ',', '', 'g')::numeric
    else null
  end
)
{%- endmacro %}
