

SELECT alias_raw, canonical_code
FROM (VALUES
  ('I',    'I'),
  ('Ⅰ',    'I'),
  ('I类',  'I'),
  ('1',    'I'),
  ('一',   'I'),
  ('II',   'II'),
  ('Ⅱ',    'II'),
  ('II类', 'II'),
  ('2',    'II'),
  ('二',   'II'),
  ('III',  'III'),
  ('Ⅲ',    'III'),
  ('III类','III'),
  ('3',    'III'),
  ('三',   'III')
) AS t(alias_raw, canonical_code)