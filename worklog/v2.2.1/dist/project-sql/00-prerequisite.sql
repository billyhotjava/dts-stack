-- ============================================================
-- 前置函数：安全解析日期
-- 若已存在可重复执行（CREATE OR REPLACE）
-- ============================================================

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

  -- Normalize separators: . and / → -
  v := replace(replace(v, '.', '-'), '/', '-');
  -- Strip time part if present (e.g. "2025-08-29 10:30:00")
  v := split_part(v, ' ', 1);

  -- YYYY-MM-DD  (2025-08-29, 2025-8-29)
  IF v ~ '^\d{4}-\d{1,2}-\d{1,2}$' THEN
    RETURN to_date(v, 'YYYY-MM-DD');
  END IF;

  -- YYYYMMDD  (20250829)
  IF v ~ '^\d{8}$' THEN
    RETURN to_date(v, 'YYYYMMDD');
  END IF;

  -- M/D/YYYY or D/M/YYYY  (8/29/2025, 29/8/2025)
  -- After normalization: 8-29-2025 or 29-8-2025
  IF v ~ '^\d{1,2}-\d{1,2}-\d{4}$' THEN
    parts := string_to_array(v, '-');
    p1 := parts[1]::int;  -- first number
    p2 := parts[2]::int;  -- second number
    p3 := parts[3]::int;  -- year

    -- Disambiguate: if p2 > 12, it must be a day → p1 is month (M/D/YYYY)
    IF p2 > 12 THEN
      RETURN make_date(p3, p1, p2);
    END IF;
    -- If p1 > 12, it must be a day → p2 is month (D/M/YYYY)
    IF p1 > 12 THEN
      RETURN make_date(p3, p2, p1);
    END IF;
    -- Both ≤ 12: ambiguous, default to M/D/YYYY (US format, common in Excel)
    RETURN make_date(p3, p1, p2);
  END IF;

  RETURN NULL;
END;
$$;
