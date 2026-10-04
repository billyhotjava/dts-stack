{{ config({"severity":"Warn","tags":[]}) }}
{{ test_unique(column_name="fund_source_id", model=get_where_subquery(ref('dim_fund_source'))) }}