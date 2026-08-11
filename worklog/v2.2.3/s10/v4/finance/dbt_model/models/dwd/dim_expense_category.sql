{{ config(materialized='table', tags=['finance', 'dim', 'dwd']) }}

SELECT expense_category_id, code, raw_value, label, sort_order
FROM (
  VALUES
    ('expense_category_material', 'MATERIAL_EQUIPMENT', '原材料/设备', '原材料/设备', 1),
    ('expense_category_outsource', 'OUTSOURCING_SERVICE', '外协/服务', '外协/服务', 2),
    ('expense_category_depreciation', 'DEPRECIATION', '折旧', '折旧', 3),
    ('expense_category_testing', 'TESTING', '检测试验', '检测试验', 4),
    ('expense_category_design', 'DESIGN_CONSULTING', '设计咨询', '设计咨询', 5),
    ('expense_category_rental', 'RENTAL', '租赁', '租赁', 6),
    ('expense_category_training', 'TRAINING', '培训', '培训', 7),
    ('expense_category_other', 'OTHER', '其他', '其他', 8)
) AS t(expense_category_id, code, raw_value, label, sort_order)
