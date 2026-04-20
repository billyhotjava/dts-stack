{{ config({"severity":"Warn","tags":[]}) }}
{{ test_unique(column_name="source_row_id", model=get_where_subquery(ref('stg_pm__project_subject_domain_v2'))) }}