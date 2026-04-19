{{ config({"severity":"Warn","tags":[]}) }}
{{ test_accepted_values(column_name="period_type", model=get_where_subquery(ref('biz_dwd_own_fund')), values=["opening","increase","usage","balance"]) }}