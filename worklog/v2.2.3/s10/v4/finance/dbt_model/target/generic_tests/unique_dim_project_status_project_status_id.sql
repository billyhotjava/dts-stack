{{ config({"severity":"Warn","tags":[]}) }}
{{ test_unique(column_name="project_status_id", model=get_where_subquery(ref('dim_project_status'))) }}