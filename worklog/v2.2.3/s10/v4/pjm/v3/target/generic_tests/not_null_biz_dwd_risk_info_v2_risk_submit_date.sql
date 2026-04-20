{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="risk_submit_date", model=get_where_subquery(ref('biz_dwd_risk_info_v2'))) }}