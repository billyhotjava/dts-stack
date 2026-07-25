

SELECT
  o.id AS source_row_id,
  'ods_tech_state_v2'::text AS source_table,
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
    when o.tech_state_name is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.tech_state_name as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.tech_state_name as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.tech_state_name as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS tech_state_name,
  (
  case
    when o.change_item is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.change_item as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.change_item as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.change_item as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS change_item,
  (
  case
    when o.owner is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.owner as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.owner as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.owner as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS owner,
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
    when o.change_reason is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.change_reason as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.change_reason as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.change_reason as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS change_reason,
  (
  case
    when o.affected_files is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.affected_files as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.affected_files as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.affected_files as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS affected_files,
  (
  case
    when o.affected_objects is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.affected_objects as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.affected_objects as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.affected_objects as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS affected_objects,
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
    when o.completion_signature is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.completion_signature as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.completion_signature as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.completion_signature as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS completion_signature_raw,
  (
  case
    when o.change_category is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.change_category as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.change_category as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.change_category as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS change_category_raw,
  (
  case
    when o.plan_synced is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_synced as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_synced as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_synced as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS plan_synced_raw,
  (
  case
    when o.review_situation is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.review_situation as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.review_situation as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.review_situation as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS review_situation_raw,
  (
  case
    when o.file_signature_status is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.file_signature_status as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.file_signature_status as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.file_signature_status as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS file_signature_status_raw,
  (
  case
    when o.reform_status is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.reform_status as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.reform_status as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.reform_status as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS reform_status_raw,

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
    when o.change_submit_time is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.change_submit_time as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.change_submit_time as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.change_submit_time as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) as text)) AS change_submit_time,
  public.dts_try_to_date(cast((
  case
    when o.signature_closure_date is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.signature_closure_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.signature_closure_date as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.signature_closure_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) as text)) AS signature_closure_date,
  public.dts_try_to_date(cast((
  case
    when o.plan_file_closure_date is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_file_closure_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_file_closure_date as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_file_closure_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) as text)) AS plan_file_closure_date,
  public.dts_try_to_date(cast((
  case
    when o.plan_reform_date is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_reform_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_reform_date as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_reform_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) as text)) AS plan_reform_date,
  public.dts_try_to_date(cast((
  case
    when o.file_signature_date is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.file_signature_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.file_signature_date as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.file_signature_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) as text)) AS file_signature_date,
  public.dts_try_to_date(cast((
  case
    when o.reform_date is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.reform_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.reform_date as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.reform_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) as text)) AS reform_date,
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
    when o.change_submit_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.change_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.change_submit_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.change_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) is null then null

    -- 去除千分位逗号后，标准数值: 123, -456.78
    when regexp_replace((
  case
    when o.change_submit_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.change_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.change_submit_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.change_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.change_submit_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.change_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.change_submit_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.change_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g')::numeric

    -- 百分号: 72.4% → 72.4（保留原始数值，不除以100）
    when regexp_replace((
  case
    when o.change_submit_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.change_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.change_submit_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.change_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.change_submit_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.change_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.change_submit_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.change_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g')::numeric

    -- 带中文单位: 28天, 36.8亿, 12个 → 提取前面的数值
    when regexp_replace((
  case
    when o.change_submit_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.change_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.change_submit_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.change_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g') ~ '^-?\d+(\.\d+)?$'
         and (
  case
    when o.change_submit_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.change_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.change_submit_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.change_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) ~ '^\s*-?\d+\.?\d*\s*[^\d.\s]'
      then regexp_replace((
  case
    when o.change_submit_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.change_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.change_submit_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.change_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g')::numeric

    -- 科学计数法: 1.23E+05, 1.23e5, 1.23E-3
    when upper((
  case
    when o.change_submit_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.change_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.change_submit_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.change_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)) ~ '^-?\d+(\.\d+)?[Ee][+\-]?\d+$'
      then (
  case
    when o.change_submit_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.change_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.change_submit_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.change_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)::double precision::numeric

    -- 全角数字转半角: ０１２３ → 0123
    when translate((
  case
    when o.change_submit_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.change_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.change_submit_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.change_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.') ~ '^-?\d+(\.\d+)?$'
      then translate((
  case
    when o.change_submit_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.change_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.change_submit_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.change_submit_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.')::numeric

    else null
  end
)::int AS change_submit_week,
  (
  case
    when (
  case
    when o.signature_closure_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.signature_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.signature_closure_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.signature_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) is null then null

    -- 去除千分位逗号后，标准数值: 123, -456.78
    when regexp_replace((
  case
    when o.signature_closure_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.signature_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.signature_closure_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.signature_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.signature_closure_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.signature_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.signature_closure_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.signature_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g')::numeric

    -- 百分号: 72.4% → 72.4（保留原始数值，不除以100）
    when regexp_replace((
  case
    when o.signature_closure_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.signature_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.signature_closure_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.signature_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.signature_closure_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.signature_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.signature_closure_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.signature_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g')::numeric

    -- 带中文单位: 28天, 36.8亿, 12个 → 提取前面的数值
    when regexp_replace((
  case
    when o.signature_closure_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.signature_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.signature_closure_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.signature_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g') ~ '^-?\d+(\.\d+)?$'
         and (
  case
    when o.signature_closure_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.signature_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.signature_closure_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.signature_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) ~ '^\s*-?\d+\.?\d*\s*[^\d.\s]'
      then regexp_replace((
  case
    when o.signature_closure_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.signature_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.signature_closure_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.signature_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g')::numeric

    -- 科学计数法: 1.23E+05, 1.23e5, 1.23E-3
    when upper((
  case
    when o.signature_closure_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.signature_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.signature_closure_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.signature_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)) ~ '^-?\d+(\.\d+)?[Ee][+\-]?\d+$'
      then (
  case
    when o.signature_closure_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.signature_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.signature_closure_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.signature_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)::double precision::numeric

    -- 全角数字转半角: ０１２３ → 0123
    when translate((
  case
    when o.signature_closure_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.signature_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.signature_closure_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.signature_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.') ~ '^-?\d+(\.\d+)?$'
      then translate((
  case
    when o.signature_closure_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.signature_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.signature_closure_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.signature_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.')::numeric

    else null
  end
)::int AS signature_closure_week,
  (
  case
    when (
  case
    when o.plan_file_closure_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_file_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_file_closure_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_file_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) is null then null

    -- 去除千分位逗号后，标准数值: 123, -456.78
    when regexp_replace((
  case
    when o.plan_file_closure_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_file_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_file_closure_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_file_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.plan_file_closure_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_file_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_file_closure_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_file_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g')::numeric

    -- 百分号: 72.4% → 72.4（保留原始数值，不除以100）
    when regexp_replace((
  case
    when o.plan_file_closure_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_file_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_file_closure_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_file_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.plan_file_closure_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_file_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_file_closure_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_file_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g')::numeric

    -- 带中文单位: 28天, 36.8亿, 12个 → 提取前面的数值
    when regexp_replace((
  case
    when o.plan_file_closure_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_file_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_file_closure_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_file_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g') ~ '^-?\d+(\.\d+)?$'
         and (
  case
    when o.plan_file_closure_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_file_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_file_closure_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_file_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) ~ '^\s*-?\d+\.?\d*\s*[^\d.\s]'
      then regexp_replace((
  case
    when o.plan_file_closure_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_file_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_file_closure_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_file_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g')::numeric

    -- 科学计数法: 1.23E+05, 1.23e5, 1.23E-3
    when upper((
  case
    when o.plan_file_closure_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_file_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_file_closure_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_file_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)) ~ '^-?\d+(\.\d+)?[Ee][+\-]?\d+$'
      then (
  case
    when o.plan_file_closure_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_file_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_file_closure_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_file_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)::double precision::numeric

    -- 全角数字转半角: ０１２３ → 0123
    when translate((
  case
    when o.plan_file_closure_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_file_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_file_closure_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_file_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.') ~ '^-?\d+(\.\d+)?$'
      then translate((
  case
    when o.plan_file_closure_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_file_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_file_closure_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_file_closure_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.')::numeric

    else null
  end
)::int AS plan_file_closure_week,
  (
  case
    when (
  case
    when o.plan_reform_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_reform_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) is null then null

    -- 去除千分位逗号后，标准数值: 123, -456.78
    when regexp_replace((
  case
    when o.plan_reform_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_reform_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.plan_reform_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_reform_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g')::numeric

    -- 百分号: 72.4% → 72.4（保留原始数值，不除以100）
    when regexp_replace((
  case
    when o.plan_reform_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_reform_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.plan_reform_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_reform_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g')::numeric

    -- 带中文单位: 28天, 36.8亿, 12个 → 提取前面的数值
    when regexp_replace((
  case
    when o.plan_reform_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_reform_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g') ~ '^-?\d+(\.\d+)?$'
         and (
  case
    when o.plan_reform_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_reform_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) ~ '^\s*-?\d+\.?\d*\s*[^\d.\s]'
      then regexp_replace((
  case
    when o.plan_reform_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_reform_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g')::numeric

    -- 科学计数法: 1.23E+05, 1.23e5, 1.23E-3
    when upper((
  case
    when o.plan_reform_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_reform_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)) ~ '^-?\d+(\.\d+)?[Ee][+\-]?\d+$'
      then (
  case
    when o.plan_reform_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_reform_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)::double precision::numeric

    -- 全角数字转半角: ０１２３ → 0123
    when translate((
  case
    when o.plan_reform_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_reform_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.') ~ '^-?\d+(\.\d+)?$'
      then translate((
  case
    when o.plan_reform_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_reform_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.')::numeric

    else null
  end
)::int AS plan_reform_week,
  (
  case
    when (
  case
    when o.file_signature_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.file_signature_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.file_signature_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.file_signature_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) is null then null

    -- 去除千分位逗号后，标准数值: 123, -456.78
    when regexp_replace((
  case
    when o.file_signature_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.file_signature_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.file_signature_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.file_signature_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.file_signature_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.file_signature_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.file_signature_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.file_signature_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g')::numeric

    -- 百分号: 72.4% → 72.4（保留原始数值，不除以100）
    when regexp_replace((
  case
    when o.file_signature_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.file_signature_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.file_signature_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.file_signature_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.file_signature_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.file_signature_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.file_signature_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.file_signature_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g')::numeric

    -- 带中文单位: 28天, 36.8亿, 12个 → 提取前面的数值
    when regexp_replace((
  case
    when o.file_signature_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.file_signature_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.file_signature_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.file_signature_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g') ~ '^-?\d+(\.\d+)?$'
         and (
  case
    when o.file_signature_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.file_signature_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.file_signature_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.file_signature_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) ~ '^\s*-?\d+\.?\d*\s*[^\d.\s]'
      then regexp_replace((
  case
    when o.file_signature_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.file_signature_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.file_signature_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.file_signature_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g')::numeric

    -- 科学计数法: 1.23E+05, 1.23e5, 1.23E-3
    when upper((
  case
    when o.file_signature_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.file_signature_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.file_signature_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.file_signature_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)) ~ '^-?\d+(\.\d+)?[Ee][+\-]?\d+$'
      then (
  case
    when o.file_signature_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.file_signature_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.file_signature_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.file_signature_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)::double precision::numeric

    -- 全角数字转半角: ０１２３ → 0123
    when translate((
  case
    when o.file_signature_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.file_signature_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.file_signature_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.file_signature_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.') ~ '^-?\d+(\.\d+)?$'
      then translate((
  case
    when o.file_signature_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.file_signature_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.file_signature_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.file_signature_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.')::numeric

    else null
  end
)::int AS file_signature_week,
  (
  case
    when (
  case
    when o.reform_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.reform_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) is null then null

    -- 去除千分位逗号后，标准数值: 123, -456.78
    when regexp_replace((
  case
    when o.reform_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.reform_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.reform_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.reform_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g')::numeric

    -- 百分号: 72.4% → 72.4（保留原始数值，不除以100）
    when regexp_replace((
  case
    when o.reform_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.reform_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.reform_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.reform_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g')::numeric

    -- 带中文单位: 28天, 36.8亿, 12个 → 提取前面的数值
    when regexp_replace((
  case
    when o.reform_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.reform_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g') ~ '^-?\d+(\.\d+)?$'
         and (
  case
    when o.reform_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.reform_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) ~ '^\s*-?\d+\.?\d*\s*[^\d.\s]'
      then regexp_replace((
  case
    when o.reform_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.reform_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g')::numeric

    -- 科学计数法: 1.23E+05, 1.23e5, 1.23E-3
    when upper((
  case
    when o.reform_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.reform_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)) ~ '^-?\d+(\.\d+)?[Ee][+\-]?\d+$'
      then (
  case
    when o.reform_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.reform_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)::double precision::numeric

    -- 全角数字转半角: ０１２３ → 0123
    when translate((
  case
    when o.reform_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.reform_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.') ~ '^-?\d+(\.\d+)?$'
      then translate((
  case
    when o.reform_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.reform_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.reform_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.')::numeric

    else null
  end
)::int AS reform_week,
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
FROM "biadmin"."public"."ods_tech_state_v2" o