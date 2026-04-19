{{ config({"severity":"Warn","tags":[]}) }}
{{ test_unique(column_name="dept_name", model=get_where_subquery(ref('biz_dws_aux_balance_by_dept'))) }}