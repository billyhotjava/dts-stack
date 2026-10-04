{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="net_balance", model=get_where_subquery(ref('biz_ads_aux_balance_personal_kpi'))) }}