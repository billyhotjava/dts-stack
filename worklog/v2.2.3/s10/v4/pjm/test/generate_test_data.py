from __future__ import annotations

import csv
from datetime import date, timedelta
from pathlib import Path

from openpyxl import Workbook


TEST_DIR = Path(__file__).resolve().parent


PROJECTS = [
    {
        "project_no": "XM-2026-C01",
        "theme": "综合统筹平台",
        "subsystem": "总体分系统",
        "dept": "总体室",
        "dept_leader": "张总",
        "owner": "王工",
        "team_leader": "王工团队",
        "project_manager": "孙PM",
        "institute_leader": "李所",
        "filled_by": "王工",
        "key_item": "综合控制机",
        "pbs_no": "PBS-C01-001",
        "pbs_name": "综合控制机机箱",
        "self_or_outsource": "自研",
        "supplier_name": "—",
    },
    {
        "project_no": "XM-2026-C02",
        "theme": "飞控执行机构",
        "subsystem": "控制分系统",
        "dept": "控制室",
        "dept_leader": "赵总",
        "owner": "陈工",
        "team_leader": "陈工团队",
        "project_manager": "陈PM",
        "institute_leader": "李所",
        "filled_by": "陈工",
        "key_item": "飞控控制器",
        "pbs_no": "PBS-C02-001",
        "pbs_name": "飞控控制器样机",
        "self_or_outsource": "外协",
        "supplier_name": "中航智控",
    },
    {
        "project_no": "XM-2026-C03",
        "theme": "姿轨动力系统",
        "subsystem": "动力分系统",
        "dept": "动力室",
        "dept_leader": "孙总",
        "owner": "赵工",
        "team_leader": "赵工团队",
        "project_manager": "赵PM",
        "institute_leader": "李所",
        "filled_by": "赵工",
        "key_item": "动力控制组件",
        "pbs_no": "PBS-C03-001",
        "pbs_name": "动力控制组件",
        "self_or_outsource": "外协",
        "supplier_name": "航天动力",
    },
    {
        "project_no": "XM-2026-C04",
        "theme": "惯导制导系统",
        "subsystem": "制导分系统",
        "dept": "制导室",
        "dept_leader": "马总",
        "owner": "林工",
        "team_leader": "林工团队",
        "project_manager": "林PM",
        "institute_leader": "李所",
        "filled_by": "林工",
        "key_item": "制导计算机",
        "pbs_no": "PBS-C04-001",
        "pbs_name": "制导计算机板卡",
        "self_or_outsource": "外协",
        "supplier_name": "中电十三所",
    },
    {
        "project_no": "XM-2026-C05",
        "theme": "实时软件平台",
        "subsystem": "软件分系统",
        "dept": "软件室",
        "dept_leader": "胡总",
        "owner": "沈工",
        "team_leader": "沈工团队",
        "project_manager": "沈PM",
        "institute_leader": "李所",
        "filled_by": "沈工",
        "key_item": "实时软件构件",
        "pbs_no": "PBS-C05-001",
        "pbs_name": "实时软件构件包",
        "self_or_outsource": "自研",
        "supplier_name": "—",
    },
    {
        "project_no": "XM-2026-C06",
        "theme": "复合结构组件",
        "subsystem": "结构分系统",
        "dept": "结构室",
        "dept_leader": "吴总",
        "owner": "周工",
        "team_leader": "周工团队",
        "project_manager": "周PM",
        "institute_leader": "李所",
        "filled_by": "周工",
        "key_item": "主承力舱段",
        "pbs_no": "PBS-C06-001",
        "pbs_name": "主承力舱段结构件",
        "self_or_outsource": "外协",
        "supplier_name": "江南机械",
    },
    {
        "project_no": "XM-2026-C07",
        "theme": "高可靠电源系统",
        "subsystem": "电源分系统",
        "dept": "电源室",
        "dept_leader": "高总",
        "owner": "宋工",
        "team_leader": "宋工团队",
        "project_manager": "宋PM",
        "institute_leader": "李所",
        "filled_by": "宋工",
        "key_item": "电源管理单元",
        "pbs_no": "PBS-C07-001",
        "pbs_name": "电源管理单元",
        "self_or_outsource": "自研",
        "supplier_name": "—",
    },
    {
        "project_no": "XM-2026-C08",
        "theme": "电子载荷组件",
        "subsystem": "电子分系统",
        "dept": "电子室",
        "dept_leader": "郑总",
        "owner": "徐工",
        "team_leader": "徐工团队",
        "project_manager": "徐PM",
        "institute_leader": "李所",
        "filled_by": "徐工",
        "key_item": "载荷处理模块",
        "pbs_no": "PBS-C08-001",
        "pbs_name": "载荷处理模块",
        "self_or_outsource": "外协",
        "supplier_name": "航电电子",
    },
    {
        "project_no": "XM-2026-C09",
        "theme": "测控链路系统",
        "subsystem": "测控分系统",
        "dept": "测控室",
        "dept_leader": "潘总",
        "owner": "田工",
        "team_leader": "田工团队",
        "project_manager": "田PM",
        "institute_leader": "李所",
        "filled_by": "田工",
        "key_item": "测控主机",
        "pbs_no": "PBS-C09-001",
        "pbs_name": "测控主机样机",
        "self_or_outsource": "自研",
        "supplier_name": "—",
    },
    {
        "project_no": "XM-2026-C10",
        "theme": "环境试验保障平台",
        "subsystem": "试验分系统",
        "dept": "试验室",
        "dept_leader": "曹总",
        "owner": "柳工",
        "team_leader": "柳工团队",
        "project_manager": "柳PM",
        "institute_leader": "李所",
        "filled_by": "柳工",
        "key_item": "综合试验工装",
        "pbs_no": "PBS-C10-001",
        "pbs_name": "综合试验工装",
        "self_or_outsource": "外协",
        "supplier_name": "航试装备",
    },
]

