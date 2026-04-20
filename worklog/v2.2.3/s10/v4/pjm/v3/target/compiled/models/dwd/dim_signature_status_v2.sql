

SELECT signature_status_id, code, label, applies_to_category, is_reviewed, is_signed, sort_order
FROM (
  VALUES
    ('signature_status_i_ii_submitted_not_reviewed', '已提出需求，未评估评审', '已提出需求，未评估评审', 'I_II', false, false, 1),
    ('signature_status_i_ii_reviewed_not_signed', '已评估评审，未签署', '已评估评审，未签署', 'I_II', true,  false, 2),
    ('signature_status_i_ii_reviewed_signed', '已评估评审，已签署', '已评估评审，已签署', 'I_II', true,  true,  3),
    ('signature_status_iii_submitted_not_signed', '已提出需求，未签署', '已提出需求，未签署', 'III', false, false, 4),
    ('signature_status_iii_submitted_signed', '已提出需求，已签署', '已提出需求，已签署', 'III', false, true,  5)
) AS t(signature_status_id, code, label, applies_to_category, is_reviewed, is_signed, sort_order)