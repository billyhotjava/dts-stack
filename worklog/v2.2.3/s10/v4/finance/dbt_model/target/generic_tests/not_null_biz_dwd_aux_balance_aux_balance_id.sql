{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="aux_balance_id", model=get_where_subquery(ref('biz_dwd_aux_balance'))) }}