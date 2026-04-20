{{ config({"severity":"Warn","tags":[]}) }}
{{ test_accepted_values(column_name="risk_category", model=get_where_subquery(ref('biz_dwd_risk_info_v2')), values=["技术","进度","成本","设计","质量","其他"]) }}