DEPT_ORDER = [project["dept"] for project in PROJECTS]

QUALITY_CATEGORIES = ["设计", "工艺", "管理", "元器件", "操作", "外协", "软件", "环境", "其他", "设计"]
QUALITY_STATUS_CYCLE = ["已完成技术和管理归零", "已完成技术归零", "已完成管理归零", "未完成归零"]
RISK_CATEGORY_CYCLE = ["技术", "进度", "成本", "设计", "质量", "其他", "技术", "进度", "质量", "设计"]
RISK_LEVEL_CYCLE = ["高", "中", "低", "高", "中", "低", "高", "中", "低", "高"]

TECH_SCENARIOS = [
    {
        "change_category": "I",
        "completion_signature": "是",
        "file_signature_status": "已评估评审，已签署",
        "review_situation": "已评估评审已通过",
        "reform_status": "已落实整改",
        "plan_synced": "是",
        "signed": True,
        "reform_done": True,
    },
    {
        "change_category": "II",
        "completion_signature": "否",
        "file_signature_status": "已提出需求，未评估评审",
        "review_situation": "已提出需求，等待评估评审",
        "reform_status": "整改中",
        "plan_synced": "是",
        "signed": False,
        "reform_done": False,
    },
    {
        "change_category": "III",
        "completion_signature": "是",
        "file_signature_status": "已提出需求，已签署",
        "review_situation": "已提出需求，已签署",
        "reform_status": "不涉及",
        "plan_synced": "否",
        "signed": True,
        "reform_done": False,
    },
    {
        "change_category": "I",
        "completion_signature": "否",
        "file_signature_status": "已评估评审，未签署",
        "review_situation": "已评估评审已通过",
        "reform_status": "整改中",
        "plan_synced": "是",
        "signed": False,
        "reform_done": False,
    },
    {
        "change_category": "II",
        "completion_signature": "是",
        "file_signature_status": "已评估评审，已签署",
        "review_situation": "已评估评审已通过",
        "reform_status": "已落实整改",
        "plan_synced": "是",
        "signed": True,
        "reform_done": True,
    },
    {
        "change_category": "III",
        "completion_signature": "否",
        "file_signature_status": "已提出需求，未签署",
        "review_situation": "已提出需求，未签署",
        "reform_status": "不涉及",
        "plan_synced": "否",
        "signed": False,
        "reform_done": False,
    },
    {
        "change_category": "I",
        "completion_signature": "否",
        "file_signature_status": "已提出需求，未评估评审",
        "review_situation": "已提出需求，等待评估评审",
        "reform_status": "整改中",
        "plan_synced": "是",
        "signed": False,
        "reform_done": False,
    },
    {
        "change_category": "II",
        "completion_signature": "是",
        "file_signature_status": "已评估评审，已签署",
        "review_situation": "已评估评审已通过",
        "reform_status": "已落实整改",
        "plan_synced": "是",
        "signed": True,
        "reform_done": True,
    },
    {
        "change_category": "III",
        "completion_signature": "是",
        "file_signature_status": "已提出需求，已签署",
        "review_situation": "已提出需求，已签署",
        "reform_status": "不涉及",
        "plan_synced": "否",
        "signed": True,
        "reform_done": False,
    },
    {
        "change_category": "I",
        "completion_signature": "否",
        "file_signature_status": "已评估评审，未签署",
        "review_situation": "已评估评审已通过",
        "reform_status": "整改中",
        "plan_synced": "是",
        "signed": False,
        "reform_done": False,
    },
]

QUALITY_SUMMARIES = {
    "设计": "接口边界收敛不充分，联调输入条件发生变化",
    "工艺": "加工工艺窗口偏窄，首件一致性波动",
    "管理": "跨部门接口闭环时序滞后，问题流转不及时",
    "元器件": "关键器件批次一致性不足，筛选后仍存在漂移",
    "操作": "试验操作顺序不规范，引入重复性异常",
    "外协": "外协件返修周期超预期，影响到货节奏",
    "软件": "实时任务调度冲突，长稳运行存在异常",
    "环境": "高低温应力下性能裕度下降，边界条件暴露",
    "其他": "现场条件变化引起的综合性异常待进一步定位",
}

RISK_DESCRIPTIONS = {
    "技术": "关键技术状态尚未完全收敛，存在方案返工概率",
    "进度": "关键节点资源排布偏紧，存在串行等待风险",
    "成本": "关键件替代方案成本抬升，预算承压",
    "设计": "设计边界条件变化，需补充论证与验证",
    "质量": "关键件一致性波动，影响批量交付稳定性",
    "其他": "外部条件与配套环境变化带来不确定性",
}

