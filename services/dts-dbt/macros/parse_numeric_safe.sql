{% macro parse_numeric_safe(expr) -%}
(
  case
    when {{ nullif_placeholder(expr) }} is null then null
    when regexp_replace({{ nullif_placeholder(expr) }}, ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace({{ nullif_placeholder(expr) }}, ',', '', 'g')::numeric
    when regexp_replace({{ nullif_placeholder(expr) }}, '[,%]', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace({{ nullif_placeholder(expr) }}, '[,%]', '', 'g')::numeric
    when regexp_replace({{ nullif_placeholder(expr) }}, '[^\d.\-]', '', 'g') ~ '^-?\d+(\.\d+)?$'
         and {{ nullif_placeholder(expr) }} ~ '^\s*-?\d+\.?\d*\s*[^\d.\s]'
      then regexp_replace({{ nullif_placeholder(expr) }}, '[^\d.\-]', '', 'g')::numeric
    when upper({{ nullif_placeholder(expr) }}) ~ '^-?\d+(\.\d+)?[Ee][+\-]?\d+$'
      then {{ nullif_placeholder(expr) }}::double precision::numeric
    when translate({{ nullif_placeholder(expr) }}, '０１２３４５６７８９．', '0123456789.') ~ '^-?\d+(\.\d+)?$'
      then translate({{ nullif_placeholder(expr) }}, '０１２３４５６７８９．', '0123456789.')::numeric
    else null
  end
)
{%- endmacro %}
