-- ============================================================
-- 前置依赖：手动在 PostgreSQL 中执行一次
-- 目标库：dts_platform (与 dbt profile 一致)
-- ============================================================

-- parse_date_safe: 安全解析多种格式的日期字符串
--
-- 支持格式：
--   YYYY-MM-DD, YYYY/MM/DD, YYYY.MM.DD   (含时间部分自动截断)
--   YYYYMMDD
--   宽松模式：YYYY-M-D (月日不补零)
--
-- 无法解析时返回 NULL，不抛异常

CREATE OR REPLACE FUNCTION parse_date_safe(p_text text)
RETURNS date
LANGUAGE plpgsql
IMMUTABLE
AS $$
DECLARE
  v text;
BEGIN
  IF p_text IS NULL THEN
    RETURN NULL;
  END IF;

  v := btrim(p_text);
  IF v = '' THEN
    RETURN NULL;
  END IF;

  -- 统一分隔符：. 和 / 替换为 -
  v := replace(replace(v, '.', '-'), '/', '-');

  -- 去掉时间部分 (取空格前)
  v := split_part(v, ' ', 1);

  -- YYYY-M-D / YYYY-MM-DD
  IF v ~ '^\d{4}-\d{1,2}-\d{1,2}$' THEN
    RETURN to_date(v, 'YYYY-MM-DD');
  END IF;

  -- YYYYMMDD
  IF v ~ '^\d{8}$' THEN
    RETURN to_date(v, 'YYYYMMDD');
  END IF;

  RETURN NULL;
END;
$$;
