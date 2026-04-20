{{ config({"severity":"Warn","tags":[]}) }}
{{ test_accepted_values(column_name="usage_rate_level", model=get_where_subquery(ref('biz_ads_own_fund_kpi')), values=["healthy","warning","danger"]) }}