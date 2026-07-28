-- PJM v4 deterministic golden dataset: exactly 55 rows.
--
-- Reserved IDs:
--   1001-1010   progress summary (10)
--   2001-2002   progress measures (2)
--   3001-3014   quality issues (14)
--   4001-4002   quality measures (2)
--   5001-5008   technical state (8)
--   6001-6002   technical-state measures (2)
--   7001-7008   risks (8)
--   8001-8002   risk measures (2)
--   9001-9002   material information (2)
--   10001-10005 budget (5)
--
-- Re-running this file only replaces rows in those reserved ranges.

USE dts_pjm_test;

SET NAMES utf8mb4;
START TRANSACTION;

DELETE FROM ods_project_subject_domain_v2 WHERE id BETWEEN 1001 AND 1010;
DELETE FROM ods_progress_measure_v2 WHERE id BETWEEN 2001 AND 2002;
DELETE FROM ods_quality_issue_v2 WHERE id BETWEEN 3001 AND 3014;
DELETE FROM ods_quality_measure_v2 WHERE id BETWEEN 4001 AND 4002;
DELETE FROM ods_tech_state_v2 WHERE id BETWEEN 5001 AND 5008;
DELETE FROM ods_tech_state_measure_v2 WHERE id BETWEEN 6001 AND 6002;
DELETE FROM ods_risk_info_v2 WHERE id BETWEEN 7001 AND 7008;
DELETE FROM ods_risk_measure_v2 WHERE id BETWEEN 8001 AND 8002;
DELETE FROM ods_material_info_v2 WHERE id BETWEEN 9001 AND 9002;
DELETE FROM ods_budget_v2
 WHERE id BETWEEN 10001 AND 10005
    OR budget_no LIKE 'BUD-GOLD-%';

-- 10 progress rows:
-- seven canonical completion statuses, all node/risk categories, an
-- outside-period completion, an unmatched-enum row and two rejected rows.
INSERT INTO ods_project_subject_domain_v2 (
  id, project_no, subsystem, node_task, plan_start_date, plan_date, plan_week,
  node_type, dept, completion_status, risk_level, actual_date, actual_week,
  delay_applied, last_update_time, last_update_week, filled_by
) VALUES
  (1001, 'PJM-B', '导航分系统', '二阶段方案评审', '2026-02-01', '2026/02/15', '７',
   '一般', '总体部', '正常待完成', '低', NULL, NULL,
   '未提交', '2026-02-01', '5', '张一'),
  (1002, 'PJM-A', '总体', '方案里程碑评审', '2026年1月1日', '2026年1月10日', '2周',
   '里程碑', '总体部', '按时完成', '低风险', '2026年1月10日', '2',
   '已提交', '2026-01-10', '2', '张二'),
  (1003, 'PJM-A', '控制分系统', '接口联调', '46000', '20260112', '3',
   '重要节点', '控制室', '超期已完成已变更', '高风险', '2026-01-20', '4',
   'YES', '2026-01-20', '4', '张三'),
  (1004, 'PJM-A', '结构分系统', '结构件交付', '2025-12-01', '2025-12-20', '51',
   '重大', '结构室', '超期已完成未变更', '中', '2026-01-05', '1',
   '否', '2026-01-05', '1', '张四'),
  (1005, 'PJM-A', '供配电分系统', '供配电设计冻结', '2026-01-01', '2026-01-05', '1',
   '里程碑节点', '电气室', '不正常待变更', '高', NULL, NULL,
   'N', '2026-01-31', '5', '张五'),
  (1006, 'PJM-A', '热控分系统', '热试验完成', '2026-01-01', '2026-01-08', '1.0E1',
   '重要', '热控室', '超期未完成未变更', '中风险', NULL, NULL,
   'FALSE', '2026-01-31', '5', '张六'),
  (1007, 'PJM-A', '软件分系统', '软件版本发布', '2026-01-01', '2026-01-15', '3',
   '重大节点', '软件室', '超期未完成已变更', '低', NULL, NULL,
   'TRUE', '2026-01-31', '5', '张七'),
  (1008, 'PJM-A', '试验分系统', '未知字典值验证', '2026.01.01', '2026.01.18', '3',
   '关键节点', '试验室', '等待确认', '极高', NULL, NULL,
   '待确认', '2026-01-31', '5', '张八'),
  (1009, 'PJM-REJECT-DATE', '测试分系统', '无效日期应被 DWD 过滤', '2026-01-01', '#N/A', '-',
   '一般节点', '测试室', '按时完成', '低', '2026-01-01', '1',
   '是', '2026-01-01', '1', '测试员'),
  (1010, NULL, '测试分系统', '空项目号应被 DWD 过滤', '2026-01-01', '2026-01-20', '4',
   '一般节点', '测试室', '正常待完成', '低', NULL, NULL,
   '否', '2026-01-20', '4', '测试员');

