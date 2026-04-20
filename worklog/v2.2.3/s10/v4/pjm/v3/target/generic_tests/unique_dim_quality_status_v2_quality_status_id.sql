{{ config({"severity":"Warn","tags":[]}) }}
{{ test_unique(column_name="quality_status_id", model=get_where_subquery(ref('dim_quality_status_v2'))) }}