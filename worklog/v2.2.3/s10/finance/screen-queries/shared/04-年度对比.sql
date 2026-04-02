-- ============================================================
-- 卡片名称：年度余额对比（分组柱状图）
-- 用途：多年度各基金余额并排对比，直观展示年度增减变化
-- 所属大屏：F4 自有资金大屏
-- 数据表：biz_dws_own_fund_yearly（DWS 层）
-- ============================================================

SELECT
    period_year,                -- 年度（如 2026、2027）
    career_balance,             -- 事业基金年末余额（万元）
    deprec_balance,             -- 折旧基金年末余额（万元）
    welfare_balance,            -- 职工福利基金年末余额（万元）
    safety_balance              -- 安全生产基金年末余额（万元）
FROM biz_dws_own_fund_yearly
ORDER BY period_year;
