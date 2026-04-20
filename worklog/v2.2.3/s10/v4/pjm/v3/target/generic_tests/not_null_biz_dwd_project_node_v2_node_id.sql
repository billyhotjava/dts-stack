{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="node_id", model=get_where_subquery(ref('biz_dwd_project_node_v2'))) }}