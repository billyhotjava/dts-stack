{{ config({"severity":"Warn","tags":[]}) }}
{{ test_unique(column_name="tech_state_id", model=get_where_subquery(ref('biz_dwd_tech_state_v2'))) }}