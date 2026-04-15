-- ============================================================
-- v2 二次指标 (Tier 2 / Derived) 查询语句 — 参数化版本
-- 支持大屏 5 个全局筛选器
-- 关键: 二次指标从 DWS 层实时计算（非读 ADS derived 表）
--       这样 project/dept 筛选器才能生效
-- ============================================================


-- ═══════════════════════════════════════════════════════════
-- 【进度域】二次指标 — 6 个
-- 从 biz_dws_progress_monthly_v2 实时聚合计算
-- ═══════════════════════════════════════════════════════════

-- card-progress-derived: 进度域二次指标
-- 用于: S1 按时完成率/健康度, S2 按时率/超期率/异常率/健康度
WITH p AS (
    SELECT
        SUM(s.due_cnt) AS due,
        SUM(s.on_time_cnt) AS on_time,
        SUM(s.incomplete_cnt) AS incomplete,
        SUM(s.incomplete_high_risk_cnt) AS high_risk_incomplete,
        SUM(s.milestone_on_time_cnt) AS ms_on_time,
        SUM(s.milestone_total_cnt) AS ms_total,
        SUM(s.abnormal_pending_non_general_cnt) AS abnormal,
        SUM(s.overdue_incomplete_unchanged_non_general_cnt) AS overdue_unchanged,
        SUM(s.overdue_incomplete_changed_non_general_cnt) AS overdue_changed
    FROM biz_dws_progress_monthly_v2 s
    WHERE s.plan_month >= to_char({{dateFrom}}::date, 'YYYY-MM')
      AND s.plan_month <= to_char({{dateTo}}::date, 'YYYY-MM')
      AND ({{projectNo}} IS NULL OR {{projectNo}} = '' OR s.project_no ILIKE '%' || {{projectNo}} || '%')
)
SELECT
    -- 二次#1 节点按时完成率 (预警 <80%)
    CASE WHEN p.due = 0 THEN 0
         ELSE ROUND(p.on_time::numeric / p.due::numeric * 100, 2)
    END AS "节点按时完成率",

    -- 二次#2 节点超期率 (预警 >15%)
    CASE WHEN p.due = 0 THEN 0
         ELSE ROUND((p.overdue_unchanged + p.overdue_changed)::numeric / p.due::numeric * 100, 2)
    END AS "节点超期率",

    -- 二次#3 节点不正常率 (预警 >20%)
    CASE WHEN p.due = 0 THEN 0
         ELSE ROUND((p.abnormal + p.overdue_unchanged)::numeric / p.due::numeric * 100, 2)
    END AS "节点不正常率",

    -- 二次#4 里程碑按时完成率 (预警 <90%)
    CASE WHEN p.ms_total = 0 THEN 0
         ELSE ROUND(p.ms_on_time::numeric / p.ms_total::numeric * 100, 2)
    END AS "里程碑按时完成率",

    -- 二次#5 高风险未完成占比 (预警 >30%)
    CASE WHEN p.incomplete = 0 THEN 0
         ELSE ROUND(p.high_risk_incomplete::numeric / p.incomplete::numeric * 100, 2)
    END AS "高风险未完成占比",

    -- 二次#6 进度健康度 (预警 <70)
    ROUND(
        (CASE WHEN p.due = 0 THEN 0 ELSE p.on_time::numeric / p.due::numeric * 100 END) * 0.4
      + (CASE WHEN p.ms_total = 0 THEN 0 ELSE p.ms_on_time::numeric / p.ms_total::numeric * 100 END) * 0.3
      + (100 - CASE WHEN p.due = 0 THEN 0 ELSE (p.abnormal + p.overdue_unchanged)::numeric / p.due::numeric * 100 END) * 0.3
    , 2) AS "进度健康度"
FROM p;


-- ═══════════════════════════════════════════════════════════
-- 【质量域】二次指标 — 5 个
-- 从 biz_dws_quality_monthly_v2 实时聚合计算
-- ═══════════════════════════════════════════════════════════