INSERT INTO ods_progress_measure_v2 (
  id, project_no, subsystem, node_task, plan_date, plan_week,
  completion_status, measure_category, measure_title, follow_up_person,
  follow_up_date, follow_up_week, closure_status, final_closure_date,
  closure_deliverable, risk_content, last_update_time, remark, filled_by
) VALUES
  (2001, 'PJM-A', '供配电分系统', '供配电设计冻结', '2026-01-05', '1',
   '不正常待变更', '计划协调', '完成延期变更审批', '李一',
   '2026-01-08', '2', '已闭环', '2026-01-12',
   '延期审批单', '审批延迟风险', '2026-01-12', '正常闭环样本', '李一'),
  (2002, 'PJM-A', '软件分系统', '软件版本发布', '#N/A', '-',
   '超期未完成已变更', '技术协调', 'API 占位符样本', '李二',
   '/', 'ＮＡ', '未闭环', NULL,
   NULL, NULL, 'NULL', '保留空值与占位符', '李二');

-- 14 quality rows:
-- ten open rows cover nine canonical categories plus one unknown category;
-- three rows cover all completed-zero states; one row is rejected.
INSERT INTO ods_quality_issue_v2 (
  id, project_no, subsystem, issue_name, dept, issue_date, issue_week,
  issue_summary, issue_category, zero_plan, zero_plan_synced, new_plan_count,
  `status`, current_progress, zero_complete_date, last_update_time,
  project_manager, filled_by
) VALUES
  (3001, 'PJM-A', '总体', '设计接口不一致', '总体部', '2026-01-03', '1',
   '接口定义存在差异', '设计', NULL, '未同步', '0',
   '处理中', '正在分析', NULL, '2026-01-31', '王经理', '质量员一'),
  (3002, 'PJM-A', '工艺', '装配工艺偏差', '工艺室', '2026/01/04', '1',
   '装配顺序需调整', '工艺', '无', '否', '０',
   '进行中', '制定工艺变更', NULL, '2026-01-31', '王经理', '质量员二'),
  (3003, 'PJM-A', '管理', '评审记录缺项', '管理室', '2026年1月5日', '1',
   '记录缺少签字', '管理', '补齐记录并复核', '已同步', '1',
   '未闭环', '等待责任人签字', NULL, '2026-01-31', '王经理', '质量员三'),
  (3004, 'PJM-A', '器件', '器件批次异常', '器件室', '20260106', '1',
   '抽检不合格', '元器件', '扩大抽检范围', 'YES', '2个',
   '待归零', '供应商复核中', NULL, '2026-01-31', '王经理', '质量员四'),
  (3005, 'PJM-A', '试验', '操作步骤遗漏', '试验室', '2026-01-07', '1',
   '操作票遗漏步骤', '操作', '修订操作票', 'Y', '1.0E1',
   '未完成', '修订中', NULL, '2026-01-31', '王经理', '质量员五'),
  (3006, 'PJM-A', '外协', '外协件尺寸超差', '采购室', '2026-01-08', '2',
   '来料尺寸不符', '外协外购', '-', 'N', '-',
   '未完成归零', '供应商返工', NULL, '2026-01-31', '王经理', '质量员六'),
  (3007, 'PJM-A', '软件', '算法边界错误', '软件室', '2026-01-09', '2',
   '边界条件处理错误', '软件', '修复并补充测试', 'TRUE', '3',
   '处理中', '代码修复中', NULL, '2026-01-31', '王经理', '质量员七'),
  (3008, 'PJM-A', '环境', '温度条件偏离', '环境室', '2026-01-10', '2',
   '环境温度超限', '环境', '调整环境控制', '是', '1',
   '处理中', '设备校准中', NULL, '2026-01-31', '王经理', '质量员八'),
  (3009, 'PJM-A', '其他', '资料编号错误', '文控室', '2026-01-11', '2',
   '编号重复', '其他', '更正资料编号', '已提交', '1',
   '处理中', '更正中', NULL, '2026-01-31', '王经理', '质量员九'),
  (3010, 'PJM-A', '未知', '未知分类验证', '测试室', '2026-01-12', '2',
   '验证未命中字典的 other 路径', '人员因素', '增加复核', 'NO', '1',
   '处理中', '测试中', NULL, '2026-01-31', '王经理', '质量员十'),
  (3011, 'PJM-A', '总体', '技术归零样本', '总体部', '2026-01-13', '3',
   '已完成技术归零', '设计', '完成技术复核', '已同步', '1',
   '已完成技术归零', '技术归零完成', '2026-01-20', '2026-01-31', '王经理', '质量员十一'),
  (3012, 'PJM-A', '管理', '管理归零样本', '管理室', '2026-01-14', '3',
   '已完成管理归零', '管理', '完成管理复核', '已同步', '1',
   '已完成管理归零', '管理归零完成', '2026-01-21', '2026-01-31', '王经理', '质量员十二'),
  (3013, 'PJM-A', '软件', '双归零样本', '软件室', '2026-01-15', '3',
   '技术和管理均已归零', '软件', '双线复核完成', '已同步', '2',
   '已完成', '全部归零完成', '2026-01-22', '2026-01-31', '王经理', '质量员十三'),
  (3014, NULL, '测试', '无效质量行', '测试室', '#VALUE!', '-',
   '空项目号与无效日期', '其他', NULL, NULL, NULL,
   '处理中', NULL, NULL, '2026-01-31', '测试经理', '测试员');

