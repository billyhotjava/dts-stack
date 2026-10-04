#!/usr/bin/env python3
from __future__ import annotations

from collections import Counter
from dataclasses import dataclass
from datetime import date, datetime, timedelta
from pathlib import Path
import random

from openpyxl import Workbook


SEED = 20260316
TOTAL_ROWS = 2000
MAJOR_PROJECT_COUNT = 5
SUBPROJECT_COUNT = 30
STATUS_DISTRIBUTION = {
    "delayed": 1000,
    "normal": 600,
    "early": 400,
}

ROOT = Path(__file__).resolve().parent
OUTPUT_PATH = ROOT / "project-cockpit-test-batch-2000.xlsx"


@dataclass(frozen=True)
class MajorProject:
    major_project_id: str
    major_project_name: str
    owner_dept: str
    owner_leader: str
    program_id: str
    program_name: str


@dataclass(frozen=True)
class Subproject:
    subproject_id: str
    subproject_name: str
    project_no: str
    subsystem: str
    owner_dept: str
    owner_user: str
    project_manager: str
    major: MajorProject


MAJOR_PROJECTS = [
    MajorProject("major-aurora", "苍穹导航综合工程", "导航室", "李总", "program-core", "核心装备群"),
    MajorProject("major-beacon", "北斗信号增强工程", "通信室", "吴总", "program-core", "核心装备群"),
    MajorProject("major-cosmos", "星链信号攻关工程", "雷达室", "林总", "program-advanced", "先进预研群"),
    MajorProject("major-dragon", "龙眼光电探测工程", "光学室", "梁总", "program-opto", "光电探测群"),
    MajorProject("major-everest", "极峰协同控制工程", "控制室", "周总", "program-control", "综合控制群"),
]

DEPT_LEADERS = {
    "导航室": "张主任",
    "通信室": "吴主任",
    "雷达室": "林主任",
    "光学室": "梁主任",
    "控制室": "周主任",
    "试验室": "韩主任",
    "软件室": "孙主任",
    "工艺室": "钱主任",
}

SUBSYSTEM_POOL = [
    "导航处理机",
    "信号处理分系统",
    "天线分系统",
    "光电探测单元",
    "控制单元",
    "软件平台",
]

NODE_TYPES = ["一般节点", "关键节点", "里程碑"]
RISK_LEVELS = ["高", "中", "低"]
COLLAB_DEPTS = ["试验室", "软件室", "工艺室", "通信室", "雷达室", ""]
SUPERVISOR_DEPTS = ["科研管理部", "质量部", "项目办", ""]
DELAY_REASON_TEXT = [
    ("technical", "技术攻关算法精度未达标，仍在优化"),
    ("quality", "质量整改返工导致排期顺延"),
    ("change", "需求变更后需重新定义接口"),
    ("coordination", "接口联调协同进度偏慢"),
    ("supplier", "关键器件到货延迟影响装调"),
    ("test", "试验排期冲突导致节点后移"),
    ("archive", "资料归档和报告审批滞后"),
]

HEADERS = [
    "project_no",
    "major_project_id",
    "major_project_name",
    "program_id",
    "program_name",
    "subproject_id",
    "subproject_name",
    "subsystem",
    "node_task",
    "plan_date",
    "plan_week",
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
    "filled_by",
    "highlight",
]


def build_subprojects() -> list[Subproject]:
    subprojects: list[Subproject] = []
    owners = [
        ("张三", "赵主管"),
        ("李四", "孙主管"),
        ("王五", "周主管"),
        ("赵六", "钱主管"),
        ("钱七", "吴主管"),
        ("孙八", "郑主管"),
    ]
    idx = 0
    for major in MAJOR_PROJECTS:
        for local in range(6):
            subsystem = SUBSYSTEM_POOL[local % len(SUBSYSTEM_POOL)]
            owner_user, project_manager = owners[local % len(owners)]
            subprojects.append(
                Subproject(
                    subproject_id=f"sub-{major.major_project_id.split('-', 1)[1]}-{local + 1:02d}",
                    subproject_name=f"{subsystem}子项目{local + 1:02d}",
                    project_no=f"PRJ-{major.major_project_id.split('-', 1)[1][0].upper()}{local + 1:03d}",
                    subsystem=subsystem,
                    owner_dept=major.owner_dept,
                    owner_user=owner_user,
                    project_manager=project_manager,
                    major=major,
                )
            )
            idx += 1
    assert len(subprojects) == SUBPROJECT_COUNT
    return subprojects


def format_week(dt: date) -> str:
    return str(dt.isocalendar().week)


def format_date_variant(dt: date, variant: int) -> str:
    if variant == 0:
        return dt.isoformat()
    if variant == 1:
        return dt.strftime("%Y/%m/%d")
    return dt.strftime("%Y.%m.%d")


def completion_status_for(status: str) -> str:
    if status == "delayed":
        return "超期已完成未变更"
    if status == "normal":
        return "正常已完成"
    return "提前完成"


def risk_level_for(status: str, dirty_enum: bool) -> str:
    if status == "delayed":
        value = random.choices(RISK_LEVELS, weights=[0.55, 0.35, 0.10], k=1)[0]
    elif status == "normal":
        value = random.choices(RISK_LEVELS, weights=[0.10, 0.35, 0.55], k=1)[0]
    else:
        value = random.choices(RISK_LEVELS, weights=[0.05, 0.20, 0.75], k=1)[0]
    if not dirty_enum:
        return value
    mapping = {"高": "高风险", "中": "中等", "低": "低 "}
    return mapping.get(value, value)


