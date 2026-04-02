-- biz_dwd_aux_balance.sql
-- DWD层：辅助余额明细，根据科目编号前缀添加费用类别(expense_category)
-- 源表: public.ods_finance_aux_balance
-- 注意: CSV导入后所有列为TEXT，此处对balance转型为NUMERIC

SELECT
    subject_code,
    subject_name,
    dept_name,
    contract_name,
    balance::NUMERIC(15,2) AS balance,
    CASE
        WHEN subject_code LIKE '5001%' THEN '原材料/设备'
        WHEN subject_code LIKE '5101%' THEN '外协/服务'
        WHEN subject_code LIKE '5201%' THEN '折旧'
        WHEN subject_code LIKE '5301%' THEN '检测试验'
        WHEN subject_code LIKE '5401%' THEN '设计咨询'
        WHEN subject_code LIKE '5501%' THEN '租赁'
        WHEN subject_code LIKE '5601%' THEN '培训'
        ELSE '其他'
    END AS expense_category
FROM public.ods_finance_aux_balance
