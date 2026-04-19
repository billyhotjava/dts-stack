

WITH cleaned AS (
  SELECT
    o.id AS source_row_id,
    'ods_risk_info_v2'::text AS source_table,
    COALESCE((
  case
    when o.source_system is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.source_system as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.source_system as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.source_system as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), 'excel') AS source_system,
    (
  case
    when o.source_file is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.source_file as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.source_file as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.source_file as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS source_file,
    (
  case
    when o.sheet_name is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.sheet_name as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.sheet_name as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.sheet_name as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS source_sheet_name,
    (
  case
    when o.batch_id is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.batch_id as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.batch_id as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.batch_id as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) AS source_batch_id,
    (
  case
    when (
  case
    when o.row_num is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.row_num as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.row_num as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.row_num as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) is null then null

    -- 去除千分位逗号后，标准数值: 123, -456.78
    when regexp_replace((
  case
    when o.row_num is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.row_num as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.row_num as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.row_num as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.row_num is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.row_num as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.row_num as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.row_num as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), ',', '', 'g')::numeric

    -- 百分号: 72.4% → 72.4（保留原始数值，不除以100）
    when regexp_replace((
  case
    when o.row_num is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.row_num as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.row_num as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.row_num as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when o.row_num is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.row_num as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.row_num as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.row_num as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[,%]', '', 'g')::numeric

    -- 带中文单位: 28天, 36.8亿, 12个 → 提取前面的数值
    when regexp_replace((
  case
    when o.row_num is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.row_num as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.row_num as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.row_num as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g') ~ '^-?\d+(\.\d+)?$'
         and (
  case
    when o.row_num is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.row_num as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.row_num as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.row_num as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
) ~ '^\s*-?\d+\.?\d*\s*[^\d.\s]'
      then regexp_replace((
  case
    when o.row_num is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.row_num as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.row_num as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.row_num as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '[^\d.\-]', '', 'g')::numeric

    -- 科学计数法: 1.23E+05, 1.23e5, 1.23E-3
    when upper((
  case
    when o.row_num is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.row_num as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.row_num as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.row_num as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)) ~ '^-?\d+(\.\d+)?[Ee][+\-]?\d+$'
      then (
  case
    when o.row_num is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.row_num as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.row_num as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.row_num as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
)::double precision::numeric

    -- 全角数字转半角: ０１２３ → 0123
    when translate((
  case
    when o.row_num is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.row_num as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.row_num as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.row_num as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.') ~ '^-?\d+(\.\d+)?$'
      then translate((
  case
    when o.row_num is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast(o.row_num as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast(o.row_num as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast(o.row_num as text), '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g')), '')
  end
), '０１２３４５６７８９．', '0123456789.')::numeric

    else null
  end
)::int AS source_row_num,
    o.import_time AS imported_at,

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
)

SELECT
  c.source_row_id,
  c.source_table,
  c.source_system,
  c.source_file,
  c.source_sheet_name,
  c.source_batch_id,
  c.source_row_num,
  c.imported_at,

  c.project_no,
  c.risk_name,
  c.subsystem,
  c.belonging_unit,
  c.risk_description,
  c.risk_phase,

  c.risk_category_raw,
  CASE
    WHEN c.risk_category_raw IN ('技术', '技术风险') THEN '技术'
    WHEN c.risk_category_raw IN ('进度', '进度风险', '供应链', '管理', '资源') THEN '进度'
    WHEN c.risk_category_raw IN ('成本', '成本风险') THEN '成本'
    WHEN c.risk_category_raw IN ('设计', '设计风险') THEN '设计'
    WHEN c.risk_category_raw IN ('质量', '质量风险') THEN '质量'
    WHEN c.risk_category_raw IS NULL THEN NULL
    ELSE '其他'
  END AS risk_category,

  c.risk_level_raw,
  CASE
    WHEN c.risk_level_raw IN ('高', '高风险') THEN '高'
    WHEN c.risk_level_raw IN ('中', '中风险') THEN '中'
    WHEN c.risk_level_raw IN ('低', '低风险') THEN '低'
    ELSE c.risk_level_raw
  END AS risk_level,

  c.impact_scope,
  c.response_measure,
  c.monthly_control_plan,
  c.weekly_release_plan,

  c.release_plan_synced_raw,
  CASE
    WHEN upper(COALESCE(c.release_plan_synced_raw, '')) IN ('Y', 'YES', 'TRUE', '是', '已同步') THEN '是'
    WHEN upper(COALESCE(c.release_plan_synced_raw, '')) IN ('N', 'NO', 'FALSE', '否', '未同步') THEN '否'
    ELSE c.release_plan_synced_raw
  END AS release_plan_synced,

  c.progress_situation,
  c.response_owner,
  c.control_owner,
  c.dept,

  c.risk_status_raw,
  CASE
    WHEN c.risk_status_raw = '已释放' THEN '已释放'
    WHEN c.risk_status_raw IS NULL THEN NULL
    ELSE '未释放'
  END AS risk_status,

  c.remark,
  c.filled_by,
  c.risk_submit_time_raw,
  c.new_plan_count,

  c.risk_submit_week,
  c.final_release_week,
  c.progress_stat_week,
  c.risk_release_week,
  c.last_update_week,

  c.risk_submit_date,
  c.final_release_date,
  c.progress_stat_date,
  c.risk_release_date,
  c.last_update_time,

  to_char(c.risk_submit_date, 'YYYY-MM') AS submit_month,
  to_char(c.risk_release_date, 'YYYY-MM') AS release_month,
  to_char(c.last_update_time, 'YYYY-MM') AS last_update_month
FROM cleaned c