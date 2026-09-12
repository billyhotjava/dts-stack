#!/usr/bin/env python3
"""Read-only F9 migration baseline. Does not change directory, sessions, or business data."""
import argparse
import json
import subprocess
from datetime import datetime, timezone

parser = argparse.ArgumentParser()
parser.add_argument("--container", default="dts-stack-dts-pg-1")
parser.add_argument("--output", required=True)
args = parser.parse_args()

def query(database, sql):
    command = ["docker", "exec", args.container, "psql", "-X", "-U", "postgres", "-d", database, "-qAt", "-v", "ON_ERROR_STOP=1", "-c", "BEGIN READ ONLY; " + sql + "; ROLLBACK;"]
    return json.loads(subprocess.run(command, text=True, capture_output=True, check=True, timeout=20).stdout.strip())

organization = query("dts_admin", """select json_build_object(
 'departments', coalesce(json_agg(coalesce(nullif(dept_code,''),id::text)) filter(where not is_root and coalesce(upper(status),'') not in ('DISABLED','INACTIVE','DELETED')),'[]'::json),
 'nodeIdFallbackCount', count(*) filter(where not is_root and (dept_code is null or dept_code='')))
 from organization_node""")
platform = query("dts_platform", """select json_build_object(
 'planCount',(select count(*) from modeling_warehouse_plan),
 'departments',(select coalesce(json_agg(distinct owner_department_id),'[]'::json) from modeling_warehouse_plan),
 'duplicateDepartments',(select count(*) from (select tenant_id,owner_department_id from modeling_warehouse_plan group by tenant_id,owner_department_id having count(*)>1) duplicates),
 'unwritablePlans',(select count(*) from modeling_warehouse_plan where lifecycle_status in ('PUBLISHED','ARCHIVED')),
 'adsCount',(select count(*) from modeling_model_spec where model_type='APPLICATION'),
 'modelTypes',(select coalesce(json_object_agg(model_type,n),'{}'::json) from (select model_type,count(*) n from modeling_model_spec group by model_type) types),
 'crossPlanReferences',(select count(*) from modeling_model_spec s cross join lateral jsonb_array_elements(coalesce(s.depends_on,'[]'::jsonb)||coalesce(s.dimension_refs,'[]'::jsonb)) ref join modeling_model_spec upstream on upstream.id::text=ref->>'modelSpecId' and upstream.tenant_id=s.tenant_id where s.plan_id<>upstream.plan_id),
 'sessionsMissingDepartment',(select count(*) from portal_sessions where dept_code is null or btrim(dept_code)=''))""")
problems = []
if set(platform["departments"]) - set(organization["departments"]):
    problems.append("DEPARTMENT_DIRECTORY_MAPPING_REQUIRED")
if platform["duplicateDepartments"]:
    problems.append("DUPLICATE_DEPARTMENT_CONTEXT")
if platform["unwritablePlans"]:
    problems.append("DEFAULT_CONTEXT_RECOVERY_REQUIRED")
if platform["adsCount"]:
    problems.append("HISTORICAL_ADS_OWNER_REQUIRES_STABLE_DIRECTORY_ID_REVIEW")
if platform["crossPlanReferences"]:
    problems.append("HISTORICAL_CROSS_DEPARTMENT_REFERENCES_REQUIRE_REVIEW")
report = {"capturedAt": datetime.now(timezone.utc).isoformat(), "readOnly": True, "organization": organization,
          "platform": platform, "migrationBlockers": problems, "customerHistoricalDowngradeScan": "NOT_RUN",
          "customerNote": "客户现场访问与历史密级恢复不在本次本机只读扫描的证据范围内。"}
with open(args.output, "w", encoding="utf-8") as output:
    json.dump(report, output, ensure_ascii=False, indent=2)
print(json.dumps({"output": args.output, "migrationBlockers": problems}, ensure_ascii=False))
raise SystemExit(1 if problems else 0)
