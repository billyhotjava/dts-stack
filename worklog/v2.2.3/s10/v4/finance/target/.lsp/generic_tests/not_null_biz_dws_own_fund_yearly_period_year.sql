{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="period_year", model=get_where_subquery(ref('biz_dws_own_fund_yearly'))) }}