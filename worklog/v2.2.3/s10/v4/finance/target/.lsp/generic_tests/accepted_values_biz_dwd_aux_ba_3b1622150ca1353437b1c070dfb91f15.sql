{{ config({"severity":"Warn","tags":[]}) }}
{{ test_accepted_values(column_name="balance_direction", model=get_where_subquery(ref('biz_dwd_aux_balance_personal')), values=["debit","credit","zero"]) }}