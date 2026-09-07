{% materialization dts_schema_only, adapter='postgres' %}
  {% set target_relation = this.incorporate(type='table') %}
  {% set existing = adapter.get_relation(database=this.database, schema=this.schema, identifier=this.identifier) %}
  {% if existing is not none %}
    {{ exceptions.raise_compiler_error('MODEL_SCHEMA_TARGET_ALREADY_EXISTS: ' ~ target_relation) }}
  {% endif %}
  {% set columns = config.require('dts_columns') %}
  {% set primary_keys = config.get('dts_primary_keys', []) %}
  {% if columns | length == 0 %}
    {{ exceptions.raise_compiler_error('MODEL_SCHEMA_FIELDS_REQUIRED') }}
  {% endif %}
  {% call statement('main') %}
    create table {{ target_relation }} (
      {% for column in columns %}
        {{ adapter.quote(column['name']) }} {{ column['data_type'] }}{% if not column['nullable'] %} not null{% endif %}{% if not loop.last or primary_keys | length > 0 %},{% endif %}
      {% endfor %}
      {% if primary_keys | length > 0 %}
        primary key ({% for key in primary_keys %}{{ adapter.quote(key) }}{% if not loop.last %}, {% endif %}{% endfor %})
      {% endif %}
    )
  {% endcall %}
  {% do adapter.commit() %}
  {{ return({'relations': [target_relation]}) }}
{% endmaterialization %}
