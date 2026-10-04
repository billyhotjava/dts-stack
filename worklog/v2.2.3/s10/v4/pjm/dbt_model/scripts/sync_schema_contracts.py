#!/usr/bin/env python3
"""Synchronize PJM dbt schema contracts from the verified PostgreSQL relations.

The script deliberately reads only information_schema metadata. It preserves
existing dbt tests, adds every output column and emits the DTS model semantics
used by the reverse-modeling artifact importer.
"""

from __future__ import annotations

import argparse
import re
import subprocess
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]
MODEL_FILES = (ROOT / "models" / "pm_schema_v2.yml", ROOT / "models" / "pm_stg_v2.yml")
SOURCE_FILE = ROOT / "models" / "pm_sources_v2.yml"

DIMENSIONS = {
    "dim_completion_status_v2": ("Domain_Prj", "dim_ec90d8b6dc4b48248b29f58fda1b5f9a", "completion_status_id", "每行一个项目节点完成状态"),
    "dim_node_type_v2": ("Domain_Prj", "dim_56f78969cb874428b6beaddedbcbf790", "node_type_id", "每行一个项目节点类型"),
    "dim_risk_level_v2": ("Domain_Prj", "dim_a1ba31f74edb488eb0ecb18af3a64e44", "risk_level_id", "每行一个项目风险等级"),
    "dim_quality_status_v2": ("QUALITY", "dim_f691053e433443f49263f067372c5943", "quality_status_id", "每行一个质量归零状态"),
    "dim_change_category_v2": ("PRODUCT_TECH", "dim_51329b87339f45939d8b1001abf17e20", "change_category_id", "每行一个技术更改类别"),
    "dim_signature_status_v2": ("PRODUCT_TECH", "dim_134a1b22174e4a7dbdc9ecbec3dd51bb", "signature_status_id", "每行一个文件签署状态"),
    "dim_quality_category_v2": ("QUALITY", "dim_94d87d2a1f6e4b5486a679df4bdc68fc", "quality_category_id", "每行一个质量问题原因分类"),
    "dim_risk_category_v2": ("Domain_Prj", "dim_31cc1f8208fd41e2abd9dbb1fb002606", "risk_category_id", "每行一个项目风险分类"),
}

