{{ config({"severity":"Warn","tags":[]}) }}
{{ test_unique(column_name="period_year", model=get_where_subquery(ref('biz_dws_own_fund_yearly'))) }}