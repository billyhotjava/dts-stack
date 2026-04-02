-- ============================================================
-- 卡片名称：按费用类别分布（矩形树图）
-- 用途：按科目编号前缀归类，展示各费用大类的余额占比
-- 所属大屏：F2 辅助余额大屏
-- 数据表：biz_dwd_aux_balance（DWD 层）
-- ============================================================

SELECT
    expense_category            -- 费用类别（原材料/设备、外协/服务、折旧、检测试验、设计咨询、租赁、培训、其他）
        AS label,
    SUM(balance)                -- 该类别余额合计（元）
        AS value
FROM biz_dwd_aux_balance
GROUP BY expense_category       -- 按派生的费用类别字段聚合
ORDER BY value DESC;
