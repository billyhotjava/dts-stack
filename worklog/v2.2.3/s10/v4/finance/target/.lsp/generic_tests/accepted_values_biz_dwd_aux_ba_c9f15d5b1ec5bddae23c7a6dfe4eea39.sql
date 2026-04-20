{{ config({"severity":"Warn","tags":[]}) }}
{{ test_accepted_values(column_name="subject_category", model=get_where_subquery(ref('biz_dwd_aux_balance_personal')), values=["其他应收-借款","应付职工薪酬","其他"]) }}