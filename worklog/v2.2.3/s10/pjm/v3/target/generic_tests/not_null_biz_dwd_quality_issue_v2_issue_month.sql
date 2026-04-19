{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="issue_month", model=get_where_subquery(ref('biz_dwd_quality_issue_v2'))) }}