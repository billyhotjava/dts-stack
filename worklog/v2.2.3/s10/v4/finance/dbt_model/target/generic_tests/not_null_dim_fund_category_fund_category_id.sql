{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="fund_category_id", model=get_where_subquery(ref('dim_fund_category'))) }}