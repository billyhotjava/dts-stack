{{ config({"severity":"Warn","tags":[]}) }}
{{ test_accepted_values(column_name="fund_category_code", model=get_where_subquery(ref('biz_dwd_own_fund')), values=["事业基金","职工福利基金","安全生产基金"]) }}