FACTS = {
    "biz_dwd_project_node_v2": {
        "domain": "Domain_Prj",
        "source": "source.pm_analytics_v3.pm_ods_v2.project_subject_domain_v2",
        "keys": ["node_id"],
        "grain": "每行代表一个项目分任务节点及其计划日期",
        "shape": "ACCUMULATING_SNAPSHOT",
        "time_type": "MILESTONE_DATES",
        "time_fields": ["plan_start_date", "plan_date", "actual_start_date", "actual_date", "last_update_time"],
        "measures": ["delay_days"],
    },
    "biz_dwd_quality_issue_v2": {
        "domain": "QUALITY",
        "source": "source.pm_analytics_v3.pm_ods_v2.quality_issue_v2",
        "keys": ["issue_id"],
        "grain": "每行代表一个项目下的一条质量问题",
        "shape": "ACCUMULATING_SNAPSHOT",
        "time_type": "MILESTONE_DATES",
        "time_fields": ["issue_date", "zero_complete_date", "last_update_time"],
        "measures": ["new_plan_count", "pending_days", "aging_days"],
    },
    "biz_dwd_tech_state_v2": {
        "domain": "PRODUCT_TECH",
        "source": "source.pm_analytics_v3.pm_ods_v2.tech_state_v2",
        "keys": ["tech_state_id"],
        "grain": "每行代表一个项目下的一项技术状态更改",
        "shape": "ACCUMULATING_SNAPSHOT",
        "time_type": "MILESTONE_DATES",
        "time_fields": ["change_submit_time", "signature_closure_date", "file_signature_date", "reform_date", "last_update_time"],
        "measures": ["new_plan_count"],
    },
    "biz_dwd_risk_info_v2": {
        "domain": "Domain_Prj",
        "source": "source.pm_analytics_v3.pm_ods_v2.risk_info_v2",
        "keys": ["risk_id"],
        "grain": "每行代表一个项目下的一条风险",
        "shape": "ACCUMULATING_SNAPSHOT",
        "time_type": "MILESTONE_DATES",
        "time_fields": ["risk_submit_date", "final_release_date", "progress_stat_date", "risk_release_date", "last_update_time"],
        "measures": ["new_plan_count", "pending_days"],
    },
    "biz_dwd_budget_v2": {
        "domain": "FINANCE",
        "source": "source.pm_analytics_v3.pm_ods_v2.budget_v2",
        "keys": ["budget_id"],
        "grain": "每行代表一个预算编号在一个业务快照日的执行状态",
        "shape": "PERIODIC_SNAPSHOT",
        "time_type": "SNAPSHOT_DATE",
        "time_fields": ["snapshot_date"],
        "measures": [
            "budget_amount", "prepaid_amount", "book_cost_amount", "payable_amount",
            "executed_amount", "remaining_amount", "overrun_amount", "execution_rate_line",
        ],
    },
    "biz_dwd_project_follow_up_v2": {
        "domain": "Domain_Prj",
        "source": "source.pm_analytics_v3.pm_ods_v2.progress_measure_v2",
        "keys": ["measure_id"],
        "grain": "每行代表一个项目节点的一条跟进措施",
        "shape": "ACCUMULATING_SNAPSHOT",
        "time_type": "MILESTONE_DATES",
        "time_fields": ["plan_date", "follow_up_date", "final_closure_date", "last_update_time"],
        "measures": ["closure_days", "pending_days"],
    },
    "biz_dwd_quality_measure_v2": {
        "domain": "QUALITY",
        "source": "source.pm_analytics_v3.pm_ods_v2.quality_measure_v2",
        "keys": ["measure_id"],
        "grain": "每行代表一条质量问题跟进措施",
        "shape": "ACCUMULATING_SNAPSHOT",
        "time_type": "MILESTONE_DATES",
        "time_fields": ["issue_date", "follow_up_date", "final_closure_date", "last_update_time"],
        "measures": ["new_plan_count", "closure_days", "pending_days"],
    },
    "biz_dwd_tech_state_measure_v2": {
        "domain": "PRODUCT_TECH",
        "source": "source.pm_analytics_v3.pm_ods_v2.tech_state_measure_v2",
        "keys": ["measure_id"],
        "grain": "每行代表一条技术状态跟进措施",
        "shape": "ACCUMULATING_SNAPSHOT",
        "time_type": "MILESTONE_DATES",
        "time_fields": ["change_submit_date", "follow_up_date", "final_closure_date", "last_update_time"],
        "measures": ["new_plan_count", "closure_days", "pending_days"],
    },
    "biz_dwd_risk_measure_v2": {
        "domain": "Domain_Prj",
        "source": "source.pm_analytics_v3.pm_ods_v2.risk_measure_v2",
        "keys": ["measure_id"],
        "grain": "每行代表一条项目风险跟进措施",
        "shape": "ACCUMULATING_SNAPSHOT",
        "time_type": "MILESTONE_DATES",
        "time_fields": ["risk_submit_date", "follow_up_date", "final_closure_date", "last_update_time"],
        "measures": ["new_plan_count", "closure_days", "pending_days"],
    },
    "biz_dwd_material_delivery_v2": {
        "domain": "MATERIAL_SUPPLY",
        "source": "source.pm_analytics_v3.pm_ods_v2.material_info_v2",
        "keys": ["material_delivery_id"],
        "grain": "每行代表一个项目下的一项 PBS 物料交付状态",
        "shape": "ACCUMULATING_SNAPSHOT",
        "time_type": "MILESTONE_DATES",
        "time_fields": ["contract_delivery_date", "actual_delivery_date", "plan_inspect_date", "complete_inspect_date", "install_date", "last_update_time"],
        "measures": ["delivery_delay_days"],
    },
}

