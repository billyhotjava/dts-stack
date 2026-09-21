"""Run the actual monthly SQL against CTE fixtures (no persistent writes).

Run in the build checkout with psycopg2 installed and PG* connection variables.
"""

import json
import re
from pathlib import Path

import psycopg2


project = Path(__file__).resolve().parents[1]
manifest = json.loads((project / "target/manifest.json").read_text())
columns = manifest["nodes"]["model.pm_analytics_v3.biz_dwd_project_node_v2"]["columns"]
model_sql = (project / "models/dws/biz_dws_progress_monthly_v2.sql").read_text()
model_sql = re.sub(r"\{\{\s*config\(.*?\)\s*\}\}", "", model_sql)
model_sql = model_sql.replace("{{ ref('biz_dwd_project_node_v2') }}", "fixture")

# A: completion-only month and year rollover; B: matching plan/completion month;
# C: unfinished node must not create an actual-month row; D: same-month completion.
cases = [
    ("A", "2025-12", "2026-01", True),
    ("B", "2026-01", "2026-02", True),
    ("B", "2026-02", None, False),
    ("C", "2026-03", None, False),
    ("D", "2026-04", "2026-04", True),
]

with psycopg2.connect("") as connection:
    connection.set_session(readonly=True)
    with connection.cursor() as cursor:
        fixtures = []
        for project_no, planned, actual, completed in cases:
            values = {name: False if col["data_type"] == "boolean" else None for name, col in columns.items()}
            values.update(
                project_no=project_no,
                plan_year=int(planned[:4]),
                plan_quarter=(int(planned[5:]) - 1) // 3 + 1,
                plan_month=planned,
                actual_year=int(actual[:4]) if actual else None,
                actual_month=actual,
                is_completed=completed,
                is_incomplete=not completed,
                is_pending_normal=not completed,
                is_on_time=completed and actual == planned,
                is_overdue_completed=completed and actual != planned,
            )
            fixtures.append("SELECT " + ", ".join(
                cursor.mogrify("%s", (values[name],)).decode()
                + "::" + column["data_type"] + ' AS "' + name + '"'
                for name, column in columns.items()
            ))
        cursor.execute("WITH fixture AS (" + " UNION ALL ".join(fixtures) + ") "
                       + "SELECT * FROM (" + model_sql + ") result")
        rows = [dict(zip([c.name for c in cursor.description], row)) for row in cursor.fetchall()]

by_key = {(r["project_no"], r["plan_month"]): r for r in rows}
assert len(rows) == len(by_key) == 6, rows
assert set(by_key) == {("A", "2025-12"), ("A", "2026-01"), ("B", "2026-01"),
                       ("B", "2026-02"), ("C", "2026-03"), ("D", "2026-04")}
outside_only = by_key["A", "2026-01"]
assert (outside_only["plan_year"], outside_only["plan_quarter"]) == (2026, 1)
for name, value in outside_only.items():
    if name.endswith("_cnt") or name.endswith("_cnt_v2"):
        assert value == (1 if name in ("outside_completed_cnt", "completed_total_cnt") else 0), (name, value)
assert by_key["B", "2026-02"]["total_cnt"] == 1
assert by_key["B", "2026-02"]["outside_completed_cnt"] == 1
assert by_key["B", "2026-02"]["completed_total_cnt"] == 1
assert by_key["D", "2026-04"]["outside_completed_cnt"] == 0
assert by_key["D", "2026-04"]["completed_total_cnt"] == 1
assert sum(r["outside_completed_cnt"] for r in rows) == 2
assert all(value is not None for row in rows for value in row.values())
print("PASS: completion-only month, year rollover, matching month, unfinished and same-month nodes")
