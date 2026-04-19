{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="expense_category_id", model=get_where_subquery(ref('dim_expense_category'))) }}