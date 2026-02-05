-- ============================================================
-- 模型: dws_patent_year_type
-- 层级: DWS (汇总层)
-- 物化: table
-- ============================================================
--
-- 设计说明
-- --------
-- 按申请年度 + 专利类型的二维交叉统计表。
--
-- 为什么需要这张表：
--   - 仪表盘需要展示"各类型专利占比"饼图/环形图
--   - 按年度切换时可以看到类型结构变化趋势
--   - ADS 层的 ads_patent_type_share 基于本表计算占比
--
-- 字段说明：
--   - stat_year:   申请年份
--   - patent_type: 专利类型（发明/实用新型/外观设计/未知）
--   - apply_cnt:   该年度该类型的申请数量
--
-- 注意：patent_type 为空时统一归为"未知"，避免 NULL 导致 GROUP BY 异常
-- ============================================================

{{ config(materialized='table', alias='dws_patent_year_type', schema='public', tags=['dws', 'patent']) }}

SELECT
  application_year                              AS stat_year,
  COALESCE(NULLIF(patent_type, ''), '未知')     AS patent_type,
  COUNT(*)                                      AS apply_cnt

FROM {{ ref('dwd_patent') }}
WHERE application_year IS NOT NULL
GROUP BY application_year, COALESCE(NULLIF(patent_type, ''), '未知')
