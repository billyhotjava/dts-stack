{{ config({"severity":"Warn","tags":[]}) }}
{{ test_unique(column_name="personal_balance_id", model=get_where_subquery(ref('biz_dwd_aux_balance_personal'))) }}