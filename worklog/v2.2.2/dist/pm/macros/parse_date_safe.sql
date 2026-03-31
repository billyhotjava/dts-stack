{% macro parse_date_safe(expr) -%}
(
  case
    when {{ expr }} is null then null
    when btrim(cast({{ expr }} as text)) = '' then null
    when btrim(cast({{ expr }} as text)) ~ '^\d{4}-\d{2}-\d{2}$'
      then to_date(btrim(cast({{ expr }} as text)), 'YYYY-MM-DD')
    when btrim(cast({{ expr }} as text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      then to_date(substr(btrim(cast({{ expr }} as text)), 1, 10), 'YYYY-MM-DD')
    when btrim(cast({{ expr }} as text)) ~ '^\d{4}/\d{2}/\d{2}$'
      then to_date(replace(btrim(cast({{ expr }} as text)), '/', '-'), 'YYYY-MM-DD')
    when btrim(cast({{ expr }} as text)) ~ '^\d{4}/\d{2}/\d{2}\s+.*$'
      then to_date(replace(substr(btrim(cast({{ expr }} as text)), 1, 10), '/', '-'), 'YYYY-MM-DD')
    when btrim(cast({{ expr }} as text)) ~ '^\d{4}\.\d{2}\.\d{2}$'
      then to_date(replace(btrim(cast({{ expr }} as text)), '.', '-'), 'YYYY-MM-DD')
    when btrim(cast({{ expr }} as text)) ~ '^\d{4}\.\d{2}\.\d{2}\s+.*$'
      then to_date(replace(substr(btrim(cast({{ expr }} as text)), 1, 10), '.', '-'), 'YYYY-MM-DD')
    when btrim(cast({{ expr }} as text)) ~ '^\d{8}$'
      then to_date(btrim(cast({{ expr }} as text)), 'YYYYMMDD')
    else null
  end
)
{%- endmacro %}
