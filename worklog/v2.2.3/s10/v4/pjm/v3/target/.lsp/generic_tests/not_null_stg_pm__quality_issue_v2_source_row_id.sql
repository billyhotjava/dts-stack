{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="source_row_id", model=get_where_subquery(ref('stg_pm__quality_issue_v2'))) }}