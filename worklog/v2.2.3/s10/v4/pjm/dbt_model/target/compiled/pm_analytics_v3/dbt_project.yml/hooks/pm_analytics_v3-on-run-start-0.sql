
CREATE OR REPLACE FUNCTION public.dts_try_to_date(v text) RETURNS date
LANGUAGE plpgsql IMMUTABLE AS $plpgsql$
DECLARE
  s text;
  n int;
  parts text[];
BEGIN
  IF v IS NULL THEN RETURN NULL; END IF;
  s := btrim(regexp_replace(v, '[\u00A0\uFEFF\u200B\u200C\u200D]', '', 'g'));
  IF s = '' THEN RETURN NULL; END IF;
  IF upper(s) IN ('#N/A','#VALUE!','#DIV/0!','#REF!','#NAME?','#NULL!','#NUM!','N/A','NA','NULL','-','/','--') THEN
    RETURN NULL;
  END IF;
  IF s ~ '^#[A-Z/]+[!?]?$' THEN RETURN NULL; END IF;

  BEGIN
    -- YYYY[-/.]M[-/.]D  (1-2 digit month/day, optional time suffix)
    parts := regexp_match(s, '^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})(?:[ T].*)?$');
    IF parts IS NOT NULL THEN
      RETURN make_date(parts[1]::int, parts[2]::int, parts[3]::int);
    END IF;

    -- YYYY年M月D日  (1-2 digit month/day, optional time suffix)
    parts := regexp_match(s, '^(\d{4})年(\d{1,2})月(\d{1,2})日(?:[ T].*)?$');
    IF parts IS NOT NULL THEN
      RETURN make_date(parts[1]::int, parts[2]::int, parts[3]::int);
    END IF;

    -- YYYYMMDD  (exactly 8 digits)
    parts := regexp_match(s, '^(\d{4})(\d{2})(\d{2})$');
    IF parts IS NOT NULL THEN
      RETURN make_date(parts[1]::int, parts[2]::int, parts[3]::int);
    END IF;

    -- Excel serial date (integer or decimal)
    IF s ~ '^\d{1,5}(\.\d+)?$' THEN
      n := split_part(s,'.',1)::int;
      IF n BETWEEN 1 AND 99999 THEN
        IF n <= 59 THEN RETURN date '1899-12-31' + n;
        ELSE RETURN date '1899-12-30' + n;
        END IF;
      END IF;
    END IF;

    RETURN NULL;
  EXCEPTION WHEN others THEN
    RETURN NULL;
  END;
END;
$plpgsql$;
