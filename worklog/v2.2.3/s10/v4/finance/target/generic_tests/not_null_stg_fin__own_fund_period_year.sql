{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="period_year", model=get_where_subquery(ref('stg_fin__own_fund'))) }}