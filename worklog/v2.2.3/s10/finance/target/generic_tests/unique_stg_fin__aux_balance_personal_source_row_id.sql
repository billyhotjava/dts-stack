{{ config({"severity":"Warn","tags":[]}) }}
{{ test_unique(column_name="source_row_id", model=get_where_subquery(ref('stg_fin__aux_balance_personal'))) }}