{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="period_type_id", model=get_where_subquery(ref('dim_own_fund_period_type'))) }}