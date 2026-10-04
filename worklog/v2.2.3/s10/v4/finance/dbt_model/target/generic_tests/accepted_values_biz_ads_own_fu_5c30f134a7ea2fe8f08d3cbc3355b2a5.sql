{{ config({"severity":"Warn","tags":[]}) }}
{{ test_accepted_values(column_name="growth_direction", model=get_where_subquery(ref('biz_ads_own_fund_kpi')), values=["growth","decline","flat"]) }}