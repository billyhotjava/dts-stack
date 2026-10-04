

SELECT
  o.id AS source_row_id,
  'ods_risk_info_v2'::text AS source_table,
  COALESCE((
  case
    when o._dts_source_system is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o._dts_source_system as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o._dts_source_system as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o._dts_source_system as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), 'excel') AS source_system,
  o._dts_import_time AS imported_at,

  (
  case
    when o.project_no is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.project_no as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.project_no as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.project_no as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS project_no,
  (
  case
    when o.risk_name is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_name as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_name as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_name as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS risk_name,
  (
  case
    when o.subsystem is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.subsystem as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.subsystem as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.subsystem as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS subsystem,
  (
  case
    when o.belonging_unit is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.belonging_unit as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.belonging_unit as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.belonging_unit as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS belonging_unit,
  (
  case
    when o.risk_description is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_description as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_description as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_description as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS risk_description,
  (
  case
    when o.risk_phase is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_phase as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_phase as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_phase as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS risk_phase,
  (
  case
    when o.impact_scope is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.impact_scope as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.impact_scope as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.impact_scope as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS impact_scope,
  (
  case
    when o.response_measure is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.response_measure as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.response_measure as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.response_measure as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS response_measure,
  (
  case
    when o.monthly_control_plan is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.monthly_control_plan as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.monthly_control_plan as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.monthly_control_plan as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS monthly_control_plan,
  (
  case
    when o.weekly_release_plan is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.weekly_release_plan as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.weekly_release_plan as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.weekly_release_plan as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS weekly_release_plan,
  (
  case
    when o.progress_situation is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.progress_situation as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.progress_situation as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.progress_situation as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS progress_situation,
  (
  case
    when o.response_owner is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.response_owner as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.response_owner as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.response_owner as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS response_owner,
  (
  case
    when o.control_owner is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.control_owner as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.control_owner as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.control_owner as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS control_owner,
  (
  case
    when o.dept is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.dept as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.dept as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.dept as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS dept,
  (
  case
    when o.remark is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.remark as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.remark as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.remark as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS remark,
  (
  case
    when o.filled_by is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.filled_by as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.filled_by as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.filled_by as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS filled_by,

  (
  case
    when o.risk_category is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_category as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_category as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_category as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS risk_category_raw,
  (
  case
    when o.risk_level is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_level as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_level as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_level as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS risk_level_raw,
  (
  case
    when o.release_plan_synced is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.release_plan_synced as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.release_plan_synced as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.release_plan_synced as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS release_plan_synced_raw,
  (
  case
    when o.risk_status is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_status as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_status as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_status as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS risk_status_raw,
  (
  case
    when o.risk_submit_time is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_submit_time as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_submit_time as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_submit_time as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS risk_submit_time_raw,

  (
  case
    when (
  case
    when o.new_plan_count is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.new_plan_count as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.new_plan_count as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.new_plan_count as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) is null then null

    -- 去除千分位逗号后，标准数值: 123, -456.78
    when regexp_replace((
  case
    when o.new_plan_count is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.new_plan_count as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.new_plan_count as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.new_plan_count as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.new_plan_count is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.new_plan_count as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.new_plan_count as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.new_plan_count as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g')::numeric

    -- 百分号: 72.4% → 72.4（保留原始数值，不除以100）
    when regexp_replace((
  case
    when o.new_plan_count is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.new_plan_count as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.new_plan_count as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.new_plan_count as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.new_plan_count is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.new_plan_count as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.new_plan_count as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.new_plan_count as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g')::numeric

    -- 带中文单位: 28天, 36.8亿, 12个 → 提取前面的数值
    when regexp_replace((
  case
    when o.new_plan_count is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.new_plan_count as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.new_plan_count as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.new_plan_count as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g') ~ '^-?\d+(\.\d+)?$'
         and (
  case
    when o.new_plan_count is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.new_plan_count as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.new_plan_count as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.new_plan_count as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) ~ '^\s*-?\d+\.?\d*\s*[^\d.\s]'
      then regexp_replace((
  case
    when o.new_plan_count is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.new_plan_count as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.new_plan_count as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.new_plan_count as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g')::numeric

    -- 科学计数法: 1.23E+05, 1.23e5, 1.23E-3
    when upper((
  case
    when o.new_plan_count is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.new_plan_count as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.new_plan_count as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.new_plan_count as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)) ~ '^-?\d+(\.\d+)?[Ee][+\-]?\d+$'
      then (
  case
    when o.new_plan_count is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.new_plan_count as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.new_plan_count as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.new_plan_count as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)::double precision::numeric

    -- 全角数字转半角: ０１２３ → 0123
    when translate((
  case
    when o.new_plan_count is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.new_plan_count as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.new_plan_count as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.new_plan_count as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.') ~ '^-?\d+(\.\d+)?$'
      then translate((
  case
    when o.new_plan_count is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.new_plan_count as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.new_plan_count as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.new_plan_count as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.')::numeric

    else null
  end
)::int AS new_plan_count,
  (
  case
    when (
  case
    when o.risk_submit_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_submit_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) is null then null

    -- 去除千分位逗号后，标准数值: 123, -456.78
    when regexp_replace((
  case
    when o.risk_submit_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_submit_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.risk_submit_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_submit_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g')::numeric

    -- 百分号: 72.4% → 72.4（保留原始数值，不除以100）
    when regexp_replace((
  case
    when o.risk_submit_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_submit_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.risk_submit_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_submit_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g')::numeric

    -- 带中文单位: 28天, 36.8亿, 12个 → 提取前面的数值
    when regexp_replace((
  case
    when o.risk_submit_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_submit_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g') ~ '^-?\d+(\.\d+)?$'
         and (
  case
    when o.risk_submit_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_submit_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) ~ '^\s*-?\d+\.?\d*\s*[^\d.\s]'
      then regexp_replace((
  case
    when o.risk_submit_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_submit_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g')::numeric

    -- 科学计数法: 1.23E+05, 1.23e5, 1.23E-3
    when upper((
  case
    when o.risk_submit_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_submit_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)) ~ '^-?\d+(\.\d+)?[Ee][+\-]?\d+$'
      then (
  case
    when o.risk_submit_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_submit_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)::double precision::numeric

    -- 全角数字转半角: ０１２３ → 0123
    when translate((
  case
    when o.risk_submit_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_submit_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.') ~ '^-?\d+(\.\d+)?$'
      then translate((
  case
    when o.risk_submit_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_submit_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.')::numeric

    else null
  end
)::int AS risk_submit_week,
  (
  case
    when (
  case
    when o.final_release_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.final_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.final_release_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.final_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) is null then null

    -- 去除千分位逗号后，标准数值: 123, -456.78
    when regexp_replace((
  case
    when o.final_release_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.final_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.final_release_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.final_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.final_release_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.final_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.final_release_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.final_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g')::numeric

    -- 百分号: 72.4% → 72.4（保留原始数值，不除以100）
    when regexp_replace((
  case
    when o.final_release_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.final_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.final_release_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.final_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.final_release_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.final_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.final_release_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.final_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g')::numeric

    -- 带中文单位: 28天, 36.8亿, 12个 → 提取前面的数值
    when regexp_replace((
  case
    when o.final_release_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.final_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.final_release_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.final_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g') ~ '^-?\d+(\.\d+)?$'
         and (
  case
    when o.final_release_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.final_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.final_release_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.final_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) ~ '^\s*-?\d+\.?\d*\s*[^\d.\s]'
      then regexp_replace((
  case
    when o.final_release_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.final_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.final_release_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.final_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g')::numeric

    -- 科学计数法: 1.23E+05, 1.23e5, 1.23E-3
    when upper((
  case
    when o.final_release_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.final_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.final_release_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.final_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)) ~ '^-?\d+(\.\d+)?[Ee][+\-]?\d+$'
      then (
  case
    when o.final_release_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.final_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.final_release_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.final_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)::double precision::numeric

    -- 全角数字转半角: ０１２３ → 0123
    when translate((
  case
    when o.final_release_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.final_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.final_release_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.final_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.') ~ '^-?\d+(\.\d+)?$'
      then translate((
  case
    when o.final_release_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.final_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.final_release_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.final_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.')::numeric

    else null
  end
)::int AS final_release_week,
  (
  case
    when (
  case
    when o.progress_stat_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.progress_stat_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.progress_stat_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.progress_stat_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) is null then null

    -- 去除千分位逗号后，标准数值: 123, -456.78
    when regexp_replace((
  case
    when o.progress_stat_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.progress_stat_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.progress_stat_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.progress_stat_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.progress_stat_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.progress_stat_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.progress_stat_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.progress_stat_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g')::numeric

    -- 百分号: 72.4% → 72.4（保留原始数值，不除以100）
    when regexp_replace((
  case
    when o.progress_stat_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.progress_stat_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.progress_stat_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.progress_stat_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.progress_stat_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.progress_stat_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.progress_stat_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.progress_stat_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g')::numeric

    -- 带中文单位: 28天, 36.8亿, 12个 → 提取前面的数值
    when regexp_replace((
  case
    when o.progress_stat_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.progress_stat_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.progress_stat_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.progress_stat_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g') ~ '^-?\d+(\.\d+)?$'
         and (
  case
    when o.progress_stat_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.progress_stat_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.progress_stat_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.progress_stat_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) ~ '^\s*-?\d+\.?\d*\s*[^\d.\s]'
      then regexp_replace((
  case
    when o.progress_stat_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.progress_stat_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.progress_stat_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.progress_stat_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g')::numeric

    -- 科学计数法: 1.23E+05, 1.23e5, 1.23E-3
    when upper((
  case
    when o.progress_stat_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.progress_stat_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.progress_stat_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.progress_stat_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)) ~ '^-?\d+(\.\d+)?[Ee][+\-]?\d+$'
      then (
  case
    when o.progress_stat_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.progress_stat_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.progress_stat_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.progress_stat_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)::double precision::numeric

    -- 全角数字转半角: ０１２３ → 0123
    when translate((
  case
    when o.progress_stat_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.progress_stat_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.progress_stat_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.progress_stat_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.') ~ '^-?\d+(\.\d+)?$'
      then translate((
  case
    when o.progress_stat_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.progress_stat_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.progress_stat_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.progress_stat_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.')::numeric

    else null
  end
)::int AS progress_stat_week,
  (
  case
    when (
  case
    when o.risk_release_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_release_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) is null then null

    -- 去除千分位逗号后，标准数值: 123, -456.78
    when regexp_replace((
  case
    when o.risk_release_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_release_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.risk_release_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_release_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g')::numeric

    -- 百分号: 72.4% → 72.4（保留原始数值，不除以100）
    when regexp_replace((
  case
    when o.risk_release_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_release_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.risk_release_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_release_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g')::numeric

    -- 带中文单位: 28天, 36.8亿, 12个 → 提取前面的数值
    when regexp_replace((
  case
    when o.risk_release_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_release_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g') ~ '^-?\d+(\.\d+)?$'
         and (
  case
    when o.risk_release_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_release_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) ~ '^\s*-?\d+\.?\d*\s*[^\d.\s]'
      then regexp_replace((
  case
    when o.risk_release_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_release_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g')::numeric

    -- 科学计数法: 1.23E+05, 1.23e5, 1.23E-3
    when upper((
  case
    when o.risk_release_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_release_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)) ~ '^-?\d+(\.\d+)?[Ee][+\-]?\d+$'
      then (
  case
    when o.risk_release_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_release_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)::double precision::numeric

    -- 全角数字转半角: ０１２３ → 0123
    when translate((
  case
    when o.risk_release_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_release_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.') ~ '^-?\d+(\.\d+)?$'
      then translate((
  case
    when o.risk_release_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_release_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_release_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.')::numeric

    else null
  end
)::int AS risk_release_week,
  (
  case
    when (
  case
    when o.last_update_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.last_update_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.last_update_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.last_update_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) is null then null

    -- 去除千分位逗号后，标准数值: 123, -456.78
    when regexp_replace((
  case
    when o.last_update_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.last_update_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.last_update_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.last_update_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.last_update_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.last_update_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.last_update_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.last_update_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g')::numeric

    -- 百分号: 72.4% → 72.4（保留原始数值，不除以100）
    when regexp_replace((
  case
    when o.last_update_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.last_update_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.last_update_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.last_update_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.last_update_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.last_update_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.last_update_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.last_update_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g')::numeric

    -- 带中文单位: 28天, 36.8亿, 12个 → 提取前面的数值
    when regexp_replace((
  case
    when o.last_update_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.last_update_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.last_update_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.last_update_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g') ~ '^-?\d+(\.\d+)?$'
         and (
  case
    when o.last_update_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.last_update_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.last_update_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.last_update_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) ~ '^\s*-?\d+\.?\d*\s*[^\d.\s]'
      then regexp_replace((
  case
    when o.last_update_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.last_update_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.last_update_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.last_update_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g')::numeric

    -- 科学计数法: 1.23E+05, 1.23e5, 1.23E-3
    when upper((
  case
    when o.last_update_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.last_update_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.last_update_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.last_update_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)) ~ '^-?\d+(\.\d+)?[Ee][+\-]?\d+$'
      then (
  case
    when o.last_update_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.last_update_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.last_update_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.last_update_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)::double precision::numeric

    -- 全角数字转半角: ０１２３ → 0123
    when translate((
  case
    when o.last_update_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.last_update_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.last_update_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.last_update_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.') ~ '^-?\d+(\.\d+)?$'
      then translate((
  case
    when o.last_update_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.last_update_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.last_update_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.last_update_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.')::numeric

    else null
  end
)::int AS last_update_week,

  public.dts_try_to_date(cast((
  case
    when o.risk_submit_time is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_submit_time as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_submit_time as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_submit_time as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) as text)) AS risk_submit_date,
  public.dts_try_to_date(cast((
  case
    when o.final_release_time is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.final_release_time as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.final_release_time as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.final_release_time as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) as text)) AS final_release_date,
  public.dts_try_to_date(cast((
  case
    when o.progress_stat_time is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.progress_stat_time as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.progress_stat_time as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.progress_stat_time as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) as text)) AS progress_stat_date,
  public.dts_try_to_date(cast((
  case
    when o.risk_release_date is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_release_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_release_date as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_release_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) as text)) AS risk_release_date,
  public.dts_try_to_date(cast((
  case
    when o.last_update_time is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.last_update_time as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.last_update_time as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.last_update_time as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) as text)) AS last_update_time
FROM "biadmin"."public"."ods_risk_info_v2" o