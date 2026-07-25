

SELECT change_category_id, code, label, is_cat_i, is_cat_ii, is_cat_iii, severity_rank
FROM (
  VALUES
    ('change_category_i', 'I', 'Ⅰ类更改', true,  false, false, 3),
    ('change_category_ii', 'II', 'Ⅱ类更改', false, true,  false, 2),
    ('change_category_iii', 'III', 'Ⅲ类更改', false, false, true,  1)
) AS t(change_category_id, code, label, is_cat_i, is_cat_ii, is_cat_iii, severity_rank)