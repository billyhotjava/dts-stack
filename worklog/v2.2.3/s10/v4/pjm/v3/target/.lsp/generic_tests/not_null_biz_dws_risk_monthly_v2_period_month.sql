{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="period_month", model=get_where_subquery(ref('biz_dws_risk_monthly_v2'))) }}