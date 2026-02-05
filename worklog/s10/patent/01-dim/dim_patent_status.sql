-- ============================================================
-- 模型: dim_patent_status
-- 层级: DIM (维度层)
-- 物化: table
-- ============================================================
--
-- 设计说明
-- --------
-- 专利状态维度映射表。ODS 中 state 字段为自由文本（"在审"、"已授权"等），
-- 不同来源系统的状态描述不统一，需要标准化映射。
--
-- 本维度表将原始状态码映射为：
--   - status_std:  标准化状态（accepted/granted/published/invalid/other）
--   - is_accepted: 是否已受理（布尔标记，便于下游直接过滤）
--   - is_granted:  是否已授权（布尔标记）
--
-- 为什么单独建维度表：
--   1. 状态映射规则可能随业务变化（新增状态值），集中维护比硬编码在 DWD 里更灵活
--   2. 多个下游模型（dwd_patent、仪表盘过滤器）都需要引用状态标准化结果
--   3. 维度表体积极小（几行），不会产生性能问题
--
-- 维护方式：
--   当发现新的原始状态值未被映射时，在 VALUES 列表中追加即可。
--
-- 字段说明
-- --------
-- status_code : ODS state 字段的原始值（去空格后），作为关联键
-- status_std  : 标准化状态枚举值
-- is_accepted : 是否为"已受理"状态
-- is_granted  : 是否为"已授权"状态
-- remark      : 备注说明
-- ============================================================

{{ config(materialized='table', alias='dim_patent_status', schema='public', tags=['dim', 'patent']) }}

SELECT
  status_code,
  status_std,
  is_accepted,
  is_granted,
  remark
FROM (VALUES
  ('在审',   'accepted',  true,  false, '在审/审查中'),
  ('受理',   'accepted',  true,  false, '受理'),
  ('初审',   'accepted',  true,  false, '初审'),
  ('实审',   'accepted',  true,  false, '实审'),
  ('已公开', 'published', false, false, '已公开/公示'),
  ('公布',   'published', false, false, '公布'),
  ('已授权', 'granted',   false, true,  '已授权'),
  ('授权',   'granted',   false, true,  '授权'),
  ('失效',   'invalid',   false, false, '失效/终止'),
  ('无效',   'invalid',   false, false, '无效/宣告无效')
) AS t(status_code, status_std, is_accepted, is_granted, remark)
