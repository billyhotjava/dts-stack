-- ============================================================
-- 卡片名称：KPI 个人余额总览
-- 用途：个人辅助余额大屏顶部 5 个 KPI 指标卡片
-- 所属大屏：F3 个人辅助余额大屏
-- 数据表：biz_ads_aux_balance_personal_kpi（ADS 层）
-- ============================================================

SELECT
    net_balance,                -- 净余额（正=公司净债权，负=公司净债务）
    debit_total,                -- 借方合计（正数余额之和，应收/借款总额）
    credit_total,               -- 贷方合计（负数余额绝对值之和，应付/代扣总额）
    employee_count,             -- 涉及职工数（去重）
    subject_count               -- 涉及科目数（去重）
FROM biz_ads_aux_balance_personal_kpi;