INSERT INTO ods_quality_measure_v2 (
  id, project_no, subsystem, issue_name, dept, issue_date, issue_category,
  zero_plan, zero_plan_synced, `status`, measure_category, measure_title,
  follow_up_person, follow_up_date, closure_status, final_closure_date,
  closure_deliverable, risk_content, last_update_time, remark, filled_by
) VALUES
  (4001, 'PJM-A', '总体', '设计接口不一致', '总体部', '2026-01-03', '设计',
   '完成接口复核', '已同步', '处理中', '质量协调', '组织接口联合评审',
   '赵一', '2026-01-15', '已闭环', '2026-01-20',
   '接口评审纪要', '接口延期风险', '2026-01-20', '正常样本', '赵一'),
  (4002, 'PJM-A', '软件', '算法边界错误', '软件室', '/', '软件',
   '-', '未同步', '未完成归零', '技术协调', '占位符验证',
   '赵二', '#N/A', '未闭环', NULL,
   NULL, NULL, 'NULL', '脏数据样本', '赵二');

-- 8 technical-state rows cover I/II/III/unknown categories, the five file
-- signature buckets, all review-signal patterns and reform states.
INSERT INTO ods_tech_state_v2 (
  id, project_no, tech_state_name, dept, change_submit_time,
  change_submit_week, completion_signature, change_category, plan_synced,
  new_plan_count, review_situation, file_signature_status, reform_status,
  reform_date, last_update_time, filled_by
) VALUES
  (5001, 'PJM-A', '总体接口状态项', '总体部', '2026-01-03',
   '1', '未签署', 'I', '未同步',
   '1', '需求已提出，尚未组织论证', '未签署', '整改中',
   NULL, '2026-01-31', '技术员一'),
  (5002, 'PJM-A', '控制接口状态项', '控制室', '2026/01/04',
   '1', '已签署', 'Ⅰ', '已同步',
   '1', '已完成评估评审并形成纪要', '已签署', '已完成',
   '2026-01-20', '2026-01-31', '技术员二'),
  (5003, 'PJM-A', '软件协议状态项', '软件室', '2026年1月5日',
   '1', '否', 'II', 'YES',
   '2', '已评审，等待签署', '已评估评审，未签署', '完成',
   '2026-01-21', '2026-01-31', '技术员三'),
  (5004, 'PJM-A', '结构材料状态项', '结构室', '20260106',
   '1', 'TRUE', 'Ⅱ', 'Y',
   '1', '评审通过，文件已签署', '已评估评审，已签署', '已落实整改',
   '2026-01-22', '2026-01-31', '技术员四'),
  (5005, 'PJM-A', '标识状态项', '文控室', '2026-01-07',
   '1', '未完成', 'III', 'NO',
   '0', 'III 类无需评估评审', '未签署', '不涉及',
   NULL, '2026-01-31', '技术员五'),
  (5006, 'PJM-A', '包装状态项', '文控室', '2026-01-08',
   '2', '已完成', 'Ⅲ', '已同步',
   '0', 'III 类直接签署', '已签署', '无',
   NULL, '2026-01-31', '技术员六'),
  (5007, 'PJM-A', '未知类别状态项', '测试室', '2026-01-09',
   '2', NULL, 'IV', '待确认',
   '-', NULL, '待提出', NULL,
   NULL, '2026-01-31', '技术员七'),
  (5008, NULL, '无效技术状态项', '测试室', '#N/A',
   '-', '否', 'I类', '否',
   NULL, NULL, '未签署', NULL,
   NULL, '2026-01-31', '测试员');

