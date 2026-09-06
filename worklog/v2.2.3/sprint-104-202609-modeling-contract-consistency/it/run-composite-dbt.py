#!/usr/bin/env python3
"""Run Sprint-104 IT-04 with an isolated, ephemeral dbt project.

Run this only from the deployment checkout after the committed implementation has
been pulled.  The script never reads credentials or creates business relations.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path


EXIT_OK = 0
EXIT_PREREQUISITE = 2
EXIT_UNEXPECTED_DBT_RESULT = 3
EXIT_EVIDENCE_WRITE_FAILED = 4
MACRO_PATTERN = re.compile(
    r"\{% test dts_unique_combination\(model, combination_of_columns\) %\}.*?\{% endtest %\}",
    re.DOTALL,
)
IDENTIFIER = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profiles-dir", type=Path, required=True, help="Existing dbt profiles directory; credentials remain there.")
    parser.add_argument("--profile", default="dts", help="Existing dbt profile name (default: dts).")
    parser.add_argument("--target", help="Optional existing dbt target name.")
    parser.add_argument("--evidence", type=Path, required=True, help="Destination JSON evidence file.")
    parser.add_argument("--source-file", type=Path, help="Committed DbtConfigService.java; defaults to the checkout containing this script.")
    return parser.parse_args()


def checkout_root(script: Path) -> Path:
    for parent in (script, *script.parents):
        candidate = parent / "source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtConfigService.java"
        if candidate.is_file():
            return parent
    raise FileNotFoundError("Cannot locate source/dts-platform from this script")


def require_identifier(value: str, label: str) -> str:
    if not IDENTIFIER.fullmatch(value):
        raise ValueError(f"{label} must be a dbt identifier")
    return value


def read_formal_macro(source_file: Path) -> str:
    content = source_file.read_text(encoding="utf-8")
    match = MACRO_PATTERN.search(content)
    if match is None:
        raise ValueError("dts_unique_combination macro is missing from committed DbtConfigService.java")
    return match.group(0) + "\n"


def write_project(project_dir: Path, profile: str, macro: str) -> None:
    (project_dir / "macros").mkdir()
    (project_dir / "models").mkdir()
    (project_dir / "dbt_project.yml").write_text(
        f"name: sprint104_composite_key_it\nversion: '1.0'\nprofile: {profile}\nmodel-paths: ['models']\nmacro-paths: ['macros']\n",
        encoding="utf-8",
    )
    (project_dir / "macros/dts_unique_combination.sql").write_text(macro, encoding="utf-8")
    models = {
        "valid_order": "select * from (values ('P001', '202601'), ('P001', '202602')) as sample(project_id, month_id)\n",
        "valid_reversed": "select * from (values ('P001', '202601'), ('P001', '202602')) as sample(project_id, month_id)\n",
        "duplicate_combination": "select * from (values ('P001', '202601'), ('P001', '202601')) as sample(project_id, month_id)\n",
        "duplicate_reversed": "select * from (values ('P001', '202601'), ('P001', '202601')) as sample(project_id, month_id)\n",
        "null_key": "select * from (values ('P001', null), ('P002', '202601')) as sample(project_id, month_id)\n",
    }
    for name, sql in models.items():
        (project_dir / "models" / f"{name}.sql").write_text("{{ config(materialized='ephemeral') }}\n" + sql, encoding="utf-8")
    (project_dir / "models/schema.yml").write_text(
        """version: 2
models:
  - name: valid_order
    tests:
      - dts_unique_combination:
          combination_of_columns: [project_id, month_id]
    columns:
      - name: project_id
        tests: [not_null]
      - name: month_id
        tests: [not_null]
  - name: valid_reversed
    tests:
      - dts_unique_combination:
          combination_of_columns: [month_id, project_id]
    columns:
      - name: project_id
        tests: [not_null]
      - name: month_id
        tests: [not_null]
  - name: duplicate_combination
    tests:
      - dts_unique_combination:
          combination_of_columns: [project_id, month_id]
  - name: duplicate_reversed
    tests:
      - dts_unique_combination:
          combination_of_columns: [month_id, project_id]
  - name: null_key
    columns:
      - name: project_id
        tests: [not_null]
      - name: month_id
        tests: [not_null]
""",
        encoding="utf-8",
    )


def redact_output(output: str) -> str:
    output = re.sub(r"(?i)(password|pwd)=([^\s&]+)", r"\1=<redacted>", output)
    return re.sub(r"(://[^:\s/@]+:)[^@\s/]+(@)", r"\1<redacted>\2", output)


def run_test(dbt: str, project_dir: Path, profiles_dir: Path, profile: str, target: str | None, selector: str) -> dict[str, object]:
    command = [dbt, "test", "--project-dir", str(project_dir), "--profiles-dir", str(profiles_dir), "--profile", profile, "--select", selector]
    if target:
        command.extend(["--target", target])
    completed = subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=False)
    return {
        "selector": selector,
        "exit_code": completed.returncode,
        "output_tail": redact_output(completed.stdout[-4000:]),
    }


def write_evidence(path: Path, evidence: dict[str, object]) -> bool:
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        return True
    except OSError:
        return False


def main() -> int:
    args = parse_args()
    evidence: dict[str, object] = {
        "case": "Sprint-104 IT-04 composite grain dbt test",
        "timestamp_utc": datetime.now(timezone.utc).isoformat(),
        "status": "NOT_RUN",
        "cases": [],
    }
    try:
        profile = require_identifier(args.profile, "profile")
        target = require_identifier(args.target, "target") if args.target else None
        if not args.profiles_dir.is_dir():
            raise ValueError("profiles-dir does not exist or is not a directory")
        dbt = shutil.which("dbt")
        if dbt is None:
            raise ValueError("dbt CLI is not available on PATH")
        source_file = args.source_file or checkout_root(Path(__file__).resolve()) / "source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtConfigService.java"
        macro = read_formal_macro(source_file)
        evidence["macro_source_sha256"] = hashlib.sha256(macro.encode("utf-8")).hexdigest()
        evidence["dbt_command"] = shutil.which("dbt")
        expected = {
            "valid_order": 0,
            "valid_reversed": 0,
            "duplicate_combination": 1,
            "duplicate_reversed": 1,
            "null_key": 1,
        }
        with tempfile.TemporaryDirectory(prefix="sprint104-composite-dbt-") as temporary:
            project_dir = Path(temporary)
            write_project(project_dir, profile, macro)
            results = [run_test(dbt, project_dir, args.profiles_dir, profile, target, selector) for selector in expected]
        evidence["cases"] = results
        evidence["expectations"] = expected
        evidence["status"] = "PASS" if all(result["exit_code"] == expected[result["selector"]] for result in results) else "FAIL"
        exit_code = EXIT_OK if evidence["status"] == "PASS" else EXIT_UNEXPECTED_DBT_RESULT
    except (OSError, ValueError) as error:
        evidence["status"] = "PREREQUISITE_FAILED"
        evidence["error"] = str(error)
        exit_code = EXIT_PREREQUISITE
    if not write_evidence(args.evidence, evidence):
        return EXIT_EVIDENCE_WRITE_FAILED
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
