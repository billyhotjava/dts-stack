{{ config({"severity":"Warn","tags":[]}) }}
{{ test_accepted_values(column_name="period_type", model=get_where_subquery(ref('stg_fin__own_fund')), values=["opening","increase","usage","balance"]) }}