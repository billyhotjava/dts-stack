-- ============================================================
-- 卡片名称：按科目类别分布（百分比条形图）
-- 用途：对比"其他应收-借款"与"应付职工薪酬"两类的规模占比
-- 所属大屏：F3 个人辅助余额大屏
-- 数据表：biz_dwd_aux_balance_personal（DWD 层）
-- ============================================================

SELECT
    subject_category            -- 科目类别（其他应收-借款 / 应付职工薪酬 / 其他）
        AS label,
    SUM(abs_balance)            -- 该类别余额绝对值合计（元）
        AS value
FROM biz_dwd_aux_balance_personal
GROUP BY subject_category       -- 按派生的科目类别字段聚合
ORDER BY value DESC;