INSERT INTO ods_tech_state_measure_v2 (
  id, project_no, tech_state_name, change_item, dept, change_submit_time,
  completion_signature, change_category, plan_synced, file_signature_status,
  reform_status, measure_category, measure_title, follow_up_person,
  follow_up_date, closure_status, final_closure_date, closure_deliverable,
  risk_content, last_update_time, remark, filled_by
) VALUES
  (6001, 'PJM-A', '总体接口状态项', '接口定义更新', '总体部', '2026-01-03',
   '否', 'I', '已同步', '未签署',
   '整改中', '技术协调', '推动接口文件签署', '孙一',
   '2026-01-10', '已闭环', '2026-01-20', '签署版接口文件',
   '签署延期风险', '2026-01-20', '正常样本', '孙一'),
  (6002, 'PJM-A', '未知状态项', '占位符验证', '测试室', '/',
   'NULL', 'IV', '-', '#N/A',
   NULL, '测试', '脏数据样本', '孙二',
   '#VALUE!', '未闭环', NULL, NULL,
   NULL, 'NULL', '保留原始占位符', '孙二');

-- 8 risk rows cover six canonical categories, a NULL category, all risk
-- levels, released/open/NULL states and one rejected row.
INSERT INTO ods_risk_info_v2 (
  id, project_no, risk_name, subsystem, risk_submit_time, risk_submit_week,
  risk_phase, risk_category, risk_level, release_plan_synced,
  new_plan_count, dept, risk_status, risk_release_date,
  last_update_time, filled_by
) VALUES
  (7001, 'PJM-A', '接口技术风险', '总体', '2026-01-03', '1',
   '设计阶段', '技术风险', '高风险', '未同步',
   '2', '总体部', '监控中', NULL, '2026-01-31', '风险员一'),
  (7002, 'PJM-A', '关键件供应风险', '结构', '2026/01/04', '1',
   '采购阶段', '供应链', '中', '已同步',
   '1', '采购室', '已释放', '2026-01-25', '2026-01-31', '风险员二'),
  (7003, 'PJM-A', '成本增长风险', '总体', '2026年1月5日', '1',
   '实施阶段', '成本风险', '低风险', 'NO',
   '1', '财务室', '未释放', NULL, '2026-01-31', '风险员三'),
  (7004, 'PJM-A', '设计变更风险', '控制', '20260106', '1',
   '设计阶段', '设计', '高', 'YES',
   '1', '控制室', '已释放', '2026-01-28', '2026-01-31', '风险员四'),
  (7005, 'PJM-A', '质量归零风险', '软件', '2026-01-07', '1',
   '试验阶段', '质量风险', '中风险', 'N',
   '3', '软件室', '处理中', NULL, '2026-01-31', '风险员五'),
  (7006, 'PJM-A', '人员保障风险', '总体', '2026-01-08', '2',
   '实施阶段', '人员因素', '低', 'TRUE',
   '1', '人力室', '跟踪中', NULL, '2026-01-31', '风险员六'),
  (7007, 'PJM-A', '空分类风险', '测试', '2026-01-09', '2',
   '试验阶段', NULL, '高', NULL,
   '-', '测试室', NULL, NULL, '2026-01-31', '风险员七'),
  (7008, NULL, '无效风险行', '测试', '#N/A', '-',
   '测试阶段', '其他', '低', '否',
   NULL, '测试室', '未释放', NULL, '2026-01-31', '测试员');

