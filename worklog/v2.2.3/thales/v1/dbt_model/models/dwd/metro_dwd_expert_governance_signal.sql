{{ config(materialized='table', tags=['metro-app-pack', 'dwd', 'expert-governance']) }}

SELECT
  'label'::text AS governance_type,
  event_id AS governance_id,
  batch_id,
  window_id,
  equipment_id,
  NULL::text AS equipment_type,
  label_type AS subject,
  expert_verdict AS action,
  severity,
  severity_score,
  confidence,
  expert_user,
  reviewed_at AS effective_at,
  expert_comment AS governance_payload_text,
  imported_at
FROM {{ ref('stg_metro__expert_event_label') }}

UNION ALL

SELECT
  'rule'::text AS governance_type,
  rule_id AS governance_id,
  NULL::text AS batch_id,
  NULL::text AS window_id,
  NULL::text AS equipment_id,
  equipment_type,
  feature_name AS subject,
  rule_operator || ' ' || COALESCE(threshold_value::text, '') AS action,
  severity,
  severity_score,
  confidence,
  expert_user,
  COALESCE(effective_from, imported_at) AS effective_at,
  rule_payload::text AS governance_payload_text,
  imported_at
FROM {{ ref('stg_metro__expert_rule') }}

UNION ALL

SELECT
  'threshold_policy'::text AS governance_type,
  policy_id AS governance_id,
  batch_id,
  NULL::text AS window_id,
  NULL::text AS equipment_id,
  NULL::text AS equipment_type,
  target_metric AS subject,
  threshold_name || ' = ' || COALESCE(threshold_value::text, '') AS action,
  NULL::text AS severity,
  0 AS severity_score,
  0.0 AS confidence,
  updated_by AS expert_user,
  COALESCE(updated_at, imported_at) AS effective_at,
  policy_payload::text AS governance_payload_text,
  imported_at
FROM {{ ref('stg_metro__threshold_policy') }}
