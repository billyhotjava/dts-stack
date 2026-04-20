{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="risk_id", model=get_where_subquery(ref('biz_dwd_risk_info_v2'))) }}