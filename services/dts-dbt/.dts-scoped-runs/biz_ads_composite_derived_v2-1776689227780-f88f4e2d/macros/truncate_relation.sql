{% macro truncate_relation(schema_name, identifier, database_name=None) -%}
    {% set database_value = database_name if database_name is not none and database_name | trim else target.database %}
    {% set relation = adapter.get_relation(
        database=database_value,
        schema=schema_name,
        identifier=identifier
    ) %}

    {% if relation is none %}
        {{ log("truncate_relation skipped: relation not found for " ~ schema_name ~ "." ~ identifier, info=True) }}
        {{ return("SKIPPED") }}
    {% endif %}

    {% if relation.type is not none and relation.type | upper == 'VIEW' %}
        {{ exceptions.raise_compiler_error("当前产出 relation 为视图，不支持清空。请改用「重建产出表」。") }}
    {% endif %}

    {% set sql -%}
        truncate table {{ relation }}
    {%- endset %}
    {% do run_query(sql) %}
    {{ log("truncate_relation executed for " ~ relation, info=True) }}
    {{ return("OK") }}
{%- endmacro %}
