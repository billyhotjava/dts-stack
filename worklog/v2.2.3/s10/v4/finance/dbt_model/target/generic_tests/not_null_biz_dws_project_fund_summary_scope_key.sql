{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="scope_key", model=get_where_subquery(ref('biz_dws_project_fund_summary'))) }}