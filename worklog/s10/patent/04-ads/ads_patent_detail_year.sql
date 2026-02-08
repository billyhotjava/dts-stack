-- ============================================================
-- 模型: ads_patent_detail_year
-- 层级: ADS (应用层)
-- 物化: table
-- ============================================================
--
-- 设计说明
-- --------
-- 全量专利明细表 + 按年动态分表。
--
-- 本模型执行后会：
--   1. 生成 ads_patent_detail_year 主表（包含所有年份数据）
--   2. 通过 post_hook 自动遍历数据中的年份，
--      为每个年份创建独立的分年表：
--        ads_patent_detail_year_2023
--        ads_patent_detail_year_2024
--        ads_patent_detail_year_2025
--        ...
--
-- 分年口径：该年申请 OR 该年授权（并集），
-- 即一条专利如果 2023 年申请、2025 年授权，
-- 会同时出现在 ads_patent_detail_year_2023 和 ads_patent_detail_year_2025。
--
-- 字段说明：
--   - application_year:  申请年份（用于分年筛选）
--   - grant_year:        授权年份（用于分年筛选）
--   - application_date:  申请日期
--   - grant_date:        授权日期
--   - patent_no:         专利号
--   - patent_title_cn:   专利名称
--   - patent_type:       专利类型
--   - patent_status_std: 标准化状态
--   - patent_status_raw: 原始状态
--   - dept_name:         所属部门
--   - assignee_name:     申请人
-- ============================================================

{{ config(
    materialized='table',
    alias='ads_patent_detail_year',
    schema='public',
    tags=['ads', 'patent'],
    post_hook="
      DO $$
      DECLARE
        yr int;
      BEGIN
        FOR yr IN
          SELECT DISTINCT y FROM (
            SELECT application_year AS y FROM public.ads_patent_detail_year WHERE application_year IS NOT NULL
            UNION
            SELECT grant_year AS y FROM public.ads_patent_detail_year WHERE grant_year IS NOT NULL
          ) t ORDER BY y
        LOOP
          EXECUTE format('DROP TABLE IF EXISTS public.ads_patent_detail_year_%s', yr);
          EXECUTE format(
            'CREATE TABLE public.ads_patent_detail_year_%s AS
             SELECT application_date, grant_date, patent_no, patent_title_cn, patent_type,
                    patent_status_std, patent_status_raw, dept_name, assignee_name
             FROM public.ads_patent_detail_year
             WHERE application_year = %s OR grant_year = %s
             ORDER BY COALESCE(application_date, grant_date) DESC NULLS LAST',
            yr, yr, yr
          );
        END LOOP;
      END $$;
    "
) }}

SELECT
  application_year,
  grant_year,
  application_date,
  grant_date,
  patent_no,
  patent_title_cn,
  patent_type,
  patent_status_std,
  patent_status_raw,
  dept_name,
  assignee_name

FROM {{ ref('dwd_patent') }}
ORDER BY COALESCE(application_date, grant_date) DESC NULLS LAST
