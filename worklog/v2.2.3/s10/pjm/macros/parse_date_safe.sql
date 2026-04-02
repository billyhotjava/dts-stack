{% macro parse_date_safe(expr) -%}
(
  case
    when {{ expr }} is null then null
    when btrim(cast({{ expr }} as text)) = '' then null
    -- Excel 错误值直接返回 NULL
    when upper(btrim(cast({{ expr }} as text))) in ('#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!', 'N/A', 'NA', 'NULL', '-', '/', '--')
      then null
    when btrim(cast({{ expr }} as text)) ~ '^#[A-Z/]+[!?]?$' then null

    -- 标准格式: YYYY-MM-DD
    when btrim(cast({{ expr }} as text)) ~ '^\d{4}-\d{2}-\d{2}$'
      then to_date(btrim(cast({{ expr }} as text)), 'YYYY-MM-DD')
    -- 带时间: YYYY-MM-DD HH:MM:SS
    when btrim(cast({{ expr }} as text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      then to_date(substr(btrim(cast({{ expr }} as text)), 1, 10), 'YYYY-MM-DD')
    -- 斜杠: YYYY/MM/DD
    when btrim(cast({{ expr }} as text)) ~ '^\d{4}/\d{2}/\d{2}$'
      then to_date(replace(btrim(cast({{ expr }} as text)), '/', '-'), 'YYYY-MM-DD')
    when btrim(cast({{ expr }} as text)) ~ '^\d{4}/\d{2}/\d{2}\s+.*$'
      then to_date(replace(substr(btrim(cast({{ expr }} as text)), 1, 10), '/', '-'), 'YYYY-MM-DD')
    -- 点分隔: YYYY.MM.DD
    when btrim(cast({{ expr }} as text)) ~ '^\d{4}\.\d{2}\.\d{2}$'
      then to_date(replace(btrim(cast({{ expr }} as text)), '.', '-'), 'YYYY-MM-DD')
    when btrim(cast({{ expr }} as text)) ~ '^\d{4}\.\d{2}\.\d{2}\s+.*$'
      then to_date(replace(substr(btrim(cast({{ expr }} as text)), 1, 10), '.', '-'), 'YYYY-MM-DD')
    -- 纯8位: YYYYMMDD
    when btrim(cast({{ expr }} as text)) ~ '^\d{8}$'
      then to_date(btrim(cast({{ expr }} as text)), 'YYYYMMDD')

    -- 中文日期: 2025年3月15日 / 2025年03月15日
    when btrim(cast({{ expr }} as text)) ~ '^\d{4}年\d{1,2}月\d{1,2}日$'
      then to_date(
        regexp_replace(
          regexp_replace(
            regexp_replace(btrim(cast({{ expr }} as text)), '年', '-'),
            '月', '-'),
          '日', ''),
        'YYYY-MM-DD')

    -- Excel 日期序列号 (1~99999 范围，起点 1900-01-01，序列号 1 = 1900-01-01)
    -- 注意: Excel 有 1900-02-29 的 bug，序列号 > 59 需要减 1
    when btrim(cast({{ expr }} as text)) ~ '^\d{1,5}$'
         and btrim(cast({{ expr }} as text))::int between 1 and 99999
      then (
        case
          when btrim(cast({{ expr }} as text))::int <= 59
            then date '1899-12-31' + btrim(cast({{ expr }} as text))::int
          else date '1899-12-30' + btrim(cast({{ expr }} as text))::int
        end
      )
    -- Excel 日期序列号带小数 (含时间部分，如 45678.625)
    when btrim(cast({{ expr }} as text)) ~ '^\d{1,5}\.\d+$'
         and split_part(btrim(cast({{ expr }} as text)), '.', 1)::int between 1 and 99999
      then (
        case
          when split_part(btrim(cast({{ expr }} as text)), '.', 1)::int <= 59
            then date '1899-12-31' + split_part(btrim(cast({{ expr }} as text)), '.', 1)::int
          else date '1899-12-30' + split_part(btrim(cast({{ expr }} as text)), '.', 1)::int
        end
      )

    else null
  end
)
{%- endmacro %}
