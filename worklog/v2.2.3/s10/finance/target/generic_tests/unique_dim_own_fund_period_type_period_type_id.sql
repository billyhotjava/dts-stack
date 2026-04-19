{{ config({"severity":"Warn","tags":[]}) }}
{{ test_unique(column_name="period_type_id", model=get_where_subquery(ref('dim_own_fund_period_type'))) }}