SUMMARIES = {
    "biz_dws_progress_monthly_v2": ("Domain_Prj", ["project_no", "plan_month"], "每行代表一个项目在一个计划月份的进度汇总"),
    "biz_dws_quality_monthly_v2": ("QUALITY", ["project_no", "dept", "period_month"], "每行代表一个项目责任部门在一个问题发生月份的质量汇总"),
    "biz_dws_tech_state_monthly_v2": ("PRODUCT_TECH", ["project_no", "dept", "period_month"], "每行代表一个项目责任部门在一个更改提出月份的技术状态汇总"),
    "biz_dws_risk_monthly_v2": ("Domain_Prj", ["project_no", "dept", "period_month"], "每行代表一个项目责任部门在一个风险提出月份的风险汇总"),
    "biz_dws_budget_v2": ("FINANCE", ["snapshot_date", "project_no", "research_lab"], "每行代表一个业务快照日下项目与研究室的预算汇总"),
    "biz_dws_project_follow_up_monthly_v2": ("Domain_Prj", ["period_month", "project_no"], "每行代表一个项目在一个跟进月份的措施汇总"),
    "biz_dws_quality_measure_monthly_v2": ("QUALITY", ["period_month", "project_no", "dept"], "每行代表项目责任部门在一个跟进月份的质量措施汇总"),
    "biz_dws_tech_state_measure_monthly_v2": ("PRODUCT_TECH", ["period_month", "project_no", "dept"], "每行代表项目责任部门在一个跟进月份的技术状态措施汇总"),
    "biz_dws_risk_measure_monthly_v2": ("Domain_Prj", ["period_month", "project_no", "dept"], "每行代表项目责任部门在一个跟进月份的风险措施汇总"),
    "biz_dws_material_delivery_monthly_v2": ("MATERIAL_SUPPLY", ["period_month", "project_no", "dept_owner"], "每行代表项目责任部门在一个合同交付月份的物料汇总"),
}

APPLICATIONS = {
    "biz_ads_progress_kpi_v2": ("Domain_Prj", ["plan_year", "plan_month"], "项目进度基础指标消费"),
    "biz_ads_progress_derived_v2": ("Domain_Prj", ["plan_year", "plan_month"], "项目进度派生指标与预警消费"),
    "biz_ads_quality_kpi_v2": ("QUALITY", ["period_year", "period_month"], "质量基础指标消费"),
    "biz_ads_quality_derived_v2": ("QUALITY", ["period_year", "period_month"], "质量派生指标与预警消费"),
    "biz_ads_tech_state_kpi_v2": ("PRODUCT_TECH", ["period_year", "period_month"], "技术状态基础指标消费"),
    "biz_ads_tech_state_derived_v2": ("PRODUCT_TECH", ["period_year", "period_month"], "技术状态派生指标与预警消费"),
    "biz_ads_risk_kpi_v2": ("Domain_Prj", ["period_year", "period_month"], "项目风险指标消费"),
    "biz_ads_budget_kpi_v2": ("FINANCE", ["snapshot_scope", "snapshot_date"], "预算执行基础指标消费"),
    "biz_ads_budget_derived_v2": ("FINANCE", ["snapshot_scope", "snapshot_date"], "预算执行派生指标与预警消费"),
    "biz_ads_composite_derived_v2": ("Domain_Prj", ["period_year", "period_month"], "项目综合健康与风险预警消费"),
    "biz_ads_project_follow_up_kpi_v2": ("Domain_Prj", ["period_year", "period_month"], "项目跟进措施闭环指标消费"),
    "biz_ads_quality_measure_kpi_v2": ("QUALITY", ["period_year", "period_month"], "质量措施闭环指标消费"),
    "biz_ads_tech_state_measure_kpi_v2": ("PRODUCT_TECH", ["period_year", "period_month"], "技术状态措施闭环指标消费"),
    "biz_ads_risk_measure_kpi_v2": ("Domain_Prj", ["period_year", "period_month"], "风险措施闭环指标消费"),
    "biz_ads_material_delivery_kpi_v2": ("MATERIAL_SUPPLY", ["period_year", "period_month", "dept_owner"], "物料采购与交付指标消费"),
}