RISK_MEASURES = {
    "技术": "组织专题评审并增加仿真验证轮次",
    "进度": "拉通周计划并前置关键资源排产",
    "成本": "同步替代件报价与降本方案",
    "设计": "冻结接口边界并补充设计复核",
    "质量": "补充筛选试验并闭环质量问题",
    "其他": "建立专项台账并按周通报处置",
}


CSV_SCHEMAS = {
    "ods_project_subject_domain_v2": [
        "project_no",
        "subsystem",
        "node_task",
        "plan_start_date",
        "plan_date",
        "plan_week",
        "deliverable",
        "node_type",
        "owner",
        "dept",
        "dept_leader",
        "completion_status",
        "collab_dept",
        "supervisor_dept",
        "delay_expected_date",
        "incomplete_reason",
        "risk_level",
        "risk_content",
        "delay_impact",
        "actual_start_date",
        "actual_date",
        "actual_week",
        "institute_leader",
        "source",
        "original_plan_date",
        "delay_days_changed",
        "delay_days_unchanged",
        "delay_applied",
        "project_manager",
        "last_update_time",
        "last_update_week",
        "filled_by",
        "highlight",
    ],
    "ods_progress_measure_v2": [
        "project_no",
        "subsystem",
        "node_task",
        "plan_date",
        "plan_week",
        "completion_status",
        "measure_category",
        "measure_title",
        "follow_up_person",
        "main_recipient",
        "cc_recipient",
        "follow_up_date",
        "follow_up_week",
        "closure_status",
        "final_closure_date",
        "final_closure_week",
        "closure_deliverable_type",
        "closure_deliverable",
        "risk_content",
        "last_update_time",
        "last_update_week",
        "remark",
        "filled_by",
    ],
    "ods_quality_issue_v2": [
        "project_no",
        "subsystem",
        "issue_name",
        "dept",
        "team_leader",
        "dept_leader",
        "issue_date",
        "issue_week",
        "issue_summary",
        "issue_category",
        "zero_plan",
        "zero_plan_synced",
        "new_plan_count",
        "status",
        "current_progress",
        "zero_complete_date",
        "zero_complete_week",
        "last_update_time",
        "last_update_week",
        "project_manager",
        "filled_by",
    ],
    "ods_quality_measure_v2": [
        "project_no",
        "subsystem",
        "issue_name",
        "dept",
        "team_leader",
        "dept_leader",
        "issue_date",
        "issue_week",
        "issue_summary",
        "issue_category",
        "zero_plan",
        "zero_plan_synced",
        "new_plan_count",
        "status",
        "current_progress",
        "project_manager",
        "measure_category",
        "measure_title",
        "follow_up_person",
        "main_recipient",
        "cc_recipient",
        "follow_up_date",
        "follow_up_week",
        "closure_status",
        "final_closure_date",
        "final_closure_week",
        "closure_deliverable_type",
        "closure_deliverable",
        "risk_content",
        "last_update_time",
        "last_update_week",
        "remark",
        "filled_by",
    ],
    "ods_tech_state_v2": [
        "project_no",
        "tech_state_name",
        "change_item",
        "owner",
        "dept",
        "dept_leader",
        "change_submit_time",
        "change_submit_week",
        "completion_signature",
        "signature_closure_date",
        "signature_closure_week",
        "change_reason",
        "change_category",
        "plan_file_closure_date",
        "plan_file_closure_week",
        "plan_reform_date",
        "plan_reform_week",
        "plan_synced",
        "new_plan_count",
        "review_situation",
        "affected_files",
        "affected_objects",
        "file_signature_status",
        "file_signature_date",
        "file_signature_week",
        "reform_status",
        "reform_date",
        "reform_week",
        "project_manager",
        "last_update_time",
        "last_update_week",
        "filled_by",
        "remark",
    ],
    "ods_tech_state_measure_v2": [
        "project_no",
        "tech_state_name",
        "change_item",
        "owner",
        "dept",
        "dept_leader",
        "change_submit_time",
        "change_submit_week",
        "completion_signature",
        "signature_closure_date",
        "signature_closure_week",
        "change_reason",
        "change_category",
        "plan_file_closure_date",
        "plan_file_closure_week",
        "plan_reform_date",
        "plan_reform_week",
        "plan_synced",
        "new_plan_count",
        "affected_files",
        "affected_objects",
        "file_signature_status",
        "reform_status",
        "project_manager",
        "measure_category",
        "measure_title",
        "follow_up_person",
        "main_recipient",
        "cc_recipient",
        "follow_up_date",
        "follow_up_week",
        "closure_status",
        "final_closure_date",
        "final_closure_week",
        "closure_deliverable_type",
        "closure_deliverable",
        "risk_content",
        "last_update_time",
        "last_update_week",
        "remark",
        "filled_by",
    ],
    "ods_risk_info_v2": [
        "project_no",
        "risk_name",
        "subsystem",
        "belonging_unit",
        "risk_description",
        "risk_submit_time",
        "risk_submit_week",
        "risk_phase",
        "risk_category",
        "risk_level",
        "impact_scope",
        "response_measure",
        "final_release_time",
        "final_release_week",
        "monthly_control_plan",
        "weekly_release_plan",
        "release_plan_synced",
        "new_plan_count",
        "progress_stat_time",
        "progress_stat_week",
        "progress_situation",
        "response_owner",
        "control_owner",
        "dept",
        "risk_status",
        "risk_release_date",
        "risk_release_week",
        "remark",
        "last_update_time",
        "last_update_week",
        "filled_by",
    ],
    "ods_risk_measure_v2": [
        "project_no",
        "risk_name",
        "subsystem",
        "belonging_unit",
        "risk_description",
        "risk_submit_time",
        "risk_submit_week",
        "risk_phase",
        "risk_category",
        "risk_level",
        "impact_scope",
        "response_measure",
        "final_release_time",
        "monthly_control_plan",
        "weekly_release_plan",
        "release_plan_synced",
        "new_plan_count",
        "progress_stat_time",
        "progress_stat_week",
        "progress_situation",
        "response_owner",
        "control_owner",
        "dept",
        "risk_status",
        "project_manager",
        "measure_category",
        "measure_title",
        "follow_up_person",
        "main_recipient",
        "cc_recipient",
        "follow_up_date",
        "follow_up_week",
        "closure_status",
        "final_closure_date",
        "final_closure_week",
        "closure_deliverable_type",
        "closure_deliverable",
        "risk_content",
        "last_update_time",
        "last_update_week",
        "remark",
        "filled_by",
    ],
    "ods_material_info_v2": [
        "project_no",
        "subsystem",
        "pbs_no",
        "pbs_name",
        "self_or_outsource",
        "supplier_name",
        "is_long_cycle",
        "contract_negotiation_date",
        "contract_negotiation_week",
        "contract_delivery_date",
        "contract_delivery_week",
        "actual_delivery_date",
        "actual_delivery_week",
        "plan_inspect_date",
        "plan_inspect_week",
        "complete_inspect_date",
        "complete_inspect_week",
        "install_date",
        "install_week",
        "dept_owner",
        "control_dept_owner",
        "weekly_progress",
        "affects_major_node",
        "risk_level",
        "risk_content",
        "delay_impact",
        "last_update_time",
        "last_update_week",
        "remark",
    ],
}


