{{ config({"severity":"Warn","tags":[]}) }}
{{ test_unique(column_name="risk_level_id", model=get_where_subquery(ref('dim_risk_level_v2'))) }}