{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="source_row_id", model=get_where_subquery(ref('stg_fin__aux_balance'))) }}