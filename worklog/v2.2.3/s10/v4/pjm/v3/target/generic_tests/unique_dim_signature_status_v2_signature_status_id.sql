{{ config({"severity":"Warn","tags":[]}) }}
{{ test_unique(column_name="signature_status_id", model=get_where_subquery(ref('dim_signature_status_v2'))) }}