def fmt(d: date | None) -> str:
    return d.isoformat() if d else ""


def week_of(d: date | None) -> str:
    return str(d.isocalendar().week) if d else ""


def format_days(days: int | None) -> str:
    return str(days) if days is not None else ""


def collab_dept(idx: int) -> str:
    deps = [DEPT_ORDER[(idx + 1) % len(DEPT_ORDER)], DEPT_ORDER[(idx + 2) % len(DEPT_ORDER)]]
    return ";".join(deps)


def build_progress_rows() -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for idx, project in enumerate(PROJECTS):
        base_start = date(2026, idx + 1, 6)
        node_specs = [
            ("需求基线评审", "需求基线包", "里程碑", "按时完成"),
            ("详细设计评审", "详细设计报告", "重要节点", "超期已完成已变更" if idx % 2 else "超期已完成未变更"),
            ("关键件交付", project["pbs_name"], "重大节点", "正常待完成"),
            ("联调准备", "联调准备报告", "重要节点", "超期未完成已变更" if idx % 2 else "超期未完成未变更"),
            ("综合联试", "综合联试纪要", "里程碑", "不正常待变更"),
        ]

        for node_idx, (task, deliverable, node_type, status) in enumerate(node_specs):
            plan_start = base_start + timedelta(days=node_idx * 49)
            plan_date = plan_start + timedelta(days=32 + node_idx)
            actual_start = plan_start if status != "正常待完成" else None
            actual_date: date | None = None
            last_update: date | None = None
            delay_expected: date | None = None
            original_plan_date = ""
            delay_days_changed = ""
            delay_days_unchanged = ""
            delay_applied = "否"
            incomplete_reason = ""
            risk_level = ""
            risk_content = ""
            delay_impact = ""
            highlight = ""

            if status == "按时完成":
                actual_date = plan_date - timedelta(days=1)
                last_update = actual_date
                highlight = f"{project['theme']} {task}一次通过"
            elif status == "超期已完成未变更":
                delay_days = 5 + idx % 3
                actual_date = plan_date + timedelta(days=delay_days)
                last_update = actual_date
                delay_days_unchanged = format_days(delay_days)
                delay_applied = "是"
                incomplete_reason = "跨专业接口澄清耗时"
                risk_level = "中"
                risk_content = f"{project['theme']}接口收敛偏慢"
                delay_impact = f"影响后续任务{delay_days}天"
            elif status == "超期已完成已变更":
                delay_days = 6 + idx % 4
                actual_date = plan_date + timedelta(days=delay_days)
                last_update = actual_date
                original_plan_date = fmt(plan_date)
                delay_days_changed = format_days(delay_days)
                delay_applied = "是"
                incomplete_reason = "新增评审意见需要补充闭环"
                risk_level = "中"
                risk_content = f"{project['theme']}设计闭环补充验证"
                delay_impact = f"影响后续任务{delay_days}天"
            elif status == "正常待完成":
                actual_start = None
                last_update = plan_date - timedelta(days=7)
                risk_level = "中"
                risk_content = f"{project['theme']}关键件正按计划推进"
                highlight = "处于窗口期内"
            elif status == "超期未完成未变更":
                overdue_days = 10 + idx % 4
                last_update = plan_date + timedelta(days=overdue_days)
                delay_expected = last_update + timedelta(days=21)
                delay_days_unchanged = format_days(overdue_days)
                incomplete_reason = "关键件到货晚于计划"
                risk_level = "高"
                risk_content = f"{project['pbs_name']}交付滞后"
                delay_impact = "影响联调窗口排布"
            elif status == "超期未完成已变更":
                overdue_days = 12 + idx % 4
                last_update = plan_date + timedelta(days=overdue_days)
                delay_expected = last_update + timedelta(days=28)
                original_plan_date = fmt(plan_date)
                delay_days_changed = format_days(overdue_days)
                delay_applied = "是"
                incomplete_reason = "接口边界调整后重新排产"
                risk_level = "高"
                risk_content = f"{project['theme']}联调输入条件变更"
                delay_impact = "影响后续综合联试安排"
            elif status == "不正常待变更":
                last_update = plan_date + timedelta(days=7 + idx % 3)
                delay_applied = "是"
                incomplete_reason = "发现外部约束变化需发起设计更改"
                risk_level = "高"
                risk_content = f"{project['theme']}需新增变更评审"
                delay_impact = "影响综合联试基线冻结"

            row = {
                "project_no": project["project_no"],
                "subsystem": project["subsystem"],
                "node_task": f"{project['theme']}{task}",
                "plan_start_date": fmt(plan_start),
                "plan_date": fmt(plan_date),
                "plan_week": week_of(plan_date),
                "deliverable": deliverable,
                "node_type": node_type,
                "owner": project["owner"],
                "dept": project["dept"],
                "dept_leader": project["dept_leader"],
                "completion_status": status,
                "collab_dept": collab_dept(idx),
                "supervisor_dept": "科技部" if node_type == "里程碑" else "质量部",
                "delay_expected_date": fmt(delay_expected),
                "incomplete_reason": incomplete_reason,
                "risk_level": risk_level,
                "risk_content": risk_content,
                "delay_impact": delay_impact,
                "actual_start_date": fmt(actual_start),
                "actual_date": fmt(actual_date),
                "actual_week": week_of(actual_date),
                "institute_leader": project["institute_leader"],
                "source": "年度计划",
                "original_plan_date": original_plan_date,
                "delay_days_changed": delay_days_changed,
                "delay_days_unchanged": delay_days_unchanged,
                "delay_applied": delay_applied,
                "project_manager": project["project_manager"],
                "last_update_time": fmt(last_update),
                "last_update_week": week_of(last_update),
                "filled_by": project["filled_by"],
                "highlight": highlight,
            }
            rows.append(row)
    return rows


