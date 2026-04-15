{% macro ensure_date_helpers() %}
CREATE OR REPLACE FUNCTION public.dts_try_to_date(v text) RETURNS date
LANGUAGE plpgsql IMMUTABLE AS $plpgsql$
DECLARE
  s text;
  n int;
  parts text[];
BEGIN
  IF v IS NULL THEN RETURN NULL; END IF;
  s := btrim(v);
  IF s = '' THEN RETURN NULL; END IF;
  IF upper(s) IN ('#N/A','#VALUE!','#DIV/0!','#REF!','#NAME?','#NULL!','#NUM!','N/A','NA','NULL','-','/','--') THEN
    RETURN NULL;
  END IF;
  IF s ~ '^#[A-Z/]+[!?]?$' THEN RETURN NULL; END IF;

  BEGIN
    IF s ~ '^\d{4}-\d{2}-\d{2}(\s+.*)?$' THEN
      RETURN make_date(substr(s,1,4)::int, substr(s,6,2)::int, substr(s,9,2)::int);
    ELSIF s ~ '^\d{4}/\d{2}/\d{2}(\s+.*)?$' THEN
      RETURN make_date(substr(s,1,4)::int, substr(s,6,2)::int, substr(s,9,2)::int);
    ELSIF s ~ '^\d{4}\.\d{2}\.\d{2}(\s+.*)?$' THEN
      RETURN make_date(substr(s,1,4)::int, substr(s,6,2)::int, substr(s,9,2)::int);
    ELSIF s ~ '^\d{8}$' THEN
      RETURN make_date(substr(s,1,4)::int, substr(s,5,2)::int, substr(s,7,2)::int);
    ELSIF s ~ '^\d{4}年\d{1,2}月\d{1,2}日$' THEN
      parts := regexp_match(s, '^(\d{4})年(\d{1,2})月(\d{1,2})日$');
      RETURN make_date(parts[1]::int, parts[2]::int, parts[3]::int);
    ELSIF s ~ '^\d{1,5}$' THEN
      n := s::int;
      IF n BETWEEN 1 AND 99999 THEN
        IF n <= 59 THEN RETURN date '1899-12-31' + n;
        ELSE RETURN date '1899-12-30' + n;
        END IF;
      END IF;
    ELSIF s ~ '^\d{1,5}\.\d+$' THEN
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
{% endmacro %}