-- card-quality-derived: 质量域二次指标
-- 用于: S1 健康度, S3 归零率/计划率/健康度, D2 KPI
WITH q AS (
    SELECT
        SUM(s.new_issue_cnt) AS new_cnt,
        SUM(s.open_issue_cnt) AS open_cnt,
        SUM(s.zero_completed_cnt) AS zero_cnt,
        SUM(s.no_zero_plan_cnt) AS no_plan_cnt,
        SUM(s.open_cat_design) AS design_cnt
    FROM biz_dws_quality_monthly_v2 s
    WHERE s.period_month >= to_char({{dateFrom}}::date, 'YYYY-MM')
      AND s.period_month <= to_char({{dateTo}}::date, 'YYYY-MM')
      AND ({{deptId}} IS NULL OR {{deptId}} = '' OR s.dept = {{deptId}})
      AND ({{projectNo}} IS NULL OR {{projectNo}} = '' OR s.project_no ILIKE '%' || {{projectNo}} || '%')
)
SELECT
    -- 二次#7 归零率 (预警 <60%)
    CASE WHEN q.new_cnt = 0 THEN 0
         ELSE ROUND(q.zero_cnt::numeric / q.new_cnt::numeric * 100, 2)
    END AS "归零率",

    -- 二次#8 现存问题比率 (预警 >50%)
    CASE WHEN q.new_cnt = 0 THEN 0
         ELSE ROUND(q.open_cnt::numeric / q.new_cnt::numeric * 100, 2)
    END AS "现存问题比率",

    -- 二次#9 归零计划提交率 (预警 <80%)
    CASE WHEN q.new_cnt = 0 THEN 0
         ELSE ROUND((q.new_cnt - q.no_plan_cnt)::numeric / q.new_cnt::numeric * 100, 2)
    END AS "归零计划提交率",

    -- 二次#10 设计类问题占比 (预警 >40%)
    CASE WHEN q.open_cnt = 0 THEN 0
         ELSE ROUND(q.design_cnt::numeric / q.open_cnt::numeric * 100, 2)
    END AS "设计类问题占比",

    -- 二次#11 质量健康度 (预警 <70)
    ROUND(
        (CASE WHEN q.new_cnt = 0 THEN 0 ELSE q.zero_cnt::numeric / q.new_cnt::numeric * 100 END) * 0.5
      + (CASE WHEN q.new_cnt = 0 THEN 0 ELSE (q.new_cnt - q.no_plan_cnt)::numeric / q.new_cnt::numeric * 100 END) * 0.3
      + (100 - CASE WHEN q.new_cnt = 0 THEN 0 ELSE q.open_cnt::numeric / q.new_cnt::numeric * 100 END) * 0.2
    , 2) AS "质量健康度"
FROM q;


-- ═══════════════════════════════════════════════════════════
-- 【技术状态域】二次指标 — 5 个
-- 从 biz_dws_tech_state_monthly_v2 实时聚合计算
-- ═══════════════════════════════════════════════════════════

