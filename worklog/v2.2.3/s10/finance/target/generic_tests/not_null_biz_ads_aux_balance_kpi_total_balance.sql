{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="total_balance", model=get_where_subquery(ref('biz_ads_aux_balance_kpi'))) }}