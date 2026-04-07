-- ============================================================
-- v2 原始指标 (Tier 1) 查询语句 — 参数化版本
-- 支持大屏 5 个全局筛选器:
--   {{dateFrom}}   统计周期-开始 (date, mm/dd/yyyy)
--   {{dateTo}}     统计周期-结束 (date, mm/dd/yyyy)
--   {{deptId}}     事业部/科室 (string, 空=全部)
--   {{projectNo}}  项目编号/名称 (string, 空=全部, 模糊匹配)
--   {{riskLevel}}  风险等级 (string, 空=全部)
--
-- 关键设计:
--   - KPI 聚合类从 DWS 层查询（保留 project/dept 维度可过滤）
--   - 明细表从 DWD 层查询
--   - 日期转换: to_char({{dateFrom}}::date, 'YYYY-MM') 匹配月份
--   - 空值处理: ({{var}} IS NULL OR {{var}} = '' OR column = {{var}})
-- ============================================================


-- ═══════════════════════════════════════════════════════════
-- 【进度域】原始指标 #1-33
-- 来源: biz_dws_progress_monthly_v2 (DWS, 保留 project_no 维度)
-- ═══════════════════════════════════════════════════════════

-- card-progress-kpi-overview: 进度域 KPI 概览（#1-33 全字段）
-- 用于: S1 项目总数/完成率/延期, S2 全部 KPI, D1 完成率/里程碑
SELECT
    -- #1-8 基础计数
    SUM(s.total_cnt)                             AS "节点总数",
    SUM(s.pending_normal_cnt)                    AS "正常待完成",
    SUM(s.due_cnt)                               AS "已到时间节点",
    SUM(s.outside_completed_cnt)                 AS "周期外完成",
    SUM(s.incomplete_cnt)                        AS "未完成总数",
    SUM(s.on_time_cnt)                           AS "按时完成数",
    SUM(s.overdue_completed_cnt)                 AS "超期完成数",
    SUM(s.completed_total_cnt)                   AS "完成总数",

    -- #9-11 完成率
    CASE WHEN (SUM(s.due_cnt) + SUM(s.outside_completed_cnt)) = 0 THEN 0
         ELSE ROUND(SUM(s.completed_total_cnt)::numeric
                  / (SUM(s.due_cnt) + SUM(s.outside_completed_cnt))::numeric * 100, 2)
    END                                          AS "完成百分比",
    CASE WHEN (SUM(s.due_cnt) + SUM(s.outside_completed_cnt)) = 0 THEN 0
         ELSE ROUND(SUM(s.on_time_cnt)::numeric
                  / (SUM(s.due_cnt) + SUM(s.outside_completed_cnt))::numeric * 100, 2)
    END                                          AS "按时完成百分比",
    CASE WHEN (SUM(s.total_cnt) + SUM(s.outside_completed_cnt)) = 0 THEN 0
         ELSE ROUND((SUM(s.overdue_completed_cnt) + SUM(s.outside_completed_cnt))::numeric
                  / (SUM(s.total_cnt) + SUM(s.outside_completed_cnt))::numeric * 100, 2)
    END                                          AS "超期完成百分比",

    -- #12-15 排除一般节点
    SUM(s.abnormal_pending_non_general_cnt)      AS "不正常待变更",
    SUM(s.overdue_incomplete_unchanged_non_general_cnt) AS "超期未完成未变更",
    SUM(s.overdue_incomplete_changed_non_general_cnt)   AS "超期未完成已变更",
    SUM(s.overdue_completed_unchanged_non_general_cnt)  AS "超期已完成未变更",

    -- #16-17 异常率/超期率
    CASE WHEN SUM(s.due_cnt) = 0 THEN 0
         ELSE ROUND((SUM(s.abnormal_pending_non_general_cnt)
                    + SUM(s.overdue_incomplete_unchanged_non_general_cnt))::numeric
                  / SUM(s.due_cnt)::numeric * 100, 2)
    END                                          AS "异常率",
    CASE WHEN SUM(s.due_cnt) = 0 THEN 0
         ELSE ROUND((SUM(s.overdue_incomplete_unchanged_non_general_cnt)
                    + SUM(s.overdue_incomplete_changed_non_general_cnt))::numeric
                  / SUM(s.due_cnt)::numeric * 100, 2)
    END                                          AS "超期率",

    -- #18-22 未完成风险/类型
    SUM(s.incomplete_high_risk_cnt)              AS "未完成高风险",
    SUM(s.incomplete_mid_risk_cnt)               AS "未完成中风险",
    SUM(s.incomplete_milestone_cnt)              AS "未完成里程碑",
    SUM(s.incomplete_major_cnt)                  AS "未完成重大",
    SUM(s.incomplete_important_cnt)              AS "未完成重要",

    -- #23-26 里程碑完成
    SUM(s.milestone_on_time_cnt)                 AS "里程碑按时完成",
    SUM(s.milestone_overdue_completed_cnt)       AS "里程碑超期完成",
    SUM(s.milestone_pending_cnt)                 AS "里程碑待完成",
    CASE WHEN (SUM(s.incomplete_milestone_cnt) + SUM(s.milestone_on_time_cnt)
             + SUM(s.milestone_overdue_completed_cnt)) = 0 THEN 0
         ELSE ROUND((SUM(s.milestone_on_time_cnt) + SUM(s.milestone_overdue_completed_cnt))::numeric
                  / (SUM(s.incomplete_milestone_cnt) + SUM(s.milestone_on_time_cnt)
                   + SUM(s.milestone_overdue_completed_cnt))::numeric * 100, 2)
    END                                          AS "里程碑完成率",

    -- #27-31 风险/类型总数
    SUM(s.high_risk_cnt)                         AS "高风险节点数",
    SUM(s.mid_risk_cnt)                          AS "中风险节点数",
    SUM(s.milestone_total_cnt)                   AS "里程碑总数",
    SUM(s.major_total_cnt)                       AS "重大节点总数",
    SUM(s.important_total_cnt)                   AS "重要节点总数",

    -- #32-33 里程碑按时/超期率
    CASE WHEN SUM(s.milestone_total_cnt) = 0 THEN 0
         ELSE ROUND(SUM(s.milestone_on_time_cnt)::numeric
                  / SUM(s.milestone_total_cnt)::numeric * 100, 2)
    END                                          AS "里程碑按时完成率",
    CASE WHEN SUM(s.milestone_total_cnt) = 0 THEN 0
         ELSE ROUND(SUM(s.milestone_overdue_completed_cnt)::numeric
                  / SUM(s.milestone_total_cnt)::numeric * 100, 2)
    END                                          AS "里程碑超期完成率"