-- card-tech-state-derived: 技术状态二次指标
-- 用于: S1 健康度, S4 签署率/整改率/健康度, D3 KPI
WITH t AS (
    SELECT
        SUM(s.total_change_cnt) AS total,
        SUM(s.new_cat_i) AS cat_i,
        SUM(s.order_signed_cnt) AS signed,
        SUM(s.order_unsigned_cat_i) + SUM(s.order_unsigned_cat_ii)
          + SUM(s.order_unsigned_cat_iii) AS unsigned,
        SUM(s.reform_done_i_ii) AS reform_done,
        SUM(s.reform_pending_i_ii) AS reform_pending
    FROM biz_dws_tech_state_monthly_v2 s
    WHERE s.period_month >= to_char({{dateFrom}}::date, 'YYYY-MM')
      AND s.period_month <= to_char({{dateTo}}::date, 'YYYY-MM')
      AND ({{deptId}} IS NULL OR {{deptId}} = '' OR s.dept = {{deptId}})
      AND ({{projectNo}} IS NULL OR {{projectNo}} = '' OR s.project_no ILIKE '%' || {{projectNo}} || '%')
)
SELECT
    -- 二次#12 签署完成率 (预警 <80%)
    CASE WHEN (t.signed + t.unsigned) = 0 THEN 0
         ELSE ROUND(t.signed::numeric / (t.signed + t.unsigned)::numeric * 100, 2)
    END AS "签署完成率",

    -- 二次#13 整改完成率 (预警 <80%)
    CASE WHEN (t.reform_done + t.reform_pending) = 0 THEN 0
         ELSE ROUND(t.reform_done::numeric / (t.reform_done + t.reform_pending)::numeric * 100, 2)
    END AS "整改完成率",

    -- 二次#14 I类更改占比 (预警 >30%)
    CASE WHEN t.total = 0 THEN 0
         ELSE ROUND(t.cat_i::numeric / t.total::numeric * 100, 2)
    END AS "I类更改占比",

    -- 二次#15 未签署比率 (预警 >20%)
    CASE WHEN t.total = 0 THEN 0
         ELSE ROUND(t.unsigned::numeric / t.total::numeric * 100, 2)
    END AS "未签署比率",

    -- 二次#16 技术状态健康度 (预警 <70)
    ROUND(
        (CASE WHEN (t.signed + t.unsigned) = 0 THEN 0
              ELSE t.signed::numeric / (t.signed + t.unsigned)::numeric * 100 END) * 0.4
      + (CASE WHEN (t.reform_done + t.reform_pending) = 0 THEN 0
              ELSE t.reform_done::numeric / (t.reform_done + t.reform_pending)::numeric * 100 END) * 0.4
      + (100 - CASE WHEN t.total = 0 THEN 0 ELSE t.unsigned::numeric / t.total::numeric * 100 END) * 0.2
    , 2) AS "技术状态健康度"
FROM t;


-- ═══════════════════════════════════════════════════════════
-- 【综合域】二次指标 — 2 个
-- 从上面三域的 DWS 数据联合计算
-- ═══════════════════════════════════════════════════════════

