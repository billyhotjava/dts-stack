{{ config(materialized='table', tags=['project-management-v3', 'dim', 'dwd']) }}

SELECT risk_level_id, code, label, is_high, is_mid, is_low, severity_rank
FROM (
  VALUES
    ('risk_level_high', '高', '高风险', true,  false, false, 3),
    ('risk_level_medium', '中', '中风险', false, true,  false, 2),
    ('risk_level_low', '低', '低风险', false, false, true,  1)
) AS t(risk_level_id, code, label, is_high, is_mid, is_low, severity_rank)
