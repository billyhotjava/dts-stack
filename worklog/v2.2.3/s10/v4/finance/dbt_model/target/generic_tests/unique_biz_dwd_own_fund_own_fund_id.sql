{{ config({"severity":"Warn","tags":[]}) }}
{{ test_unique(column_name="own_fund_id", model=get_where_subquery(ref('biz_dwd_own_fund'))) }}