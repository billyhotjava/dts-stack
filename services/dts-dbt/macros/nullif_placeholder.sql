{% macro nullif_placeholder(expr) -%}
(
  case
    when {{ expr }} is null then null
    when upper(btrim(cast({{ expr }} as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast({{ expr }} as text)), '')
  end
)
{%- endmacro %}
