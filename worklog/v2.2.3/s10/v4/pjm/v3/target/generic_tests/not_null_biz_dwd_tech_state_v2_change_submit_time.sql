{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="change_submit_time", model=get_where_subquery(ref('biz_dwd_tech_state_v2'))) }}