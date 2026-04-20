{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="change_category_id", model=get_where_subquery(ref('dim_change_category_v2'))) }}