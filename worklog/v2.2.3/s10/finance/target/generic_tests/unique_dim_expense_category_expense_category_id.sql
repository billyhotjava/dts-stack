{{ config({"severity":"Warn","tags":[]}) }}
{{ test_unique(column_name="expense_category_id", model=get_where_subquery(ref('dim_expense_category'))) }}