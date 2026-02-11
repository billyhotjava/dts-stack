-- ============================================================
-- 前置依赖：手动在 PostgreSQL 中执行一次
-- 目标库：dts_platform (与 dbt profile 一致)
-- ============================================================

-- parse_date_safe: 安全解析多种格式的日期字符串
--
-- 支持格式：
--   YYYY-MM-DD, YYYY/MM/DD, YYYY.MM.DD   (含时间部分自动截断)
--   YYYYMMDD
--   M/D/YYYY, D/M/YYYY
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
  parts text[];
  p1 int; p2 int; p3 int;
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

  -- YYYY-MM-DD  (2025-08-29, 2025-8-29)
  IF v ~ '^\d{4}-\d{1,2}-\d{1,2}$' THEN
    RETURN to_date(v, 'YYYY-MM-DD');
  END IF;

  -- YYYYMMDD
  IF v ~ '^\d{8}$' THEN
    RETURN to_date(v, 'YYYYMMDD');
  END IF;

  -- M/D/YYYY or D/M/YYYY (8/29/2025, 29/8/2025)
  -- After normalization: 8-29-2025 or 29-8-2025
  IF v ~ '^\d{1,2}-\d{1,2}-\d{4}$' THEN
    parts := string_to_array(v, '-');
    p1 := parts[1]::int;
    p2 := parts[2]::int;
    p3 := parts[3]::int;

    -- If second segment > 12, it must be day -> M/D/YYYY
    IF p2 > 12 THEN
      RETURN make_date(p3, p1, p2);
    END IF;
    -- If first segment > 12, it must be day -> D/M/YYYY
    IF p1 > 12 THEN
      RETURN make_date(p3, p2, p1);
    END IF;
    -- Ambiguous case defaults to M/D/YYYY
    RETURN make_date(p3, p1, p2);
  END IF;

  RETURN NULL;
END;
$$;
