{% macro nullif_placeholder(expr) -%}
(
  case
    when {{ expr }} is null then null
    when upper(btrim(cast({{ expr }} as text))) in ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      then null
    else nullif(btrim(cast({{ expr }} as text)), '')
  end
)
{%- endmacro %}
