

SELECT
  o.id AS source_row_id,
  'ods_quality_issue_v2'::text AS source_table,
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
    when o.issue_name is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.issue_name as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.issue_name as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.issue_name as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS issue_name,
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
    when o.team_leader is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.team_leader as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.team_leader as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.team_leader as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS team_leader,
  (
  case
    when o.dept_leader is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.dept_leader as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.dept_leader as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.dept_leader as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS dept_leader,
  (
  case
    when o.issue_summary is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.issue_summary as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.issue_summary as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.issue_summary as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS issue_summary,
  (
  case
    when o.zero_plan is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.zero_plan as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.zero_plan as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.zero_plan as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS zero_plan,
  (
  case
    when o.current_progress is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.current_progress as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.current_progress as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.current_progress as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS current_progress,
  (
  case
    when o.project_manager is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.project_manager as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.project_manager as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.project_manager as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS project_manager,
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
    when o.issue_category is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.issue_category as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.issue_category as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.issue_category as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS issue_category_raw,
  (
  case
    when o.zero_plan_synced is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.zero_plan_synced as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.zero_plan_synced as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.zero_plan_synced as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS zero_plan_synced_raw,
  (
  case
    when o.status is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.status as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.status as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.status as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS status_raw,
  (
  case
    when o.issue_date is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.issue_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.issue_date as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.issue_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS issue_date_raw,

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
  public.dts_try_to_date(cast((
  case
    when o.issue_date is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.issue_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.issue_date as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.issue_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) as text)) AS issue_date,
  public.dts_try_to_date(cast((
  case
    when o.zero_complete_date is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.zero_complete_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.zero_complete_date as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.zero_complete_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) as text)) AS zero_complete_date,
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
) as text)) AS last_update_time,
  (
  case
    when (
  case
    when o.issue_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.issue_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.issue_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.issue_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) is null then null

    -- 去除千分位逗号后，标准数值: 123, -456.78
    when regexp_replace((
  case
    when o.issue_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.issue_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.issue_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.issue_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.issue_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.issue_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.issue_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.issue_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g')::numeric

    -- 百分号: 72.4% → 72.4（保留原始数值，不除以100）
    when regexp_replace((
  case
    when o.issue_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.issue_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.issue_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.issue_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.issue_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.issue_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.issue_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.issue_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g')::numeric

    -- 带中文单位: 28天, 36.8亿, 12个 → 提取前面的数值
    when regexp_replace((
  case
    when o.issue_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.issue_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.issue_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.issue_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g') ~ '^-?\d+(\.\d+)?$'
         and (
  case
    when o.issue_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.issue_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.issue_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.issue_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) ~ '^\s*-?\d+\.?\d*\s*[^\d.\s]'
      then regexp_replace((
  case
    when o.issue_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.issue_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.issue_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.issue_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g')::numeric

    -- 科学计数法: 1.23E+05, 1.23e5, 1.23E-3
    when upper((
  case
    when o.issue_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.issue_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.issue_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.issue_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)) ~ '^-?\d+(\.\d+)?[Ee][+\-]?\d+$'
      then (
  case
    when o.issue_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.issue_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.issue_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.issue_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)::double precision::numeric

    -- 全角数字转半角: ０１２３ → 0123
    when translate((
  case
    when o.issue_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.issue_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.issue_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.issue_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.') ~ '^-?\d+(\.\d+)?$'
      then translate((
  case
    when o.issue_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.issue_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.issue_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.issue_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.')::numeric

    else null
  end
)::int AS issue_week,
  (
  case
    when (
  case
    when o.zero_complete_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.zero_complete_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.zero_complete_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.zero_complete_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) is null then null

    -- 去除千分位逗号后，标准数值: 123, -456.78
    when regexp_replace((
  case
    when o.zero_complete_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.zero_complete_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.zero_complete_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.zero_complete_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.zero_complete_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.zero_complete_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.zero_complete_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.zero_complete_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g')::numeric

    -- 百分号: 72.4% → 72.4（保留原始数值，不除以100）
    when regexp_replace((
  case
    when o.zero_complete_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.zero_complete_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.zero_complete_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.zero_complete_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.zero_complete_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.zero_complete_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.zero_complete_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.zero_complete_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g')::numeric

    -- 带中文单位: 28天, 36.8亿, 12个 → 提取前面的数值
    when regexp_replace((
  case
    when o.zero_complete_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.zero_complete_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.zero_complete_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.zero_complete_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g') ~ '^-?\d+(\.\d+)?$'
         and (
  case
    when o.zero_complete_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.zero_complete_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.zero_complete_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.zero_complete_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) ~ '^\s*-?\d+\.?\d*\s*[^\d.\s]'
      then regexp_replace((
  case
    when o.zero_complete_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.zero_complete_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.zero_complete_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.zero_complete_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g')::numeric

    -- 科学计数法: 1.23E+05, 1.23e5, 1.23E-3
    when upper((
  case
    when o.zero_complete_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.zero_complete_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.zero_complete_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.zero_complete_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)) ~ '^-?\d+(\.\d+)?[Ee][+\-]?\d+$'
      then (
  case
    when o.zero_complete_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.zero_complete_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.zero_complete_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.zero_complete_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)::double precision::numeric

    -- 全角数字转半角: ０１２３ → 0123
    when translate((
  case
    when o.zero_complete_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.zero_complete_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.zero_complete_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.zero_complete_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.') ~ '^-?\d+(\.\d+)?$'
      then translate((
  case
    when o.zero_complete_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.zero_complete_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.zero_complete_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.zero_complete_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.')::numeric

    else null
  end
)::int AS zero_complete_week,
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
)::int AS last_update_week
FROM "biadmin"."public"."ods_quality_issue_v2" o