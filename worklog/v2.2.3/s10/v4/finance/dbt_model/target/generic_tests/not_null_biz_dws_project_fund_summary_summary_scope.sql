{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="summary_scope", model=get_where_subquery(ref('biz_dws_project_fund_summary'))) }}