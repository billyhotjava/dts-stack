{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="subject_code", model=get_where_subquery(ref('stg_fin__aux_balance'))) }}