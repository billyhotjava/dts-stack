-- ============================================================
-- 卡片名称：年末资金构成（环形图/饼图）
-- 用途：展示指定年度年末余额中四类基金的占比
-- 所属大屏：F4 自有资金大屏
-- 数据表：biz_dws_own_fund_yearly（DWS 层）
-- ============================================================

SELECT
    career_balance,             -- 事业基金年末余额（万元）
    deprec_balance,             -- 折旧基金年末余额（万元）
    welfare_balance,            -- 职工福利基金年末余额（万元）
    safety_balance              -- 安全生产基金年末余额（万元）
FROM biz_dws_own_fund_yearly
WHERE period_year = :selected_year;