def plan_date_for(index: int) -> date:
    base = date(2026, 1, 6)
    return base + timedelta(days=index % 160)


def actual_date_for(plan: date, status: str) -> date:
    if status == "delayed":
        return plan + timedelta(days=random.randint(1, 28))
    if status == "normal":
        return plan
    return plan - timedelta(days=random.randint(1, 10))


def maybe_dirty_text(text: str, allow: bool) -> str:
    if not allow:
        return text
    options = [
        f" {text}",
        f"{text} ",
        text.replace("子项目", " 子项目"),
    ]
    return random.choice(options)


def build_row(subproject: Subproject, row_index: int, status: str) -> list[str]:
    plan = plan_date_for(row_index)
    actual = actual_date_for(plan, status)
    dirty_date = row_index % 23 == 0
    missing_actual = row_index % 41 == 0
    dirty_risk = row_index % 17 == 0
    dirty_subproject = row_index % 19 == 0
    empty_dept = row_index % 37 == 0
    free_text_reason = row_index % 29 == 0

    completion_status = completion_status_for(status)
    delay_days = (actual - plan).days
    delay_days_changed = max(delay_days, 0)
    delay_days_unchanged = 0 if delay_days > 0 else abs(delay_days)
    delay_applied = "是" if status == "delayed" and row_index % 5 != 0 else "否"
    reason_code, reason_text = random.choice(DELAY_REASON_TEXT)
    if status != "delayed":
        reason_text = ""
    elif free_text_reason:
        reason_text = f"{reason_text}，并伴随跨部门协同反复确认。"

    collab_dept = random.choice(COLLAB_DEPTS)
    supervisor = random.choice(SUPERVISOR_DEPTS)
    dept_value = "" if empty_dept else subproject.owner_dept

    node_type = random.choices(NODE_TYPES, weights=[0.55, 0.30, 0.15], k=1)[0]
    if row_index % 11 == 0:
        node_type = "里程碑"

    node_task = f"{subproject.subsystem}-节点-{row_index + 1:04d}"
    if node_type == "里程碑":
        node_task = f"{subproject.subsystem}-里程碑-{row_index + 1:04d}"

    highlight = "是" if status == "delayed" and random.random() < 0.32 else ""
    risk_content = {
        "delayed": "存在延期与资源协调风险",
        "normal": "按计划推进，关注接口配合",
        "early": "提前完成，可转入下阶段准备",
    }[status]
    delay_impact = {
        "delayed": "影响月度计划兑现和主项目节点节拍",
        "normal": "整体节奏稳定，影响可控",
        "early": "释放后续联调窗口",
    }[status]

    plan_text = format_date_variant(plan, row_index % 3 if dirty_date else 0)
    actual_text = "" if missing_actual else format_date_variant(actual, (row_index + 1) % 3 if dirty_date else 0)

    return [
        subproject.project_no,
        subproject.major.major_project_id,
        subproject.major.major_project_name,
        subproject.major.program_id,
        subproject.major.program_name,
        subproject.subproject_id,
        maybe_dirty_text(subproject.subproject_name, dirty_subproject),
        subproject.subsystem,
        node_task,
        plan_text,
        format_week(plan),
        node_type,
        subproject.owner_user,
        dept_value,
        DEPT_LEADERS.get(subproject.owner_dept, ""),
        completion_status,
        collab_dept,
        supervisor,
        format_date_variant(plan + timedelta(days=max(delay_days, 0)), 0) if status == "delayed" else "",
        reason_text,
        risk_level_for(status, dirty_risk),
        risk_content,
        delay_impact,
        actual_text,
        "" if missing_actual else format_week(actual),
        subproject.major.owner_leader,
        "项目主体域测试数据",
        plan.isoformat(),
        str(delay_days_changed),
        str(delay_days_unchanged),
        delay_applied,
        subproject.project_manager,
        datetime(2026, 3, 16, 9, 0, 0).isoformat(sep=" "),
        "信息科测试账号",
        highlight,
    ]


def write_workbook(rows: list[list[str]]) -> None:
    workbook = Workbook()
    sheet = workbook.active
    sheet.title = "project_progress_raw"
    sheet.append(HEADERS)
    for row in rows:
        sheet.append(row)

    summary = workbook.create_sheet("summary")
    summary.append(["metric", "value"])
    summary.append(["seed", SEED])
    summary.append(["total_rows", TOTAL_ROWS])
    for key, value in STATUS_DISTRIBUTION.items():
        summary.append([f"status_{key}", value])
    workbook.save(OUTPUT_PATH)


def main() -> None:
    random.seed(SEED)
    subprojects = build_subprojects()
    statuses: list[str] = []
    for key, value in STATUS_DISTRIBUTION.items():
        statuses.extend([key] * value)
    random.shuffle(statuses)

    rows: list[list[str]] = []
    status_counter: Counter[str] = Counter()
    subproject_counter: Counter[str] = Counter()
    for index, status in enumerate(statuses):
        subproject = subprojects[index % len(subprojects)]
        rows.append(build_row(subproject, index, status))
        status_counter[status] += 1
        subproject_counter[subproject.subproject_id] += 1

    write_workbook(rows)

    print(f"generated: {OUTPUT_PATH}")
    print(f"rows: {len(rows)}")
    print(f"major_projects: {MAJOR_PROJECT_COUNT}")
    print(f"subprojects: {len(subprojects)}")
    print(f"status_distribution: {dict(status_counter)}")
    print(
        "subproject_row_range:",
        min(subproject_counter.values()),
        max(subproject_counter.values()),
    )


if __name__ == "__main__":
    main()
