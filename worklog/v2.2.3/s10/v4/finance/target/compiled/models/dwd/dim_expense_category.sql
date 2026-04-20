

SELECT expense_category_id, code, label, sort_order
FROM (
  VALUES
    ('expense_category_material', '原材料/设备', '原材料/设备', 1),
    ('expense_category_outsource', '外协/服务', '外协/服务', 2),
    ('expense_category_depreciation', '折旧', '折旧', 3),
    ('expense_category_testing', '检测试验', '检测试验', 4),
    ('expense_category_design', '设计咨询', '设计咨询', 5),
    ('expense_category_rental', '租赁', '租赁', 6),
    ('expense_category_training', '培训', '培训', 7),
    ('expense_category_other', '其他', '其他', 8)
) AS t(expense_category_id, code, label, sort_order)