MODEL_DESCRIPTIONS = {
    "stg_pm__progress_measure_v2": "项目跟进措施标准化视图",
    "stg_pm__quality_measure_v2": "质量措施标准化视图",
    "stg_pm__tech_state_measure_v2": "技术状态措施标准化视图",
    "stg_pm__risk_measure_v2": "风险措施标准化视图",
    "stg_pm__material_info_v2": "重要物料信息标准化视图",
    "biz_dwd_project_follow_up_v2": "项目跟进措施明细事实表",
    "biz_dwd_quality_measure_v2": "质量措施明细事实表",
    "biz_dwd_tech_state_measure_v2": "技术状态措施明细事实表",
    "biz_dwd_risk_measure_v2": "风险措施明细事实表",
    "biz_dwd_material_delivery_v2": "重要物料交付明细事实表",
    "biz_dws_project_follow_up_monthly_v2": "项目跟进措施月度汇总表",
    "biz_dws_quality_measure_monthly_v2": "质量措施月度汇总表",
    "biz_dws_tech_state_measure_monthly_v2": "技术状态措施月度汇总表",
    "biz_dws_risk_measure_monthly_v2": "风险措施月度汇总表",
    "biz_dws_material_delivery_monthly_v2": "重要物料交付月度汇总表",
    "biz_ads_project_follow_up_kpi_v2": "项目跟进措施闭环指标应用表",
    "biz_ads_quality_measure_kpi_v2": "质量措施闭环指标应用表",
    "biz_ads_tech_state_measure_kpi_v2": "技术状态措施闭环指标应用表",
    "biz_ads_risk_measure_kpi_v2": "风险措施闭环指标应用表",
    "biz_ads_material_delivery_kpi_v2": "物料采购与交付指标应用表",
}

NEW_STG_MODELS = {
    "stg_pm__progress_measure_v2",
    "stg_pm__quality_measure_v2",
    "stg_pm__tech_state_measure_v2",
    "stg_pm__risk_measure_v2",
    "stg_pm__material_info_v2",
}

MODEL_GRAIN_TESTS = {
    "biz_dws_project_follow_up_monthly_v2": ["period_month", "project_no"],
    "biz_dws_quality_measure_monthly_v2": ["period_month", "project_no", "dept"],
    "biz_dws_tech_state_measure_monthly_v2": ["period_month", "project_no", "dept"],
    "biz_dws_risk_measure_monthly_v2": ["period_month", "project_no", "dept"],
    "biz_dws_material_delivery_monthly_v2": ["period_month", "project_no", "dept_owner"],
    "biz_ads_project_follow_up_kpi_v2": ["period_year", "period_month"],
    "biz_ads_quality_measure_kpi_v2": ["period_year", "period_month"],
    "biz_ads_tech_state_measure_kpi_v2": ["period_year", "period_month"],
    "biz_ads_risk_measure_kpi_v2": ["period_year", "period_month"],
    "biz_ads_material_delivery_kpi_v2": ["period_year", "period_month", "dept_owner"],
}

ALIASES = {
    "dim_boolean_alias", "dim_change_category_alias", "dim_node_type_alias",
    "dim_quality_category_alias", "dim_quality_status_alias", "dim_reform_status_alias",
    "dim_risk_category_alias", "dim_risk_level_alias", "dim_risk_status_alias",
    "dim_signature_status_alias",
}

FIELD_LABELS = {
    "budget_id": "预算快照标识", "budget_no": "预算编号", "node_id": "节点标识",
    "issue_id": "质量问题标识", "risk_id": "风险标识", "tech_state_id": "技术状态更改标识",
    "project_no": "项目编号", "snapshot_scope": "快照范围", "snapshot_date": "业务快照日期",
    "plan_month": "计划月份", "period_month": "统计月份", "period_year": "统计年份",
    "plan_year": "计划年份", "plan_quarter": "计划季度", "research_lab": "研究室",
    "source_row_id": "源记录行标识", "source_table": "源表", "source_system": "源系统",
    "source_imported_at": "源导入时间", "imported_at": "导入时间", "etl_time": "加工时间",
    "code": "标准编码", "label": "标准名称", "alias_raw": "源端原始值",
    "canonical_code": "标准值", "sort_order": "排序号", "severity_rank": "严重程度序号",
}

