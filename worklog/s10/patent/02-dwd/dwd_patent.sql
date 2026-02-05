-- ============================================================
-- 模型: dwd_patent
-- 层级: DWD (明细层)
-- 物化: table
-- ============================================================
--
-- 设计说明
-- --------
-- 专利明细宽表，是整个专利数仓的核心模型。
-- 从 ODS 原始数据清洗加工而来，所有下游 DWS/ADS 模型均依赖此表。
--
-- 本层完成的加工：
--   1. 数据清洗：去除前后空格，空字符串转 NULL
--   2. 类型转换：字符串日期 → date 类型（通过 parse_date_safe 函数）
--   3. 状态标准化：关联 dim_patent_status，将自由文本映射为标准枚举
--   4. 主键生成：优先使用 patent_no，缺失时用 md5(标题+日期+申请人) 生成
--   5. 衍生字段：年份、月份、授权耗时天数
--
-- 为什么需要 DWD 层：
--   - ODS 数据全部是 varchar(500)，无类型约束，不适合直接聚合统计
--   - 日期格式不统一（YYYY-MM-DD / YYYY/MM/DD / YYYYMMDD 混杂）
--   - 状态字段为自由文本，需要标准化后才能按状态过滤/统计
--   - 衍生字段（年份、月份、耗时）在多个 DWS 中重复使用，集中计算避免冗余
--
-- 字段说明
-- --------
-- patent_id              : 专利唯一标识。优先取 patent_no，缺失时取 md5 哈希
-- patent_no              : 专利号（可能为空）
-- patent_title_cn        : 专利中文名称
-- patent_type            : 专利类型（发明/实用新型/外观设计）
-- patent_status_raw      : ODS 原始状态值（保留，便于排查）
-- patent_status_std      : 标准化状态（accepted/granted/published/invalid/other）
-- is_accepted            : 是否已受理
-- is_granted             : 是否已授权
-- application_date       : 申请日期 (date)
-- accept_date            : 受理日期 (date)
-- grant_date             : 授权日期 (date)
-- first_publication_date : 首次公开日期 (date)
-- assignee_name          : 申请人/权利人
-- agent_org_name         : 代理机构
-- dept_name              : 所属部门
-- dept_code              : 所属部门编码
-- inventor_names         : 发明人
-- application_year       : 申请年份 (int)，从 application_date 提取
-- application_month      : 申请月份 (YYYY-MM)，用于月度趋势
-- grant_year             : 授权年份 (int)
-- grant_month            : 授权月份 (YYYY-MM)
-- days_to_grant          : 从申请到授权的天数，衡量审批效率
-- source_table           : 来源表标记，便于多源合并时追溯
-- etl_time               : ETL 加工时间戳
-- ============================================================

{{ config(materialized='table', alias='dwd_patent', schema='public', tags=['dwd', 'patent']) }}

SELECT
  -- ---- 主键 ----
  COALESCE(
    NULLIF(btrim(o.patent_no), ''),
    md5(
      COALESCE(btrim(o.patent_title_cn), '') || '|' ||
      COALESCE(parse_date_safe(o.application_date)::text, '') || '|' ||
      COALESCE(btrim(o.assignee_name), '')
    )
  ) AS patent_id,

  -- ---- 基本属性 ----
  NULLIF(btrim(o.patent_no), '')               AS patent_no,
  NULLIF(btrim(o.patent_title_cn), '')          AS patent_title_cn,
  NULLIF(btrim(o.patent_type), '')              AS patent_type,

  -- ---- 状态标准化 ----
  NULLIF(btrim(o.state), '')                    AS patent_status_raw,
  COALESCE(s.status_std, 'other')               AS patent_status_std,
  COALESCE(s.is_accepted, false)                AS is_accepted,
  COALESCE(s.is_granted, false)                 AS is_granted,

  -- ---- 日期清洗（字符串 → date）----
  parse_date_safe(
    COALESCE(NULLIF(btrim(o.application_date), ''), NULLIF(btrim(o.accept_date), ''))
  ) AS application_date,
  parse_date_safe(NULLIF(btrim(o.accept_date), '')) AS accept_date,
  parse_date_safe(o.grant_date)                  AS grant_date,
  parse_date_safe(o.first_publication_date)       AS first_publication_date,

  -- ---- 组织与人员 ----
  NULLIF(btrim(o.assignee_name), '')             AS assignee_name,
  NULLIF(btrim(o.agent_org_name), '')            AS agent_org_name,
  NULLIF(btrim(o.dept_name), '')                 AS dept_name,
  NULLIF(btrim(o.dept_code), '')                 AS dept_code,
  NULLIF(btrim(o.inventor_names), '')            AS inventor_names,

  -- ---- 衍生时间维度 ----
  EXTRACT(YEAR FROM parse_date_safe(
    COALESCE(NULLIF(btrim(o.application_date), ''), NULLIF(btrim(o.accept_date), ''))
  ))::int AS application_year,
  to_char(
    parse_date_safe(COALESCE(NULLIF(btrim(o.application_date), ''), NULLIF(btrim(o.accept_date), ''))),
    'YYYY-MM'
  ) AS application_month,
  EXTRACT(YEAR FROM parse_date_safe(o.grant_date))::int          AS grant_year,
  to_char(parse_date_safe(o.grant_date), 'YYYY-MM')              AS grant_month,

  -- ---- 审批效率 ----
  CASE
    WHEN parse_date_safe(COALESCE(NULLIF(btrim(o.application_date), ''), NULLIF(btrim(o.accept_date), ''))) IS NOT NULL
     AND parse_date_safe(o.grant_date) IS NOT NULL
    THEN (parse_date_safe(o.grant_date) - parse_date_safe(
      COALESCE(NULLIF(btrim(o.application_date), ''), NULLIF(btrim(o.accept_date), ''))
    ))::int
  END AS days_to_grant,

  -- ---- ETL 标记 ----
  'ods_patent_info'::text AS source_table,
  now()                   AS etl_time

FROM {{ source('public', 'ods_patent_info') }} o
LEFT JOIN {{ ref('dim_patent_status') }} s
  ON s.status_code = NULLIF(btrim(o.state), '')
