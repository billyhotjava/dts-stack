{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="project_id", model=get_where_subquery(ref('stg_fin__project_fund'))) }}