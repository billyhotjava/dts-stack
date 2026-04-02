{{ config(materialized='table', tags=['project-management', 'dim', 'dwd']) }}

SELECT code, label, severity_rank
FROM (VALUES
  ('I',   'Ⅰ类更改', 3),
  ('Ⅰ',  'Ⅰ类更改', 3),
  ('II',  'Ⅱ类更改', 2),
  ('Ⅱ',  'Ⅱ类更改', 2),
  ('III', 'Ⅲ类更改', 1),
  ('Ⅲ',  'Ⅲ类更改', 1)
) AS t(code, label, severity_rank)
