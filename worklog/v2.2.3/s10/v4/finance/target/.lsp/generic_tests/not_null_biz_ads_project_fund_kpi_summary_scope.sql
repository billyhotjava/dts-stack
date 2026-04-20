{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="summary_scope", model=get_where_subquery(ref('biz_ads_project_fund_kpi'))) }}