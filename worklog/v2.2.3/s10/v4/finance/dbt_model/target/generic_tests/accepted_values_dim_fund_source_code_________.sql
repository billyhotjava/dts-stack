{{ config({"severity":"Warn","tags":[]}) }}
{{ test_accepted_values(column_name="code", model=get_where_subquery(ref('dim_fund_source')), values=["年初","预计增加","预计使用"]) }}