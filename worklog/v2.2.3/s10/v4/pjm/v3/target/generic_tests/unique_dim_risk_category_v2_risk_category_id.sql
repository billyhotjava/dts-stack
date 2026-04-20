{{ config({"severity":"Warn","tags":[]}) }}
{{ test_unique(column_name="risk_category_id", model=get_where_subquery(ref('dim_risk_category_v2'))) }}