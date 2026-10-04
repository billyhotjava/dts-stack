{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="employee_dept", model=get_where_subquery(ref('biz_dws_aux_balance_personal_by_dept'))) }}