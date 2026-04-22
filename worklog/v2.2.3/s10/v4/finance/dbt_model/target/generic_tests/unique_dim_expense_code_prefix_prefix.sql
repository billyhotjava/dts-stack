{{ config({"severity":"Warn","tags":[]}) }}
{{ test_unique(column_name="prefix", model=get_where_subquery(ref('dim_expense_code_prefix'))) }}