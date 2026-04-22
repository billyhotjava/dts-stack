{{ config({"severity":"Warn","tags":[]}) }}
{{ test_accepted_values(column_name="code", model=get_where_subquery(ref('dim_project_status')), values=["在研","支出待处理","已完成待收款","已完成审计"]) }}