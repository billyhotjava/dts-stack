{% macro parse_date_safe(expr) -%}
public.dts_try_to_date(cast({{ nullif_placeholder(expr) }} as text))
{%- endmacro %}
