

SELECT
  t.source_row_id AS personal_balance_id,
  t.source_row_id,
  t.source_table,
  t.subject_code,
  t.subject_name,
  t.employee_dept,
  t.employee_name,
  t.balance,
  t.abs_balance,
  t.balance_direction,
  b.balance_direction_id,
  t.subject_category,
  s.subject_category_id,
  now() AS etl_time
FROM "biadmin"."public"."stg_fin__aux_balance_personal" t
LEFT JOIN "biadmin"."public"."dim_balance_direction" b
  ON b.code = t.balance_direction
LEFT JOIN "biadmin"."public"."dim_personal_subject_category" s
  ON s.code = t.subject_category