FROM biz_dws_progress_monthly_v2 s
WHERE s.plan_month >= to_char({{dateFrom}}::date, 'YYYY-MM')
  AND s.plan_month <= to_char({{dateTo}}::date, 'YYYY-MM')
  AND ({{projectNo}} IS NULL OR {{projectNo}} = '' OR s.project_no ILIKE '%' || {{projectNo}} || '%');


-- card-progress-node-list: 进度节点明细
-- 用于: S2 延期TOP10, D1 任务明细/延期排行
SELECT
    d.node_id,
    d.project_no                 AS "项目编号",
    d.subsystem                  AS "分系统",
    d.node_task                  AS "节点任务",
    d.node_type                  AS "节点类型",
    d.owner                      AS "负责人",
    d.dept                       AS "责任科室",
    d.completion_status          AS "完成情况",
    d.risk_level                 AS "风险等级",
    d.plan_date                  AS "计划时间",
    d.actual_date                AS "实际完成时间",
    d.delay_days                 AS "延期天数",
    d.delay_expected_date        AS "预计完成时间",
    d.incomplete_reason          AS "未完成原因"
FROM biz_dwd_project_node_v2 d
WHERE d.plan_month >= to_char({{dateFrom}}::date, 'YYYY-MM')
  AND d.plan_month <= to_char({{dateTo}}::date, 'YYYY-MM')
  AND ({{deptId}} IS NULL OR {{deptId}} = '' OR d.dept = {{deptId}})
  AND ({{projectNo}} IS NULL OR {{projectNo}} = '' OR d.project_no ILIKE '%' || {{projectNo}} || '%')
  AND ({{riskLevel}} IS NULL OR {{riskLevel}} = '' OR d.risk_level = {{riskLevel}})
