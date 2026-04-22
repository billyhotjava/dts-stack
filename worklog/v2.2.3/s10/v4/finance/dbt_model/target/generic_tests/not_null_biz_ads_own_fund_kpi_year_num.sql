{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="year_num", model=get_where_subquery(ref('biz_ads_own_fund_kpi'))) }}