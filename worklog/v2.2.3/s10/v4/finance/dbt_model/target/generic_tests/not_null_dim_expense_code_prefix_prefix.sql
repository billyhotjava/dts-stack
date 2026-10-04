{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="prefix", model=get_where_subquery(ref('dim_expense_code_prefix'))) }}