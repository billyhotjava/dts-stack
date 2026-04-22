{{ config({"severity":"Warn","tags":[]}) }}
{{ test_not_null(column_name="year_num", model=get_where_subquery(ref('biz_dwd_own_fund'))) }}