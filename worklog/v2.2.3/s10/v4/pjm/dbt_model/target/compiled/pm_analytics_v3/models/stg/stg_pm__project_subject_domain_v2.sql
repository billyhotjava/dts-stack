

SELECT
  o.id AS source_row_id,
  'ods_project_subject_domain_v2'::text AS source_table,
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
    when o.node_task is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.node_task as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.node_task as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.node_task as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS node_task,
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
    when o.collab_dept is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.collab_dept as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.collab_dept as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.collab_dept as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS collab_dept,
  (
  case
    when o.supervisor_dept is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.supervisor_dept as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.supervisor_dept as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.supervisor_dept as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS supervisor_dept,
  (
  case
    when o.incomplete_reason is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.incomplete_reason as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.incomplete_reason as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.incomplete_reason as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS incomplete_reason,
  (
  case
    when o.risk_content is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.risk_content as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.risk_content as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.risk_content as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS risk_content,
  (
  case
    when o.delay_impact is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.delay_impact as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.delay_impact as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.delay_impact as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS delay_impact,
  (
  case
    when o.institute_leader is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.institute_leader as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.institute_leader as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.institute_leader as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS institute_leader,
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
    when o.highlight is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.highlight as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.highlight as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.highlight as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS highlight,
  (
  case
    when o.deliverable is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.deliverable as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.deliverable as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.deliverable as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS deliverable,

  (
  case
    when o.completion_status is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.completion_status as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.completion_status as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.completion_status as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS completion_status_raw,
  (
  case
    when o.node_type is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.node_type as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.node_type as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.node_type as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS node_type_raw,
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
    when o.delay_applied is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.delay_applied as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.delay_applied as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.delay_applied as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS delay_applied_raw,
  (
  case
    when o.source is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.source as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.source as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.source as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS data_source,

  (
  case
    when o.plan_date is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_date as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS plan_date_raw,
  public.dts_try_to_date(cast((
  case
    when o.plan_start_date is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_start_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_start_date as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_start_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) as text)) AS plan_start_date,
  public.dts_try_to_date(cast((
  case
    when o.plan_date is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_date as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) as text)) AS plan_date,
  public.dts_try_to_date(cast((
  case
    when o.actual_start_date is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.actual_start_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.actual_start_date as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.actual_start_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) as text)) AS actual_start_date,
  public.dts_try_to_date(cast((
  case
    when o.actual_date is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.actual_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.actual_date as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.actual_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) as text)) AS actual_date,
  public.dts_try_to_date(cast((
  case
    when o.delay_expected_date is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.delay_expected_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.delay_expected_date as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.delay_expected_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) as text)) AS delay_expected_date,
  public.dts_try_to_date(cast((
  case
    when o.original_plan_date is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.original_plan_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.original_plan_date as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.original_plan_date as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) as text)) AS original_plan_date,
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
    when o.plan_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) is null then null

    -- 去除千分位逗号后，标准数值: 123, -456.78
    when regexp_replace((
  case
    when o.plan_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.plan_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g')::numeric

    -- 百分号: 72.4% → 72.4（保留原始数值，不除以100）
    when regexp_replace((
  case
    when o.plan_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.plan_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g')::numeric

    -- 带中文单位: 28天, 36.8亿, 12个 → 提取前面的数值
    when regexp_replace((
  case
    when o.plan_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g') ~ '^-?\d+(\.\d+)?$'
         and (
  case
    when o.plan_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) ~ '^\s*-?\d+\.?\d*\s*[^\d.\s]'
      then regexp_replace((
  case
    when o.plan_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g')::numeric

    -- 科学计数法: 1.23E+05, 1.23e5, 1.23E-3
    when upper((
  case
    when o.plan_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)) ~ '^-?\d+(\.\d+)?[Ee][+\-]?\d+$'
      then (
  case
    when o.plan_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)::double precision::numeric

    -- 全角数字转半角: ０１２３ → 0123
    when translate((
  case
    when o.plan_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.') ~ '^-?\d+(\.\d+)?$'
      then translate((
  case
    when o.plan_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.plan_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.plan_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.plan_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.')::numeric

    else null
  end
)::int AS plan_week,
  (
  case
    when (
  case
    when o.actual_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.actual_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.actual_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.actual_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) is null then null

    -- 去除千分位逗号后，标准数值: 123, -456.78
    when regexp_replace((
  case
    when o.actual_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.actual_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.actual_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.actual_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.actual_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.actual_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.actual_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.actual_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g')::numeric

    -- 百分号: 72.4% → 72.4（保留原始数值，不除以100）
    when regexp_replace((
  case
    when o.actual_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.actual_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.actual_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.actual_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.actual_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.actual_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.actual_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.actual_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g')::numeric

    -- 带中文单位: 28天, 36.8亿, 12个 → 提取前面的数值
    when regexp_replace((
  case
    when o.actual_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.actual_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.actual_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.actual_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g') ~ '^-?\d+(\.\d+)?$'
         and (
  case
    when o.actual_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.actual_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.actual_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.actual_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) ~ '^\s*-?\d+\.?\d*\s*[^\d.\s]'
      then regexp_replace((
  case
    when o.actual_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.actual_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.actual_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.actual_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g')::numeric

    -- 科学计数法: 1.23E+05, 1.23e5, 1.23E-3
    when upper((
  case
    when o.actual_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.actual_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.actual_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.actual_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)) ~ '^-?\d+(\.\d+)?[Ee][+\-]?\d+$'
      then (
  case
    when o.actual_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.actual_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.actual_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.actual_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)::double precision::numeric

    -- 全角数字转半角: ０１２３ → 0123
    when translate((
  case
    when o.actual_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.actual_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.actual_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.actual_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.') ~ '^-?\d+(\.\d+)?$'
      then translate((
  case
    when o.actual_week is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.actual_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.actual_week as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.actual_week as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.')::numeric

    else null
  end
)::int AS actual_week,
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
FROM "biadmin"."public"."ods_project_subject_domain_v2" o