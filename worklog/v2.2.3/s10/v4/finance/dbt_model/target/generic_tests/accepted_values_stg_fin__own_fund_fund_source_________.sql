{{ config({"severity":"Warn","tags":[]}) }}
{{ test_accepted_values(column_name="fund_source", model=get_where_subquery(ref('stg_fin__own_fund')), values=["年初","预计增加","预计使用"]) }}