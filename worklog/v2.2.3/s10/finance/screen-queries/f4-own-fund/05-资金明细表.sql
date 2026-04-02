-- ============================================================
-- 卡片名称：自有资金明细表
-- 用途：展示自有资金全量明细（每年4行：年初/增加/使用/余额）
-- 所属大屏：F4 自有资金大屏
-- 数据表：biz_dwd_own_fund（DWD 层）
-- ============================================================

SELECT
    year_period,                -- 年度期间文本（如 "2026年初"、"2026年预计增加"）
    period_year,                -- 年度数值（派生，如 2026）
    period_type,                -- 期间类型（派生：opening/increase/usage/balance）
    period_sort,                -- 期间排序号（派生：1=年初 2=增加 3=使用 4=余额）
    career_fund,                -- 事业基金（万元）
    career_note,                -- 事业基金备注
    deprec_fund,                -- 折旧基金（万元）
    deprec_note,                -- 折旧基金备注
    welfare_fund,               -- 职工福利基金（万元）
    safety_fund,                -- 安全生产基金（万元）
    total                       -- 合计（四类基金之和，万元）
FROM biz_dwd_own_fund
ORDER BY period_year, period_sort;
