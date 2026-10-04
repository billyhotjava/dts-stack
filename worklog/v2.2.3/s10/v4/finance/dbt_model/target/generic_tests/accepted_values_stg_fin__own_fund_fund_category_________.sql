{{ config({"severity":"Warn","tags":[]}) }}
{{ test_accepted_values(column_name="fund_category", model=get_where_subquery(ref('stg_fin__own_fund')), values=["事业基金","职工福利基金","安全生产基金"]) }}