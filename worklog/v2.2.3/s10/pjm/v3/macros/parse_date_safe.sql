{% macro _make_valid_date(year_expr, month_expr, day_expr) -%}
(
  case
    when {{ year_expr }} between 1 and 9999
     and {{ month_expr }} between 1 and 12
     and {{ day_expr }} between 1 and (
       case
         when {{ month_expr }} in (1, 3, 5, 7, 8, 10, 12) then 31
         when {{ month_expr }} in (4, 6, 9, 11) then 30
         when {{ month_expr }} = 2 then
           case
             when mod({{ year_expr }}, 400) = 0
               or (mod({{ year_expr }}, 4) = 0 and mod({{ year_expr }}, 100) <> 0)
               then 29
             else 28
           end
         else 0
       end
     )
      then make_date({{ year_expr }}, {{ month_expr }}, {{ day_expr }})
    else null
  end
)
{%- endmacro %}

{% macro parse_date_safe(expr) -%}
(
  case
    when {{ expr }} is null then null
    when btrim(cast({{ expr }} as text)) = '' then null
    -- Excel 错误值直接返回 NULL
    when upper(btrim(cast({{ expr }} as text))) in ('#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!', 'N/A', 'NA', 'NULL', '-', '/', '--')
      then null
    when btrim(cast({{ expr }} as text)) ~ '^#[A-Z/]+[!?]?$' then null

    -- 标准格式: YYYY-MM-DD / YYYY-M-D
    when btrim(cast({{ expr }} as text)) ~ '^\d{4}-\d{1,2}-\d{1,2}$'
      then {{ _make_valid_date(
        "split_part(btrim(cast(" ~ expr ~ " as text)), '-', 1)::int",
        "split_part(btrim(cast(" ~ expr ~ " as text)), '-', 2)::int",
        "split_part(btrim(cast(" ~ expr ~ " as text)), '-', 3)::int"
      ) }}

    -- 带时间: YYYY-MM-DD HH:MM:SS / YYYY-M-D HH:MM:SS
    when btrim(cast({{ expr }} as text)) ~ '^\d{4}-\d{1,2}-\d{1,2}\s+.*$'
      then {{ _make_valid_date(
        "split_part(split_part(btrim(cast(" ~ expr ~ " as text)), ' ', 1), '-', 1)::int",
        "split_part(split_part(btrim(cast(" ~ expr ~ " as text)), ' ', 1), '-', 2)::int",
        "split_part(split_part(btrim(cast(" ~ expr ~ " as text)), ' ', 1), '-', 3)::int"
      ) }}

    -- 斜杠: YYYY/MM/DD / YYYY/M/D
    when btrim(cast({{ expr }} as text)) ~ '^\d{4}/\d{1,2}/\d{1,2}$'
      then {{ _make_valid_date(
        "split_part(replace(btrim(cast(" ~ expr ~ " as text)), '/', '-'), '-', 1)::int",
        "split_part(replace(btrim(cast(" ~ expr ~ " as text)), '/', '-'), '-', 2)::int",
        "split_part(replace(btrim(cast(" ~ expr ~ " as text)), '/', '-'), '-', 3)::int"
      ) }}
    when btrim(cast({{ expr }} as text)) ~ '^\d{4}/\d{1,2}/\d{1,2}\s+.*$'
      then {{ _make_valid_date(
        "split_part(replace(split_part(btrim(cast(" ~ expr ~ " as text)), ' ', 1), '/', '-'), '-', 1)::int",
        "split_part(replace(split_part(btrim(cast(" ~ expr ~ " as text)), ' ', 1), '/', '-'), '-', 2)::int",
        "split_part(replace(split_part(btrim(cast(" ~ expr ~ " as text)), ' ', 1), '/', '-'), '-', 3)::int"
      ) }}

    -- 点分隔: YYYY.MM.DD / YYYY.M.D
    when btrim(cast({{ expr }} as text)) ~ '^\d{4}\.\d{1,2}\.\d{1,2}$'
      then {{ _make_valid_date(
        "split_part(replace(btrim(cast(" ~ expr ~ " as text)), '.', '-'), '-', 1)::int",
        "split_part(replace(btrim(cast(" ~ expr ~ " as text)), '.', '-'), '-', 2)::int",
        "split_part(replace(btrim(cast(" ~ expr ~ " as text)), '.', '-'), '-', 3)::int"
      ) }}
    when btrim(cast({{ expr }} as text)) ~ '^\d{4}\.\d{1,2}\.\d{1,2}\s+.*$'
      then {{ _make_valid_date(
        "split_part(replace(split_part(btrim(cast(" ~ expr ~ " as text)), ' ', 1), '.', '-'), '-', 1)::int",
        "split_part(replace(split_part(btrim(cast(" ~ expr ~ " as text)), ' ', 1), '.', '-'), '-', 2)::int",
        "split_part(replace(split_part(btrim(cast(" ~ expr ~ " as text)), ' ', 1), '.', '-'), '-', 3)::int"
      ) }}

    -- 纯8位: YYYYMMDD
    when btrim(cast({{ expr }} as text)) ~ '^\d{8}$'
      then {{ _make_valid_date(
        "substr(btrim(cast(" ~ expr ~ " as text)), 1, 4)::int",
        "substr(btrim(cast(" ~ expr ~ " as text)), 5, 2)::int",
        "substr(btrim(cast(" ~ expr ~ " as text)), 7, 2)::int"
      ) }}

    -- 中文日期: 2025年3月15日 / 2025年03月15日
    when btrim(cast({{ expr }} as text)) ~ '^\d{4}年\d{1,2}月\d{1,2}日$'
      then {{ _make_valid_date(
        "split_part(regexp_replace(regexp_replace(regexp_replace(btrim(cast(" ~ expr ~ " as text)), '年', '-'), '月', '-'), '日', ''), '-', 1)::int",
        "split_part(regexp_replace(regexp_replace(regexp_replace(btrim(cast(" ~ expr ~ " as text)), '年', '-'), '月', '-'), '日', ''), '-', 2)::int",
        "split_part(regexp_replace(regexp_replace(regexp_replace(btrim(cast(" ~ expr ~ " as text)), '年', '-'), '月', '-'), '日', ''), '-', 3)::int"
      ) }}

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
