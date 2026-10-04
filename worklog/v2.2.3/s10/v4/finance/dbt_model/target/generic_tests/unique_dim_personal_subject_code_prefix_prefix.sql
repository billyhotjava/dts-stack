{{ config({"severity":"Warn","tags":[]}) }}
{{ test_unique(column_name="prefix", model=get_where_subquery(ref('dim_personal_subject_code_prefix'))) }}