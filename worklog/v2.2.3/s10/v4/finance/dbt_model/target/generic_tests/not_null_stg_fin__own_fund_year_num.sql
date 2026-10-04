{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="year_num", model=get_where_subquery(ref('stg_fin__own_fund'))) }}