ORDER BY d.delay_days DESC NULLS LAST;


-- ═══════════════════════════════════════════════════════════
-- 【质量域】原始指标 #34-48
-- 来源: biz_dws_quality_monthly_v2 (DWS, 保留 project_no + dept)
-- ═══════════════════════════════════════════════════════════

-- card-quality-kpi: 质量域 KPI 概览（#34-48）
-- 用于: S1 质量分类饼图, S3 KPI/归零/分类, D2 闭环摘要
SELECT
    SUM(s.new_issue_cnt)                     AS "新增质量问题数",
    SUM(s.open_issue_cnt)                    AS "现存质量问题数",
    SUM(s.tech_zero_cnt)                     AS "已完成技术归零",
    SUM(s.mgmt_zero_cnt)                     AS "已完成管理归零",
    SUM(s.both_zero_cnt)                     AS "已完成技术和管理归零",
    SUM(s.zero_completed_cnt)                AS "已完成归零合计",
    SUM(s.no_zero_plan_cnt)                  AS "未提交归零计划",
    CASE WHEN SUM(s.new_issue_cnt) = 0 THEN 0
         ELSE ROUND(SUM(s.zero_completed_cnt)::numeric
                  / SUM(s.new_issue_cnt)::numeric * 100, 2)
    END                                      AS "归零率",
    -- 问题分类 (#41-48)
    SUM(s.open_cat_design)                   AS "设计",
    SUM(s.open_cat_process)                  AS "工艺",
    SUM(s.open_cat_management)               AS "管理",
    SUM(s.open_cat_component)                AS "元器件",
    SUM(s.open_cat_operation)                AS "操作",
    SUM(s.open_cat_outsource)                AS "外协外购",
    SUM(s.open_cat_software)                 AS "软件",
    SUM(s.open_cat_other)                    AS "其他"
FROM biz_dws_quality_monthly_v2 s
WHERE s.period_month >= to_char({{dateFrom}}::date, 'YYYY-MM')
  AND s.period_month <= to_char({{dateTo}}::date, 'YYYY-MM')
  AND ({{deptId}} IS NULL OR {{deptId}} = '' OR s.dept = {{deptId}})
  AND ({{projectNo}} IS NULL OR {{projectNo}} = '' OR s.project_no ILIKE '%' || {{projectNo}} || '%');


-- card-quality-issue-list: 质量问题明细清单
-- 用于: S3 问题清单, D2 质量清单
SELECT
    d.issue_id,
    d.project_no                 AS "项目编号",
    d.subsystem                  AS "分系统",
    d.issue_name                 AS "问题名称",
    d.dept                       AS "责任单位",
    d.team_leader                AS "团队负责人",
    d.issue_category             AS "原因分类",
    d.status                     AS "状态",
    d.issue_date                 AS "发生时间",
    d.zero_complete_date         AS "归零完成时间",
    d.current_progress           AS "当前进展",
    d.pending_days               AS "滞留天数",
    CASE WHEN d.has_zero_plan THEN '是' ELSE '否' END AS "有归零计划"
FROM biz_dwd_quality_issue_v2 d
WHERE d.issue_month >= to_char({{dateFrom}}::date, 'YYYY-MM')
  AND d.issue_month <= to_char({{dateTo}}::date, 'YYYY-MM')
  AND ({{deptId}} IS NULL OR {{deptId}} = '' OR d.dept = {{deptId}})
  AND ({{projectNo}} IS NULL OR {{projectNo}} = '' OR d.project_no ILIKE '%' || {{projectNo}} || '%')
ORDER BY d.pending_days DESC;


-- ═══════════════════════════════════════════════════════════
-- 【技术状态域】原始指标 #49-67
-- 来源: biz_dws_tech_state_monthly_v2 (DWS)
-- ═══════════════════════════════════════════════════════════

-- card-tech-state-kpi: 技术状态 KPI 概览（#49-67）
-- 用于: S1 技术变更柱图, S4 全部, D3 签署分布
SELECT
    SUM(s.total_change_cnt)                  AS "变更总数",
    SUM(s.new_cat_i)                         AS "新增I类更改",
    SUM(s.new_cat_ii)                        AS "新增II类更改",
    SUM(s.new_cat_iii)                       AS "新增III类更改",
    -- 文件签署 5 种状态
    SUM(s.file_submitted_not_reviewed_i_ii)  AS "已提出未评审(I,II类)",
    SUM(s.file_reviewed_not_signed_i_ii)     AS "已评审未签署(I,II类)",
    SUM(s.file_reviewed_signed_i_ii)         AS "已评审已签署(I,II类)",
    SUM(s.file_submitted_not_signed_iii)     AS "已提出未签署(III类)",
    SUM(s.file_submitted_signed_iii)         AS "已提出已签署(III类)",
    -- 汇总
    SUM(s.file_submitted_not_reviewed_i_ii)
      + SUM(s.file_reviewed_not_signed_i_ii) AS "文件未签署(I,II类)",
    SUM(s.file_submitted_not_signed_iii)     AS "文件未签署(III类)",
    SUM(s.file_reviewed_signed_i_ii)
      + SUM(s.file_submitted_signed_iii)     AS "文件已签署(合计)",
    -- 变更单签署
    SUM(s.order_unsigned_cat_i)              AS "变更单未签署(I类)",
    SUM(s.order_unsigned_cat_ii)             AS "变更单未签署(II类)",
    SUM(s.order_unsigned_cat_iii)            AS "变更单未签署(III类)",
    SUM(s.order_unsigned_cat_i) + SUM(s.order_unsigned_cat_ii)
      + SUM(s.order_unsigned_cat_iii)        AS "变更单未签署(合计)",
    SUM(s.order_signed_cnt)                  AS "变更单已签署",
    -- 签署率
    CASE WHEN (SUM(s.order_signed_cnt) + SUM(s.order_unsigned_cat_i)
             + SUM(s.order_unsigned_cat_ii) + SUM(s.order_unsigned_cat_iii)) = 0 THEN 0
         ELSE ROUND(SUM(s.order_signed_cnt)::numeric
                  / (SUM(s.order_signed_cnt) + SUM(s.order_unsigned_cat_i)
                   + SUM(s.order_unsigned_cat_ii) + SUM(s.order_unsigned_cat_iii))::numeric * 100, 2)
    END                                      AS "签署完成率",
    -- 整改
    SUM(s.reform_pending_i_ii)               AS "未落实整改(I,II类)",
    SUM(s.reform_done_i_ii)                  AS "已落实整改(I,II类)",
    SUM(s.reform_na_iii)                     AS "不涉及整改(III类)"
FROM biz_dws_tech_state_monthly_v2 s
WHERE s.period_month >= to_char({{dateFrom}}::date, 'YYYY-MM')
  AND s.period_month <= to_char({{dateTo}}::date, 'YYYY-MM')
  AND ({{deptId}} IS NULL OR {{deptId}} = '' OR s.dept = {{deptId}})
  AND ({{projectNo}} IS NULL OR {{projectNo}} = '' OR s.project_no ILIKE '%' || {{projectNo}} || '%');


-- card-tech-state-list: 技术状态变更明细
-- 用于: S4 变更清单, D3 变更清单
SELECT
    d.tech_state_id,
    d.project_no                 AS "项目编号",
    d.tech_state_name            AS "技术状态项",
    d.change_item                AS "更改事项",
    d.owner                      AS "负责人",
    d.dept                       AS "责任科室",
    d.change_category            AS "更改类别",
    d.change_submit_time         AS "提出时间",
    d.completion_signature       AS "变更单签署",
    d.file_signature_status      AS "文件签署状态",
    d.reform_status              AS "整改状态"
FROM biz_dwd_tech_state_v2 d
WHERE d.submit_month >= to_char({{dateFrom}}::date, 'YYYY-MM')
  AND d.submit_month <= to_char({{dateTo}}::date, 'YYYY-MM')
  AND ({{deptId}} IS NULL OR {{deptId}} = '' OR d.dept = {{deptId}})
  AND ({{projectNo}} IS NULL OR {{projectNo}} = '' OR d.project_no ILIKE '%' || {{projectNo}} || '%')
ORDER BY d.change_submit_time DESC;


-- ═══════════════════════════════════════════════════════════
-- 【风险域】KPI + 明细
-- 来源: biz_dws_risk_monthly_v2 (DWS)
-- ═══════════════════════════════════════════════════════════

-- card-risk-kpi: 风险域 KPI 概览
-- 用于: S1 风险分布, S5 KPI, D4 KPI/分类
SELECT
    SUM(s.total_risk_cnt)                    AS "风险总数",
    SUM(s.high_cnt)                          AS "高风险",
    SUM(s.mid_cnt)                           AS "中风险",
    SUM(s.low_cnt)                           AS "低风险",
    SUM(s.released_cnt)                      AS "已释放",
    SUM(s.open_cnt)                          AS "未释放",
    CASE WHEN SUM(s.total_risk_cnt) = 0 THEN 0
         ELSE ROUND(SUM(s.released_cnt)::numeric
                  / SUM(s.total_risk_cnt)::numeric * 100, 2)
    END                                      AS "释放率"
FROM biz_dws_risk_monthly_v2 s
WHERE s.period_month >= to_char({{dateFrom}}::date, 'YYYY-MM')
  AND s.period_month <= to_char({{dateTo}}::date, 'YYYY-MM')
  AND ({{deptId}} IS NULL OR {{deptId}} = '' OR s.dept = {{deptId}})
  AND ({{projectNo}} IS NULL OR {{projectNo}} = '' OR s.project_no ILIKE '%' || {{projectNo}} || '%');


-- card-risk-info-list: 风险明细清单
-- 用于: S5 风险清单, D4 风险清单
SELECT
    d.risk_id,
    d.project_no                 AS "项目编号",
    d.risk_name                  AS "风险名称",
    d.subsystem                  AS "分系统",
    d.risk_level                 AS "风险等级",
    d.risk_phase                 AS "风险阶段",
    d.risk_category              AS "风险分类",
    d.risk_status                AS "风险状态",
    d.risk_submit_date           AS "提出时间",
    d.final_release_date         AS "计划释放时间",
    d.response_owner             AS "应对负责人",
    d.dept                       AS "责任科室",
    d.pending_days               AS "滞留天数"
FROM biz_dwd_risk_info_v2 d
WHERE d.submit_month >= to_char({{dateFrom}}::date, 'YYYY-MM')
  AND d.submit_month <= to_char({{dateTo}}::date, 'YYYY-MM')
  AND ({{deptId}} IS NULL OR {{deptId}} = '' OR d.dept = {{deptId}})
  AND ({{projectNo}} IS NULL OR {{projectNo}} = '' OR d.project_no ILIKE '%' || {{projectNo}} || '%')
  AND ({{riskLevel}} IS NULL OR {{riskLevel}} = '' OR d.risk_level = {{riskLevel}})
ORDER BY d.risk_rank DESC, d.pending_days DESC;
