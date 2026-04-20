{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="plan_month", model=get_where_subquery(ref('biz_dws_progress_monthly_v2'))) }}