def build_progress_measure_rows(progress_rows: list[dict[str, str]]) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    project_to_nodes: dict[str, list[dict[str, str]]] = {}
    for row in progress_rows:
        project_to_nodes.setdefault(row["project_no"], []).append(row)

    for idx, project in enumerate(PROJECTS):
        nodes = project_to_nodes[project["project_no"]]
        node = nodes[1] if idx % 2 == 0 else nodes[3]
        is_closed = bool(node["actual_date"])
        rows.append(
            {
                "project_no": project["project_no"],
                "subsystem": node["subsystem"],
                "node_task": node["node_task"],
                "plan_date": node["plan_date"],
                "plan_week": node["plan_week"],
                "completion_status": node["completion_status"],
                "measure_category": "重大跟进" if "超期未完成" in node["completion_status"] else "常规跟进",
                "measure_title": f"{node['node_task']}闭环跟进",
                "follow_up_person": project["owner"],
                "main_recipient": project["dept_leader"],
                "cc_recipient": project["institute_leader"],
                "follow_up_date": node["last_update_time"] or node["plan_date"],
                "follow_up_week": node["last_update_week"] or node["plan_week"],
                "closure_status": "已闭环" if is_closed else "进行中",
                "final_closure_date": node["actual_date"] if is_closed else "",
                "final_closure_week": node["actual_week"] if is_closed else "",
                "closure_deliverable_type": "文件" if is_closed else "",
                "closure_deliverable": f"{node['deliverable']};闭环纪要" if is_closed else "",
                "risk_content": node["risk_content"],
                "last_update_time": node["last_update_time"] or node["plan_date"],
                "last_update_week": node["last_update_week"] or node["plan_week"],
                "remark": "与关键节点状态同步",
                "filled_by": project["filled_by"],
            }
        )
    return rows


def build_quality_rows() -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for idx, project in enumerate(PROJECTS):
        issue_date = date(2026, idx + 1, 6) + timedelta(days=92)
        issue_category = QUALITY_CATEGORIES[idx]
        status = QUALITY_STATUS_CYCLE[idx % len(QUALITY_STATUS_CYCLE)]
        zero_plan = "" if idx == 7 else f"完成{project['theme']}问题归零并输出验证记录"
        zero_plan_synced = "否" if not zero_plan else ("是" if idx % 3 else "否")
        zero_complete_date: date | None = None
        last_update = issue_date + timedelta(days=21)
        current_progress = "问题定位与归零措施执行中"

        if status == "已完成技术和管理归零":
            zero_complete_date = issue_date + timedelta(days=32)
            last_update = zero_complete_date
            current_progress = "归零完成，已形成技术与管理双闭环"
        elif status == "已完成技术归零":
            zero_complete_date = issue_date + timedelta(days=28)
            last_update = zero_complete_date
            current_progress = "技术归零完成，等待管理归零确认"
        elif status == "已完成管理归零":
            zero_complete_date = issue_date + timedelta(days=30)
            last_update = zero_complete_date
            current_progress = "管理归零已完成，技术验证收口中"

        rows.append(
            {
                "project_no": project["project_no"],
                "subsystem": project["subsystem"],
                "issue_name": f"{project['theme']}{project['key_item']}异常问题",
                "dept": project["dept"],
                "team_leader": project["team_leader"],
                "dept_leader": project["dept_leader"],
                "issue_date": fmt(issue_date),
                "issue_week": week_of(issue_date),
                "issue_summary": f"{project['theme']}{QUALITY_SUMMARIES[issue_category]}",
                "issue_category": issue_category,
                "zero_plan": zero_plan,
                "zero_plan_synced": zero_plan_synced,
                "new_plan_count": str((idx % 3) + 1),
                "status": status,
                "current_progress": current_progress,
                "zero_complete_date": fmt(zero_complete_date),
                "zero_complete_week": week_of(zero_complete_date),
                "last_update_time": fmt(last_update),
                "last_update_week": week_of(last_update),
                "project_manager": project["project_manager"],
                "filled_by": project["filled_by"],
            }
        )
    return rows


