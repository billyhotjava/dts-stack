{% test unique_combination(model, combination_of_columns) %}

SELECT
  {% for column_name in combination_of_columns %}
  {{ column_name }}{% if not loop.last %},{% endif %}
  {% endfor %}
FROM {{ model }}
GROUP BY
  {% for column_name in combination_of_columns %}
  {{ column_name }}{% if not loop.last %},{% endif %}
  {% endfor %}
HAVING COUNT(*) > 1

{% endtest %}
