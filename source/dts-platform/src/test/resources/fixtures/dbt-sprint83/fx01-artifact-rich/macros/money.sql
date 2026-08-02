{% macro normalize_money(column_name) %}
cast({{ column_name }} as numeric(18, 2))
{% endmacro %}
