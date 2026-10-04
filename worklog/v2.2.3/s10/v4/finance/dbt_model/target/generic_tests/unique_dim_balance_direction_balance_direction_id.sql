{{ config({"severity":"Warn","tags":[]}) }}
{{ test_unique(column_name="balance_direction_id", model=get_where_subquery(ref('dim_balance_direction'))) }}