INSERT INTO ods_risk_measure_v2 (
  id, project_no, risk_name, subsystem, risk_submit_time, risk_category,
  risk_level, release_plan_synced, risk_status, project_manager,
  measure_category, measure_title, follow_up_person, follow_up_date,
  closure_status, final_closure_date, closure_deliverable, risk_content,
  last_update_time, remark, filled_by
) VALUES
  (8001, 'PJM-A', '接口技术风险', '总体', '2026-01-03', '技术',
   '高', '已同步', '未释放', '王经理',
   '风险协调', '完成接口风险复核', '周一', '2026-01-15',
   '已闭环', '2026-01-25', '风险复核报告', '接口风险',
   '2026-01-25', '正常样本', '周一'),
  (8002, 'PJM-A', '占位符风险', '测试', '/', '未知',
   '#N/A', '-', 'NULL', '王经理',
   '测试', 'API 空值验证', '周二', '#VALUE!',
   '未闭环', NULL, NULL, NULL,
   'NULL', '脏数据样本', '周二');

INSERT INTO ods_material_info_v2 (
  id, project_no, subsystem, pbs_no, pbs_name, self_or_outsource,
  supplier_name, is_long_cycle, contract_delivery_date,
  actual_delivery_date, plan_inspect_date, complete_inspect_date,
  install_date, dept_owner, control_dept_owner, weekly_progress,
  affects_major_node, risk_level, risk_content, delay_impact,
  last_update_time, remark
) VALUES
  (9001, 'PJM-A', '结构分系统', 'PBS-001', '主承力结构件', '外协',
   '示例供应商甲', '是', '2026-01-20',
   '2026-01-18', '2026-01-21', '2026-01-22',
   '2026-01-25', '结构室/陈一', '采购室/陈二', '已提前到货并完成检验',
   '否', '低', '无', '无',
   '2026-01-25', '正常物料样本'),
  (9002, 'PJM-A', '控制分系统', 'PBS-002', '控制器关键芯片', '外购',
   '示例供应商乙', '是', '2026/01/10',
   NULL, '2026年1月15日', NULL,
   NULL, '控制室/陈三', '采购室/陈四', '供应商交期延迟',
   '是', '高风险', '芯片供应短缺', '影响重要节点',
   '2026-01-31', '延期高风险样本');

-- 5 budget rows: healthy, overrun, zero-budget/non-zero execution, dirty
-- numeric formats, and one row rejected by DWD because project_no is NULL.
INSERT INTO ods_budget_v2 (
  id, project_no, budget_no, subtopic, research_lab,
  budget_amount_adjusted, prepaid_amount, book_cost_amount, payable_amount
) VALUES
  (10001, 'PJM-A', 'BUD-GOLD-001', '总体方案', '总体部',
   '1000', '100', '300', '100'),
  (10002, 'PJM-A', 'BUD-GOLD-002', '控制系统', '控制室',
   '100', '60', '50', '20'),
  (10003, 'PJM-A', 'BUD-GOLD-003', '零预算临时任务', '测试室',
   '0', '0', '0', '10'),
  (10004, 'PJM-B', 'BUD-GOLD-004', '数值格式验证', '总体部',
   '1,000', '１０', '1.2E2', '-'),
  (10005, NULL, 'BUD-GOLD-005', '空项目号过滤样本', '测试室',
   '500', NULL, '#N/A', '20');

COMMIT;