def build_quality_measure_rows(issue_rows: list[dict[str, str]]) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    project_lookup = {project["project_no"]: project for project in PROJECTS}
    for issue in issue_rows:
        project = project_lookup[issue["project_no"]]
        is_closed = bool(issue["zero_complete_date"])
        rows.append(
            {
                **issue,
                "project_manager": project["project_manager"],
                "measure_category": "专项跟进" if issue["status"] == "未完成归零" else "常规跟进",
                "measure_title": f"{issue['issue_name']}归零跟进",
                "follow_up_person": project["owner"],
                "main_recipient": project["dept_leader"],
                "cc_recipient": project["institute_leader"],
                "follow_up_date": issue["last_update_time"],
                "follow_up_week": issue["last_update_week"],
                "closure_status": "已闭环" if is_closed else "进行中",
                "final_closure_date": issue["zero_complete_date"] if is_closed else "",
                "final_closure_week": issue["zero_complete_week"] if is_closed else "",
                "closure_deliverable_type": "文件" if is_closed else "",
                "closure_deliverable": "归零报告;验证记录" if is_closed else "",
                "risk_content": issue["issue_summary"],
                "remark": "与质量问题台账同步",
                "filled_by": issue["filled_by"],
            }
        )
    return rows


def build_tech_rows() -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for idx, project in enumerate(PROJECTS):
        scenario = TECH_SCENARIOS[idx]
        change_submit = date(2026, idx + 1, 6) + timedelta(days=76)
        file_signature_date = change_submit + timedelta(days=14) if scenario["signed"] else None
        signature_closure_date = change_submit + timedelta(days=10) if scenario["completion_signature"] == "是" else None
        plan_file_closure_date = change_submit + timedelta(days=21)
        plan_reform_date = plan_file_closure_date + timedelta(days=21)
        reform_date = plan_reform_date if scenario["reform_done"] else None
        last_update = reform_date or file_signature_date or (change_submit + timedelta(days=18))

        rows.append(
            {
                "project_no": project["project_no"],
                "tech_state_name": project["key_item"],
                "change_item": f"{project['theme']}技术状态调整",
                "owner": project["owner"],
                "dept": project["dept"],
                "dept_leader": project["dept_leader"],
                "change_submit_time": fmt(change_submit),
                "change_submit_week": week_of(change_submit),
                "completion_signature": scenario["completion_signature"],
                "signature_closure_date": fmt(signature_closure_date),
                "signature_closure_week": week_of(signature_closure_date),
                "change_reason": f"{project['theme']}关键约束调整，需要更新技术状态与文件",
                "change_category": scenario["change_category"],
                "plan_file_closure_date": fmt(plan_file_closure_date),
                "plan_file_closure_week": week_of(plan_file_closure_date),
                "plan_reform_date": fmt(plan_reform_date),
                "plan_reform_week": week_of(plan_reform_date),
                "plan_synced": scenario["plan_synced"],
                "new_plan_count": str((idx % 2) + 1),
                "review_situation": scenario["review_situation"],
                "affected_files": f"{project['project_no']}-SPEC-001;{project['project_no']}-DRAW-002",
                "affected_objects": project["pbs_name"],
                "file_signature_status": scenario["file_signature_status"],
                "file_signature_date": fmt(file_signature_date),
                "file_signature_week": week_of(file_signature_date),
                "reform_status": scenario["reform_status"],
                "reform_date": fmt(reform_date),
                "reform_week": week_of(reform_date),
                "project_manager": project["project_manager"],
                "last_update_time": fmt(last_update),
                "last_update_week": week_of(last_update),
                "filled_by": project["filled_by"],
                "remark": "已纳入技术状态跟踪" if scenario["signed"] else "待完成文件签署",
            }
        )
    return rows