-- card-composite-health: 综合健康度 + 风险预警指数
-- 用于: S1 健康度仪表盘/雷达图, D1 风险预警指数
WITH prog AS (
    SELECT
        SUM(s.due_cnt) AS due, SUM(s.on_time_cnt) AS on_time,
        SUM(s.milestone_on_time_cnt) AS ms_on_time, SUM(s.milestone_total_cnt) AS ms_total,
        SUM(s.abnormal_pending_non_general_cnt) AS abnormal,
        SUM(s.overdue_incomplete_unchanged_non_general_cnt) AS overdue_unchanged,
        SUM(s.incomplete_milestone_cnt) AS incomplete_ms,
        SUM(s.incomplete_high_risk_cnt) AS incomplete_high
    FROM biz_dws_progress_monthly_v2 s
    WHERE s.plan_month >= to_char({{dateFrom}}::date, 'YYYY-MM')
      AND s.plan_month <= to_char({{dateTo}}::date, 'YYYY-MM')
      AND ({{projectNo}} IS NULL OR {{projectNo}} = '' OR s.project_no ILIKE '%' || {{projectNo}} || '%')
),
qual AS (
    SELECT
        SUM(s.new_issue_cnt) AS new_cnt, SUM(s.open_issue_cnt) AS open_cnt,
        SUM(s.zero_completed_cnt) AS zero_cnt, SUM(s.no_zero_plan_cnt) AS no_plan_cnt
    FROM biz_dws_quality_monthly_v2 s
    WHERE s.period_month >= to_char({{dateFrom}}::date, 'YYYY-MM')
      AND s.period_month <= to_char({{dateTo}}::date, 'YYYY-MM')
      AND ({{deptId}} IS NULL OR {{deptId}} = '' OR s.dept = {{deptId}})
      AND ({{projectNo}} IS NULL OR {{projectNo}} = '' OR s.project_no ILIKE '%' || {{projectNo}} || '%')
),
tech AS (
    SELECT
        SUM(s.order_signed_cnt) AS signed,
        SUM(s.order_unsigned_cat_i) + SUM(s.order_unsigned_cat_ii) + SUM(s.order_unsigned_cat_iii) AS unsigned,
        SUM(s.reform_done_i_ii) AS reform_done, SUM(s.reform_pending_i_ii) AS reform_pending,
        SUM(s.total_change_cnt) AS total
    FROM biz_dws_tech_state_monthly_v2 s
    WHERE s.period_month >= to_char({{dateFrom}}::date, 'YYYY-MM')
      AND s.period_month <= to_char({{dateTo}}::date, 'YYYY-MM')
      AND ({{deptId}} IS NULL OR {{deptId}} = '' OR s.dept = {{deptId}})
      AND ({{projectNo}} IS NULL OR {{projectNo}} = '' OR s.project_no ILIKE '%' || {{projectNo}} || '%')
),
scores AS (
    SELECT
        -- 进度健康分
        ROUND(
            (CASE WHEN p.due = 0 THEN 0 ELSE p.on_time::numeric / p.due::numeric * 100 END) * 0.4
          + (CASE WHEN p.ms_total = 0 THEN 0 ELSE p.ms_on_time::numeric / p.ms_total::numeric * 100 END) * 0.3
          + (100 - CASE WHEN p.due = 0 THEN 0 ELSE (p.abnormal + p.overdue_unchanged)::numeric / p.due::numeric * 100 END) * 0.3
        , 2) AS prog_score,
        -- 质量健康分
        ROUND(
            (CASE WHEN q.new_cnt = 0 THEN 0 ELSE q.zero_cnt::numeric / q.new_cnt::numeric * 100 END) * 0.5
          + (CASE WHEN q.new_cnt = 0 THEN 0 ELSE (q.new_cnt - q.no_plan_cnt)::numeric / q.new_cnt::numeric * 100 END) * 0.3
          + (100 - CASE WHEN q.new_cnt = 0 THEN 0 ELSE q.open_cnt::numeric / q.new_cnt::numeric * 100 END) * 0.2
        , 2) AS qual_score,
        -- 技术状态健康分
        ROUND(
            (CASE WHEN (t.signed + t.unsigned) = 0 THEN 0 ELSE t.signed::numeric / (t.signed + t.unsigned)::numeric * 100 END) * 0.4
          + (CASE WHEN (t.reform_done + t.reform_pending) = 0 THEN 0 ELSE t.reform_done::numeric / (t.reform_done + t.reform_pending)::numeric * 100 END) * 0.4
          + (100 - CASE WHEN t.total = 0 THEN 0 ELSE t.unsigned::numeric / t.total::numeric * 100 END) * 0.2
        , 2) AS tech_score,
        -- 风险预警原子值
        p.incomplete_ms, p.incomplete_high, p.overdue_unchanged
    FROM prog p, qual q, tech t
)
SELECT
    scores.prog_score            AS "进度健康分",
    scores.qual_score            AS "质量健康分",
    scores.tech_score            AS "技术状态健康分",

    -- 二次#17 综合健康度 (<70红, <80黄)
    ROUND(scores.prog_score * 0.4 + scores.qual_score * 0.3 + scores.tech_score * 0.3, 2)
                                 AS "项目综合健康度",

    -- 二次#18 风险预警指数 (>10黄, >20红)
    COALESCE(scores.incomplete_ms, 0) * 3
      + COALESCE(scores.incomplete_high, 0) * 2
      + COALESCE(scores.overdue_unchanged, 0) * 1
                                 AS "风险预警指数",

    -- 预警级别
    CASE WHEN ROUND(scores.prog_score * 0.4 + scores.qual_score * 0.3 + scores.tech_score * 0.3, 2) < 70 THEN 'red'
         WHEN ROUND(scores.prog_score * 0.4 + scores.qual_score * 0.3 + scores.tech_score * 0.3, 2) < 80 THEN 'yellow'
         ELSE 'green'
    END                          AS "健康预警级别",

    CASE WHEN (COALESCE(scores.incomplete_ms, 0) * 3 + COALESCE(scores.incomplete_high, 0) * 2 + COALESCE(scores.overdue_unchanged, 0)) > 20 THEN 'red'
         WHEN (COALESCE(scores.incomplete_ms, 0) * 3 + COALESCE(scores.incomplete_high, 0) * 2 + COALESCE(scores.overdue_unchanged, 0)) > 10 THEN 'yellow'
         ELSE 'green'
    END                          AS "风险预警级别"
FROM scores;
