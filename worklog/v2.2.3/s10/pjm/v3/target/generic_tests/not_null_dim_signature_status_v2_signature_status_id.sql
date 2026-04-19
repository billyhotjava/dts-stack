{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="signature_status_id", model=get_where_subquery(ref('dim_signature_status_v2'))) }}