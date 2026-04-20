{{ config({"severity":"Warn","tags":[]}) }}
{{ test_unique(column_name="subject_category_id", model=get_where_subquery(ref('dim_personal_subject_category'))) }}