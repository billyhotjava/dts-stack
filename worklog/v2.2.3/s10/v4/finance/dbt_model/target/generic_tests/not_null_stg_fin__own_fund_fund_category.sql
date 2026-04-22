{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="fund_category", model=get_where_subquery(ref('stg_fin__own_fund'))) }}