TOKEN_LABELS = {
    "id": "标识", "no": "编号", "source": "源", "row": "行", "table": "表", "system": "系统",
    "imported": "导入", "at": "时间", "project": "项目", "node": "节点", "task": "任务",
    "plan": "计划", "actual": "实际", "date": "日期", "time": "时间", "year": "年份",
    "quarter": "季度", "month": "月份", "week": "周", "period": "统计", "snapshot": "快照",
    "scope": "范围", "budget": "预算", "amount": "金额", "prepaid": "预付", "book": "账面",
    "cost": "成本", "payable": "应付", "executed": "已执行", "remaining": "剩余", "overrun": "超支",
    "rate": "比率", "ratio": "占比", "health": "健康度", "score": "评分", "warning": "预警",
    "warn": "预警", "total": "总", "cnt": "数量", "count": "数量", "item": "条目",
    "quality": "质量", "issue": "问题", "risk": "风险", "tech": "技术", "state": "状态",
    "status": "状态", "category": "分类", "level": "等级", "completion": "完成", "signature": "签署",
    "change": "更改", "reform": "整改", "raw": "原始值", "label": "名称", "code": "编码",
    "is": "是否", "has": "是否有", "open": "未关闭", "released": "已释放", "release": "释放",
    "high": "高", "mid": "中", "low": "低", "new": "新增", "done": "已完成",
    "pending": "待完成", "incomplete": "未完成", "overdue": "超期", "abnormal": "异常",
    "milestone": "里程碑", "general": "一般", "important": "重要", "major": "重大",
    "owner": "负责人", "dept": "部门", "leader": "负责人", "name": "名称", "description": "说明",
    "reason": "原因", "content": "内容", "remark": "备注", "days": "天数", "day": "日",
    "type": "类型", "context": "上下文", "attribute": "属性", "value": "值", "text": "文本",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--container", default="v223-dts-pg-1")
    parser.add_argument("--database", default="biadmin")
    parser.add_argument("--user", default="postgres")
    parser.add_argument("--model-schema", default="public")
    parser.add_argument("--source-schema", default="public")
    return parser.parse_args()


def model_names() -> list[str]:
    names = sorted(path.stem for path in (ROOT / "models").rglob("*.sql"))
    if len(names) != 63 or len(set(names)) != len(names):
        raise SystemExit(f"expected 63 unique SQL models, found {len(names)}")
    return names


def relation_columns(args: argparse.Namespace, names: list[str], schema: str) -> dict[str, list[tuple[str, str]]]:
    if not re.fullmatch(r"[a-z][a-z0-9_]{0,62}", schema):
        raise SystemExit(f"invalid PostgreSQL schema: {schema}")
    quoted = ",".join("'" + name.replace("'", "''") + "'" for name in names)
    sql = (
        "select table_name,column_name,data_type from information_schema.columns "
        f"where table_schema='{schema}' and table_name in ({quoted}) order by table_name,ordinal_position"
    )
    result = subprocess.run(
        ["docker", "exec", args.container, "psql", "-U", args.user, "-d", args.database, "-At", "-F", "\t", "-c", sql],
        check=True,
        capture_output=True,
        text=True,
    )
    columns: dict[str, list[tuple[str, str]]] = {name: [] for name in names}
    for line in result.stdout.splitlines():
        table, column, data_type = line.split("\t", 2)
        columns[table].append((column, data_type))
    missing = [name for name, values in columns.items() if not values]
    if missing:
        raise SystemExit("missing verified relations: " + ", ".join(missing))
    return columns


def source_tables() -> tuple[dict, list[dict]]:
    document = yaml.safe_load(SOURCE_FILE.read_text(encoding="utf-8")) or {}
    sources = document.get("sources") or []
    if len(sources) != 1 or sources[0].get("name") != "pm_ods_v2":
        raise SystemExit("expected exactly one pm_ods_v2 source declaration")
    tables = sources[0].get("tables") or []
    if len(tables) != 10:
        raise SystemExit(f"expected 10 ODS source tables, found {len(tables)}")
    return document, tables


def field_label(name: str) -> str:
    if name in FIELD_LABELS:
        return FIELD_LABELS[name]
    tokens = name.split("_")
    translated = [TOKEN_LABELS.get(token, token.upper()) for token in tokens]
    return "".join(translated)


def semantic_spec(name: str) -> dict:
    base = {"overrideSource": "PJM dbt package contract v2.2.3", "technicalOnly": False}
    if name.startswith("stg_pm__"):
        return {**base, "modelType": "STG", "layer": "STG", "technicalOnly": True}
    if name in ALIASES:
        keys = ["context", "alias_raw"] if name == "dim_signature_status_alias" else ["alias_raw"]
        return {
            **base,
            "modelType": "DIMENSION",
            "layer": "DWD",
            "grain": {"statement": "每行代表一个源端取值到标准编码的映射", "keys": keys},
            "fieldRoles": {},
            "technicalOnly": True,
        }
    if name in DIMENSIONS:
        domain, definition, key, statement = DIMENSIONS[name]
        return {
            **base,
            "modelType": "DIMENSION",
            "layer": "DWD",
            "grain": {"statement": statement, "keys": [key]},
            "domainCode": domain,
            "consumptionScenarios": ["PJM 公共维度标准化与事实关联"],
            "fieldRoles": {},
            "dimensionStrategy": "STATIC_DBT_SQL_TYPE_1",
            "dimensionDefinitionCode": definition,
        }
    if name in FACTS:
        spec = FACTS[name]
        return {
            **base,
            "modelType": "FACT",
            "layer": "DWD",
            "grain": {"statement": spec["grain"], "keys": spec["keys"]},
            "factShape": spec["shape"],
            "timeSemantics": {"type": spec["time_type"], "fields": spec["time_fields"]},
            "domainCode": spec["domain"],
            "sourceRefs": [{"kind": "TABLE", "ref": spec["source"], "layer": "ODS"}],
            "fieldRoles": {},
        }
    if name in SUMMARIES:
        domain, keys, statement = SUMMARIES[name]
        return {
            **base,
            "modelType": "SUMMARY",
            "layer": "DWS",
            "grain": {"statement": statement, "keys": keys},
            "domainCode": domain,
            "fieldRoles": {},
        }
    if name in APPLICATIONS:
        domain, keys, scenario = APPLICATIONS[name]
        return {
            **base,
            "modelType": "APPLICATION",
            "layer": "ADS",
            "grain": {"statement": " + ".join(keys) + " 唯一确定一行输出", "keys": keys},
            "domainCode": domain,
            "consumptionScenarios": [scenario],
            "fieldRoles": {},
        }
    raise SystemExit(f"missing semantic specification for {name}")


def field_role(model: str, column: str, data_type: str, semantics: dict) -> str:
    keys = set(semantics.get("grain", {}).get("keys", []))
    if column in keys:
        return "KEY"
    if model in DIMENSIONS or model in ALIASES:
        return "ATTRIBUTE"
    if model in FACTS:
        spec = FACTS[model]
        if column in spec["time_fields"]:
            return "TIME"
        if column in spec["measures"]:
            return "MEASURE"
        return "ATTRIBUTE"
    if model in SUMMARIES or model in APPLICATIONS:
        if data_type in {"smallint", "integer", "bigint", "numeric", "real", "double precision"}:
            return "MEASURE"
        if data_type.startswith("timestamp") or data_type == "date":
            return "TIME"
        return "ATTRIBUTE"
    return "ATTRIBUTE"


def load_models(path: Path) -> tuple[dict, list[dict]]:
    document = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    models = document.get("models") or []
    return document, models


def synchronize(path: Path, verified: dict[str, list[tuple[str, str]]]) -> None:
    document, models = load_models(path)
    by_name = {model["name"]: model for model in models}
    expected = set(verified)
    actual = set(by_name)
    if actual - expected:
        raise SystemExit(f"{path.name} declares unknown models: {sorted(actual - expected)}")
    for name in sorted(expected - actual):
        model = {"name": name, "description": MODEL_DESCRIPTIONS.get(name, field_label(name))}
        models.append(model)
        by_name[name] = model

    for model in models:
        name = model["name"]
        semantics = semantic_spec(name)
        existing_columns = {column["name"]: column for column in model.get("columns") or []}
        synchronized_columns = []
        roles = {}
        for column_name, data_type in verified[name]:
            column = existing_columns.get(column_name, {"name": column_name})
            column["description"] = column.get("description") or field_label(column_name)
            column["data_type"] = data_type
            required_tests: list[str] = []
            if name in NEW_STG_MODELS and column_name == "source_row_id":
                required_tests = ["unique", "not_null"]
            if name in FACTS and name in MODEL_DESCRIPTIONS:
                if column_name in FACTS[name]["keys"]:
                    required_tests = ["unique", "not_null"]
                elif column_name == "project_no":
                    required_tests = ["not_null"]
            if (name in SUMMARIES or name in APPLICATIONS) and name in MODEL_DESCRIPTIONS and column_name == "period_month":
                required_tests = ["not_null"]
            if required_tests:
                existing_tests = list(column.get("data_tests") or column.get("tests") or [])
                column["data_tests"] = list(dict.fromkeys([*existing_tests, *required_tests]))
            synchronized_columns.append(column)
            if not semantics["technicalOnly"]:
                roles[column_name] = field_role(name, column_name, data_type, semantics)
        stale = set(existing_columns) - {name for name, _ in verified[name]}
        if stale:
            raise SystemExit(f"{name} has stale schema columns: {sorted(stale)}")
        if not semantics["technicalOnly"]:
            semantics["fieldRoles"] = roles
        if name in DIMENSIONS:
            attributes = []
            for order, column in enumerate(synchronized_columns, 1):
                meta = column.setdefault("meta", {})
                meta.setdefault("dts", {})["dimensionAttributeCode"] = column["name"].upper()
                attributes.append({
                    "code": column["name"].upper(),
                    "name": column["description"],
                    "definition": column["description"],
                    "primaryKey": column["name"] in semantics["grain"]["keys"],
                    "order": order,
                })
            semantics["dimensionDefinition"] = {
                "name": model.get("description") or name,
                "abbreviation": name,
                "definition": semantics["grain"]["statement"],
                "attributes": attributes,
            }
        model["config"] = {**(model.get("config") or {}), "contract": {"enforced": True}}
        model["meta"] = {**(model.get("meta") or {}), "dts": semantics}
        if name in MODEL_GRAIN_TESTS:
            model["data_tests"] = [
                {
                    "unique_combination": {
                        "arguments": {"combination_of_columns": MODEL_GRAIN_TESTS[name]}
                    }
                }
            ]
        model["columns"] = synchronized_columns

    document["version"] = 2
    document["models"] = models
    rendered = yaml.safe_dump(document, allow_unicode=True, sort_keys=False, width=140)
    path.write_text(rendered, encoding="utf-8")


def synchronize_sources(verified: dict[str, list[tuple[str, str]]]) -> int:
    document, tables = source_tables()
    for table in tables:
        identifier = table.get("identifier") or table["name"]
        if identifier not in verified:
            raise SystemExit(f"missing verified source relation: {identifier}")
        existing_columns = {column["name"]: column for column in table.get("columns") or []}
        synchronized_columns = []
        for column_name, data_type in verified[identifier]:
            column = existing_columns.get(column_name, {"name": column_name})
            column["description"] = column.get("description") or field_label(column_name)
            column["data_type"] = data_type
            synchronized_columns.append(column)
        stale = set(existing_columns) - {name for name, _ in verified[identifier]}
        if stale:
            raise SystemExit(f"{identifier} has stale source columns: {sorted(stale)}")
        table["columns"] = synchronized_columns
    rendered = yaml.safe_dump(document, allow_unicode=True, sort_keys=False, width=140)
    SOURCE_FILE.write_text(rendered, encoding="utf-8")
    return sum(len(value) for value in verified.values())


def main() -> None:
    args = parse_args()
    names = model_names()
    verified = relation_columns(args, names, args.model_schema)
    declarations: set[str] = set()
    file_models: dict[Path, dict[str, list[tuple[str, str]]]] = {}
    for path in MODEL_FILES:
        if path.name == "pm_stg_v2.yml":
            expected_names = {name for name in names if name.startswith("stg_pm__")}
        else:
            expected_names = set(names) - {name for name in names if name.startswith("stg_pm__")}
        selected = {name: verified[name] for name in expected_names}
        overlap = declarations.intersection(selected)
        if overlap:
            raise SystemExit("duplicate model declarations: " + ", ".join(sorted(overlap)))
        declarations.update(selected)
        file_models[path] = selected
    if declarations != set(names):
        raise SystemExit("schema declarations do not cover all SQL models: " + ", ".join(sorted(set(names) - declarations)))
    for path, selected in file_models.items():
        synchronize(path, selected)
    _, tables = source_tables()
    source_names = [table.get("identifier") or table["name"] for table in tables]
    source_column_count = synchronize_sources(relation_columns(args, source_names, args.source_schema))
    print(
        f"synchronized {len(names)} models/{sum(len(value) for value in verified.values())} columns "
        f"and {len(source_names)} sources/{source_column_count} columns"
    )


if __name__ == "__main__":
    main()