def build_tech_measure_rows(tech_rows: list[dict[str, str]]) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    project_lookup = {project["project_no"]: project for project in PROJECTS}
    for tech in tech_rows:
        project = project_lookup[tech["project_no"]]
        is_closed = tech["reform_status"] in {"已落实整改", "不涉及"} and bool(tech["file_signature_status"])
        rows.append(
            {
                "project_no": tech["project_no"],
                "tech_state_name": tech["tech_state_name"],
                "change_item": tech["change_item"],
                "owner": tech["owner"],
                "dept": tech["dept"],
                "dept_leader": tech["dept_leader"],
                "change_submit_time": tech["change_submit_time"],
                "change_submit_week": tech["change_submit_week"],
                "completion_signature": tech["completion_signature"],
                "signature_closure_date": tech["signature_closure_date"],
                "signature_closure_week": tech["signature_closure_week"],
                "change_reason": tech["change_reason"],
                "change_category": tech["change_category"],
                "plan_file_closure_date": tech["plan_file_closure_date"],
                "plan_file_closure_week": tech["plan_file_closure_week"],
                "plan_reform_date": tech["plan_reform_date"],
                "plan_reform_week": tech["plan_reform_week"],
                "plan_synced": tech["plan_synced"],
                "new_plan_count": tech["new_plan_count"],
                "affected_files": tech["affected_files"],
                "affected_objects": tech["affected_objects"],
                "file_signature_status": tech["file_signature_status"],
                "reform_status": tech["reform_status"],
                "project_manager": project["project_manager"],
                "measure_category": "专项跟进" if tech["reform_status"] == "整改中" else "常规跟进",
                "measure_title": f"{tech['change_item']}跟进",
                "follow_up_person": project["owner"],
                "main_recipient": project["dept_leader"],
                "cc_recipient": project["institute_leader"],
                "follow_up_date": tech["last_update_time"],
                "follow_up_week": tech["last_update_week"],
                "closure_status": "已闭环" if is_closed else "进行中",
                "final_closure_date": tech["reform_date"] if is_closed else "",
                "final_closure_week": tech["reform_week"] if is_closed else "",
                "closure_deliverable_type": "文件" if is_closed else "",
                "closure_deliverable": "技术状态闭环单;签署记录" if is_closed else "",
                "risk_content": tech["change_reason"],
                "last_update_time": tech["last_update_time"],
                "last_update_week": tech["last_update_week"],
                "remark": tech["remark"],
                "filled_by": tech["filled_by"],
            }
        )
    return rows


def build_risk_rows() -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for idx, project in enumerate(PROJECTS):
        risk_submit = date(2026, idx + 1, 6) + timedelta(days=98)
        risk_category = RISK_CATEGORY_CYCLE[idx]
        risk_level = RISK_LEVEL_CYCLE[idx]
        is_released = idx in {0, 2, 4, 6, 8}
        risk_release_date = risk_submit + timedelta(days=42) if is_released else None
        progress_stat_time = risk_release_date or (risk_submit + timedelta(days=21))
        final_release_time = risk_release_date or (risk_submit + timedelta(days=56))
        rows.append(
            {
                "project_no": project["project_no"],
                "risk_name": f"{project['theme']}{risk_category}风险",
                "subsystem": project["subsystem"],
                "belonging_unit": project["key_item"],
                "risk_description": f"{project['theme']}{RISK_DESCRIPTIONS[risk_category]}",
                "risk_submit_time": fmt(risk_submit),
                "risk_submit_week": week_of(risk_submit),
                "risk_phase": "初样研制" if idx < 5 else "联调准备",
                "risk_category": risk_category,
                "risk_level": risk_level,
                "impact_scope": project["pbs_name"],
                "response_measure": RISK_MEASURES[risk_category],
                "final_release_time": fmt(final_release_time),
                "final_release_week": week_of(final_release_time),
                "monthly_control_plan": f"{project['theme']}建立月度风险台账",
                "weekly_release_plan": f"{project['theme']}每周跟踪关键释放动作",
                "release_plan_synced": "是" if idx % 2 == 0 else "否",
                "new_plan_count": str((idx % 3) + 1),
                "progress_stat_time": fmt(progress_stat_time),
                "progress_stat_week": week_of(progress_stat_time),
                "progress_situation": "措施已闭环，风险释放" if is_released else "措施执行中，尚未达到释放条件",
                "response_owner": project["owner"],
                "control_owner": project["dept_leader"],
                "dept": project["dept"],
                "risk_status": "已释放" if is_released else "未释放",
                "risk_release_date": fmt(risk_release_date),
                "risk_release_week": week_of(risk_release_date),
                "remark": "与关键物料同步跟踪",
                "last_update_time": fmt(progress_stat_time),
                "last_update_week": week_of(progress_stat_time),
                "filled_by": project["filled_by"],
            }
        )
    return rows


