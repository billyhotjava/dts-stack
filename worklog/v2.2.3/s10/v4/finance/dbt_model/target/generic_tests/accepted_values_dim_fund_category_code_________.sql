{{ config({"severity":"Warn","tags":[]}) }}
{{ test_accepted_values(column_name="code", model=get_where_subquery(ref('dim_fund_category')), values=["事业基金","职工福利基金","安全生产基金"]) }}