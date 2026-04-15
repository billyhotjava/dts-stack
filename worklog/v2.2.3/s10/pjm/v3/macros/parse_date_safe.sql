{% macro parse_date_safe(expr) -%}
public.dts_try_to_date(cast({{ expr }} as text))
{%- endmacro %}