def build_risk_measure_rows(risk_rows: list[dict[str, str]]) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    project_lookup = {project["project_no"]: project for project in PROJECTS}
    for risk in risk_rows:
        project = project_lookup[risk["project_no"]]
        is_closed = risk["risk_status"] == "已释放"
        rows.append(
            {
                "project_no": risk["project_no"],
                "risk_name": risk["risk_name"],
                "subsystem": risk["subsystem"],
                "belonging_unit": risk["belonging_unit"],
                "risk_description": risk["risk_description"],
                "risk_submit_time": risk["risk_submit_time"],
                "risk_submit_week": risk["risk_submit_week"],
                "risk_phase": risk["risk_phase"],
                "risk_category": risk["risk_category"],
                "risk_level": risk["risk_level"],
                "impact_scope": risk["impact_scope"],
                "response_measure": risk["response_measure"],
                "final_release_time": risk["final_release_time"],
                "monthly_control_plan": risk["monthly_control_plan"],
                "weekly_release_plan": risk["weekly_release_plan"],
                "release_plan_synced": risk["release_plan_synced"],
                "new_plan_count": risk["new_plan_count"],
                "progress_stat_time": risk["progress_stat_time"],
                "progress_stat_week": risk["progress_stat_week"],
                "progress_situation": risk["progress_situation"],
                "response_owner": risk["response_owner"],
                "control_owner": risk["control_owner"],
                "dept": risk["dept"],
                "risk_status": risk["risk_status"],
                "project_manager": project["project_manager"],
                "measure_category": "重大跟进" if risk["risk_level"] == "高" and not is_closed else "常规跟进",
                "measure_title": f"{risk['risk_name']}释放跟进",
                "follow_up_person": project["owner"],
                "main_recipient": project["dept_leader"],
                "cc_recipient": project["institute_leader"],
                "follow_up_date": risk["last_update_time"],
                "follow_up_week": risk["last_update_week"],
                "closure_status": "已闭环" if is_closed else "进行中",
                "final_closure_date": risk["risk_release_date"] if is_closed else "",
                "final_closure_week": risk["risk_release_week"] if is_closed else "",
                "closure_deliverable_type": "文件" if is_closed else "",
                "closure_deliverable": "风险释放报告;验证记录" if is_closed else "",
                "risk_content": risk["risk_description"],
                "last_update_time": risk["last_update_time"],
                "last_update_week": risk["last_update_week"],
                "remark": risk["remark"],
                "filled_by": risk["filled_by"],
            }
        )
    return rows


def build_material_rows() -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for idx, project in enumerate(PROJECTS):
        base = date(2026, idx + 1, 6)
        negotiation = base + timedelta(days=28)
        contract_delivery = negotiation + timedelta(days=63)
        delivered = idx in {0, 1, 3, 4, 6, 8}
        actual_delivery = contract_delivery + timedelta(days=(idx % 3) - 1) if delivered else None
        plan_inspect = contract_delivery + timedelta(days=7)
        complete_inspect = plan_inspect + timedelta(days=4) if delivered else None
        install_date = complete_inspect + timedelta(days=7) if complete_inspect else None
        risk_level = "高" if not delivered and idx % 2 else ("中" if not delivered else "低")
        risk_content = f"{project['pbs_name']}到货节奏存在波动" if not delivered else ""
        delay_impact = "影响关键节点交付" if not delivered else ""
        last_update = install_date or actual_delivery or (contract_delivery + timedelta(days=14))
        rows.append(
            {
                "project_no": project["project_no"],
                "subsystem": project["subsystem"],
                "pbs_no": project["pbs_no"],
                "pbs_name": project["pbs_name"],
                "self_or_outsource": project["self_or_outsource"],
                "supplier_name": project["supplier_name"],
                "is_long_cycle": "是" if project["self_or_outsource"] == "外协" else "否",
                "contract_negotiation_date": fmt(negotiation),
                "contract_negotiation_week": week_of(negotiation),
                "contract_delivery_date": fmt(contract_delivery),
                "contract_delivery_week": week_of(contract_delivery),
                "actual_delivery_date": fmt(actual_delivery),
                "actual_delivery_week": week_of(actual_delivery),
                "plan_inspect_date": fmt(plan_inspect),
                "plan_inspect_week": week_of(plan_inspect),
                "complete_inspect_date": fmt(complete_inspect),
                "complete_inspect_week": week_of(complete_inspect),
                "install_date": fmt(install_date),
                "install_week": week_of(install_date),
                "dept_owner": f"{project['owner']}/{project['dept']}",
                "control_dept_owner": f"{project['dept_leader']}/{project['dept']}",
                "weekly_progress": "已完成到货检验并进入装配" if delivered else "供应商正在加急排产，尚未到货",
                "affects_major_node": "是" if not delivered else "否",
                "risk_level": risk_level,
                "risk_content": risk_content,
                "delay_impact": delay_impact,
                "last_update_time": fmt(last_update),
                "last_update_week": week_of(last_update),
                "remark": "与风险台账联动" if not delivered else "",
            }
        )
    return rows


def write_table(base_name: str, rows: list[dict[str, str]]) -> None:
    fieldnames = CSV_SCHEMAS[base_name]
    csv_path = TEST_DIR / f"{base_name}.csv"
    with csv_path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)

    workbook = Workbook()
    sheet = workbook.active
    sheet.title = base_name[:31]
    sheet.append(fieldnames)
    for row in rows:
        sheet.append([row.get(field, "") for field in fieldnames])
    workbook.save(TEST_DIR / f"{base_name}.xlsx")


def main() -> None:
    progress_rows = build_progress_rows()
    quality_rows = build_quality_rows()
    tech_rows = build_tech_rows()
    risk_rows = build_risk_rows()

    tables = {
        "ods_project_subject_domain_v2": progress_rows,
        "ods_progress_measure_v2": build_progress_measure_rows(progress_rows),
        "ods_quality_issue_v2": quality_rows,
        "ods_quality_measure_v2": build_quality_measure_rows(quality_rows),
        "ods_tech_state_v2": tech_rows,
        "ods_tech_state_measure_v2": build_tech_measure_rows(tech_rows),
        "ods_risk_info_v2": risk_rows,
        "ods_risk_measure_v2": build_risk_measure_rows(risk_rows),
        "ods_material_info_v2": build_material_rows(),
    }

    for base_name, rows in tables.items():
        write_table(base_name, rows)

    print("generated tables:")
    for base_name, rows in tables.items():
        print(f"{base_name}: {len(rows)} rows")


if __name__ == "__main__":
    main()
