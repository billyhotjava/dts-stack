{% macro nullif_placeholder(expr) -%}
(
  case
    when {{ expr }} is null then null
    -- 先清理不可见字符: BOM(U+FEFF), 零宽空格(U+200B/200C/200D), 不间断空格(U+00A0)
    when upper(btrim(regexp_replace(cast({{ expr }} as text), '[ ﻿​‌‍]', '', 'g')))
      in ('', '/', '-', '--', 'N/A', 'NA', 'NULL',
          '#N/A', '#VALUE!', '#DIV/0!', '#REF!', '#NAME?', '#NULL!', '#NUM!')
      then null
    -- 正则兜底: 所有 # 开头的 Excel 错误模式
    when btrim(cast({{ expr }} as text)) ~ '^#[A-Z/]+[!?]?$' then null
    else nullif(btrim(regexp_replace(cast({{ expr }} as text), '[ ﻿​‌‍]', '', 'g')), '')
  end
)
{%- endmacro %}
