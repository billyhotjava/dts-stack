{{ config({"severity":"Warn","tags":[]}) }}
{{ test_unique(column_name="employee_dept", model=get_where_subquery(ref('biz_dws_aux_balance_personal_by_dept'))) }}