{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="plan_date", model=get_where_subquery(ref('biz_dwd_project_node_v2'))) }}