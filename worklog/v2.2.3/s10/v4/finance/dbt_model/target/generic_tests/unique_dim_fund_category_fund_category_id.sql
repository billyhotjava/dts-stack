{{ config({"severity":"Warn","tags":[]}) }}
{{ test_unique(column_name="fund_category_id", model=get_where_subquery(ref('dim_fund_category'))) }}