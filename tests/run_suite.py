#!/usr/bin/env python3
import argparse
import contextlib
import datetime as dt
import fcntl
import json
import os
import subprocess
import sys
import time
from pathlib import Path
from typing import Any
from xml.sax.saxutils import escape as xml_escape


DEFAULT_MAX_OUTPUT_CHARS = 4000
DEFAULT_SUITE_CONFIG = Path(__file__).resolve().with_name("suites.json")


@contextlib.contextmanager
def acquire_suite_lock(lock_path: Path, suite_name: str):
    lock_path.parent.mkdir(parents=True, exist_ok=True)
    with open(lock_path, "a+", encoding="utf-8") as lock_file:
        fcntl.flock(lock_file, fcntl.LOCK_EX)
        lock_file.seek(0)
        lock_file.truncate(0)
        lock_file.write(f"suite={suite_name}\npid={os.getpid()}\n")
        lock_file.flush()
        try:
            yield
        finally:
            fcntl.flock(lock_file, fcntl.LOCK_UN)


def load_suites(config_path: Path) -> dict[str, list[dict[str, Any]]]:
    try:
        payload = json.loads(config_path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise RuntimeError(f"suite config file not found: {config_path}") from exc
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"invalid suite config JSON: {config_path}: {exc}") from exc
    if not isinstance(payload, dict):
        raise RuntimeError("suite config root must be JSON object")
    suites: dict[str, list[dict[str, Any]]] = {}
    for key, value in payload.items():
        if not isinstance(key, str) or not isinstance(value, list):
            raise RuntimeError(f"invalid suite entry: {key}")
        suites[key] = value
    return suites


def normalize_quarantine(case_id: str, quarantine: Any) -> dict[str, Any]:
    if quarantine in (None, False):
        return {"enabled": False}
    if quarantine is True:
        return {"enabled": True}
    if not isinstance(quarantine, dict):
        raise RuntimeError(f"invalid quarantine config for case {case_id}")
    normalized = {
        "enabled": bool(quarantine.get("enabled", True)),
        "reason": str(quarantine.get("reason", "")).strip(),
        "slaHours": int(quarantine.get("slaHours", 0) or 0),
        "issue": str(quarantine.get("issue", "")).strip(),
        "openedAt": str(quarantine.get("openedAt", "")).strip(),
        "escalation": str(quarantine.get("escalation", "")).strip(),
    }
    return normalized


def normalize_case(case: dict[str, Any], idx: int) -> dict[str, Any]:
    case_id = str(case.get("id", f"CASE-{idx:03d}"))
    name = str(case.get("name", "Unnamed case"))
    required = bool(case.get("required", True))
    enabled = bool(case.get("enabled", True))
    timeout_seconds = int(case.get("timeoutSeconds", 30))
    command = case.get("command")
    if not isinstance(command, list) or not all(isinstance(x, str) for x in command):
        raise RuntimeError(f"invalid command for case {case_id}")
    env = case.get("env", {})
    if env is None:
        env = {}
    if not isinstance(env, dict):
        raise RuntimeError(f"invalid env for case {case_id}")
    tags = case.get("tags", [])
    if tags is None:
        tags = []
    if not isinstance(tags, list):
        raise RuntimeError(f"invalid tags for case {case_id}")
    tags = [str(t) for t in tags]
    owner = str(case.get("owner", "")).strip()
    business_journey = str(case.get("businessJourney", "")).strip()
    quarantine = normalize_quarantine(case_id, case.get("quarantine"))
    return {
        "id": case_id,
        "name": name,
        "required": required,
        "enabled": enabled,
        "timeoutSeconds": timeout_seconds,
        "command": command,
        "env": {str(k): str(v) for k, v in env.items()},
        "tags": tags,
        "owner": owner,
        "businessJourney": business_journey,
        "quarantine": quarantine,
    }


def trim_output(text: str, limit: int = DEFAULT_MAX_OUTPUT_CHARS) -> str:
    if len(text) <= limit:
        return text
    head = text[: limit // 2]
    tail = text[-(limit // 2) :]
    return f"{head}\n...<truncated {len(text) - limit} chars>...\n{tail}"


def render_md_report(
    suite: str,
    started_at: str,
    finished_at: str,
    records: list[dict[str, Any]],
    required_failures: int,
    metrics: dict[str, Any],
    business_journeys: list[dict[str, Any]],
) -> str:
    lines: list[str] = []
    lines.append(f"# Test Report: {suite}")
    lines.append("")
    lines.append(f"- Started: {started_at}")
    lines.append(f"- Finished: {finished_at}")
    lines.append(f"- Required failures: {required_failures}")
    total_failures = sum(1 for r in records if r["status"] != "PASS")
    lines.append(f"- Total failures: {total_failures}")
    lines.append(f"- Pass rate: {metrics.get('passRate', 0.0):.2%}")
    lines.append(f"- Owner coverage: {metrics.get('ownerCoverage', 0.0):.2%}")
    lines.append(f"- Quarantine cases: {metrics.get('quarantineCaseCount', 0)}")
    lines.append(f"- Quarantine failures: {metrics.get('quarantineFailureCount', 0)}")
    lines.append("")
    if business_journeys:
        lines.append("## Business Journeys")
        lines.append("")
        lines.append("| Journey | Case ID | Status | Owner |")
        lines.append("|---|---|---|---|")
        for item in business_journeys:
            lines.append(
                f"| {item['name']} | {item['caseId']} | {item['status']} | {item.get('owner', '') or '-'} |"
            )
        lines.append("")
    lines.append("| Case ID | Name | Required | Status | Exit Code | Duration(s) |")
    lines.append("|---|---|---|---|---:|---:|")
    for record in records:
        lines.append(
            f"| {record['id']} | {record['name']} | {record['required']} | {record['status']} | "
            f"{record.get('exitCode', '')} | {record['durationSec']:.2f} |"
        )
    lines.append("")
    for record in records:
        lines.append(f"## {record['id']} - {record['name']}")
        lines.append("")
        lines.append(f"- Status: {record['status']}")
        lines.append(f"- Required: {record['required']}")
        lines.append(f"- Exit Code: {record.get('exitCode', '')}")
        lines.append(f"- Duration(s): {record['durationSec']:.2f}")
        if record.get("owner"):
            lines.append(f"- Owner: {record['owner']}")
        if record.get("businessJourney"):
            lines.append(f"- Business Journey: {record['businessJourney']}")
        quarantine = record.get("quarantine", {}) or {}
        if quarantine.get("enabled"):
            lines.append(f"- Quarantine: yes ({quarantine.get('reason') or 'no reason'})")
            if quarantine.get("slaHours"):
                lines.append(f"- SLA Hours: {quarantine['slaHours']}")
        stdout = record.get("stdout", "")
        stderr = record.get("stderr", "")
        if stdout:
            lines.append("")
            lines.append("### stdout")
            lines.append("```text")
            lines.append(stdout)
            lines.append("```")
        if stderr:
            lines.append("")
            lines.append("### stderr")
            lines.append("```text")
            lines.append(stderr)
            lines.append("```")
        lines.append("")
    return "\n".join(lines)


def render_junit_report(suite: str, records: list[dict[str, Any]], started_at: str, finished_at: str) -> str:
    tests = len(records)
    failures = sum(1 for r in records if r["status"] != "PASS")
    duration = sum(float(r.get("durationSec", 0.0)) for r in records)
    suite_name = xml_escape(suite)
    lines: list[str] = []
    lines.append('<?xml version="1.0" encoding="UTF-8"?>')
    lines.append(
        f'<testsuite name="{suite_name}" tests="{tests}" failures="{failures}" '
        f'time="{duration:.3f}" timestamp="{xml_escape(started_at)}">'
    )
    lines.append(f'  <properties><property name="finishedAt" value="{xml_escape(finished_at)}"/></properties>')
    for record in records:
        case_id = xml_escape(str(record["id"]))
        case_name = xml_escape(str(record["name"]))
        sec = float(record.get("durationSec", 0.0))
        lines.append(f'  <testcase classname="{suite_name}" name="{case_id}: {case_name}" time="{sec:.3f}">')
        if record["status"] != "PASS":
            stderr = xml_escape(str(record.get("stderr", "")))
            stdout = xml_escape(str(record.get("stdout", "")))
            details = f"exitCode={record.get('exitCode', '')}\nstdout:\n{stdout}\nstderr:\n{stderr}"
            lines.append(f'    <failure message="case failed">{details}</failure>')
        lines.append("  </testcase>")
    lines.append("</testsuite>")
    return "\n".join(lines)


def parse_args(suite_names: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run DTS integration/system test suites")
    parser.add_argument("--suite", required=True, help="suite name from tests/suites.json")
    parser.add_argument(
        "--config",
        default=os.environ.get("DTS_SUITE_CONFIG", str(DEFAULT_SUITE_CONFIG)),
        help="suite config path; defaults to tests/suites.json or DTS_SUITE_CONFIG",
    )
    parser.add_argument("--include-optional", action="store_true", help="run cases with required=false")
    parser.add_argument("--include-disabled", action="store_true", help="run cases with enabled=false")
    parser.add_argument("--tag", action="append", default=[], help="only run cases containing this tag; repeatable")
    parser.add_argument("--fail-fast", action="store_true", help="stop suite at first required failure")
    parser.add_argument("--base-url", help="override DTS_BASE_URL for all cases")
    parser.add_argument("--mcp-http-endpoint", help="override DTS_MCP_HTTP_ENDPOINT for MCP HTTP cases")
    parser.add_argument("--mcp-stdio-cmd", help="override DTS_MCP_STDIO_CMD for MCP stdio cases")
    parser.add_argument("--dry-run", action="store_true", help="only print selected cases")
    parser.add_argument("--report-dir", default="tests/reports", help="directory for JSON/Markdown reports")
    parser.add_argument("--junit-path", help="write junit xml report to this path; default auto in report-dir")
    return parser.parse_args()


def build_business_journeys(records: list[dict[str, Any]]) -> list[dict[str, Any]]:
    journeys: list[dict[str, Any]] = []
    for record in records:
        journey_name = str(record.get("businessJourney", "")).strip()
        if not journey_name:
            continue
        journeys.append(
            {
                "name": journey_name,
                "caseId": record["id"],
                "status": record["status"],
                "owner": record.get("owner", ""),
                "quarantine": bool((record.get("quarantine") or {}).get("enabled")),
            }
        )
    return journeys


def build_suite_metrics(records: list[dict[str, Any]]) -> dict[str, Any]:
    case_count = len(records)
    pass_count = sum(1 for record in records if record["status"] == "PASS")
    fail_count = case_count - pass_count
    owned_case_count = sum(1 for record in records if str(record.get("owner", "")).strip())
    quarantine_records = [record for record in records if (record.get("quarantine") or {}).get("enabled")]
    quarantine_failure_count = sum(1 for record in quarantine_records if record["status"] != "PASS")
    return {
        "caseCount": case_count,
        "passCount": pass_count,
        "failCount": fail_count,
        "passRate": round(pass_count / case_count, 4) if case_count else 0.0,
        "ownedCaseCount": owned_case_count,
        "ownerCoverage": round(owned_case_count / case_count, 4) if case_count else 0.0,
        "quarantineCaseCount": len(quarantine_records),
        "quarantineFailureCount": quarantine_failure_count,
    }


def main() -> int:
    repo_root = Path(__file__).resolve().parent.parent
    default_config_path = Path(os.environ.get("DTS_SUITE_CONFIG", str(DEFAULT_SUITE_CONFIG))).expanduser()
    if not default_config_path.is_absolute():
        default_config_path = (repo_root / default_config_path).resolve()
    config_path = default_config_path
    suites = load_suites(config_path)
    args = parse_args(sorted(suites.keys()))
    config_path = Path(args.config).expanduser()
    if not config_path.is_absolute():
        config_path = (repo_root / config_path).resolve()
    suites = load_suites(config_path)
    if args.suite not in suites:
        available = ", ".join(sorted(suites.keys()))
        raise RuntimeError(f"unknown suite {args.suite}; available suites: {available}")
    raw_cases = suites[args.suite]

    selected_cases: list[dict[str, Any]] = []
    include_tags = {t.strip() for t in args.tag if t and t.strip()}
    for idx, case in enumerate(raw_cases, start=1):
        normalized = normalize_case(case, idx)
        required = bool(normalized["required"])
        enabled = bool(normalized["enabled"])
        case_tags = set(normalized["tags"])
        if not enabled and not args.include_disabled:
            continue
        if not required and not args.include_optional:
            continue
        if include_tags and not (include_tags & case_tags):
            continue
        selected_cases.append(normalized)

    if not selected_cases:
        print("[WARN] no cases selected. use --include-optional if suite has only optional cases.")
        return 0

    if args.dry_run:
        print(f"[INFO] suite={args.suite}, selected_cases={len(selected_cases)}")
        for case in selected_cases:
            print(
                f"- {case['id']} required={case['required']} enabled={case['enabled']} tags={case['tags']} "
                f"owner={case.get('owner') or '-'} quarantine={case.get('quarantine', {}).get('enabled', False)} "
                f"cmd={' '.join(case['command'])}"
            )
        return 0

    started = dt.datetime.now(dt.timezone.utc)
    env_base = os.environ.copy()
    if args.base_url:
        env_base["DTS_BASE_URL"] = args.base_url
    if args.mcp_http_endpoint:
        env_base["DTS_MCP_HTTP_ENDPOINT"] = args.mcp_http_endpoint
    if args.mcp_stdio_cmd:
        env_base["DTS_MCP_STDIO_CMD"] = args.mcp_stdio_cmd

    records: list[dict[str, Any]] = []
    required_failures = 0
    total_failures = 0

    lock_path = repo_root / "tests" / ".locks" / "run_suite.lock"
    print(f"[INFO] acquiring suite lock: {lock_path}")
    with acquire_suite_lock(lock_path, args.suite):
        print(f"[INFO] running suite={args.suite}, cases={len(selected_cases)}")
        for idx, case in enumerate(selected_cases, start=1):
            case_id = str(case["id"])
            name = str(case["name"])
            required = bool(case["required"])
            command = case["command"]
            timeout_seconds = int(case["timeoutSeconds"])
            case_env = case.get("env", {})

            print(f"[RUN] ({idx}/{len(selected_cases)}) {case_id} {name}")
            t0 = time.monotonic()
            env = env_base.copy()
            env.update(case_env)
            try:
                completed = subprocess.run(
                    command,
                    cwd=repo_root,
                    env=env,
                    capture_output=True,
                    text=True,
                    timeout=timeout_seconds,
                    check=False,
                )
                duration = time.monotonic() - t0
                status = "PASS" if completed.returncode == 0 else "FAIL"
                stdout = trim_output(completed.stdout or "")
                stderr = trim_output(completed.stderr or "")
                records.append(
                    {
                        "id": case_id,
                        "name": name,
                        "required": required,
                        "status": status,
                        "exitCode": completed.returncode,
                        "durationSec": duration,
                        "stdout": stdout,
                        "stderr": stderr,
                        "tags": case.get("tags", []),
                        "owner": case.get("owner", ""),
                        "businessJourney": case.get("businessJourney", ""),
                        "quarantine": case.get("quarantine", {"enabled": False}),
                    }
                )
                print(f"[{status}] {case_id} exit={completed.returncode} duration={duration:.2f}s")
                if stdout:
                    print(stdout.strip())
                if stderr:
                    print(stderr.strip(), file=sys.stderr)
                if status == "FAIL":
                    total_failures += 1
                    if required:
                        required_failures += 1
                        if args.fail_fast:
                            print(f"[INFO] fail-fast triggered by required case {case_id}", file=sys.stderr)
                            break
            except subprocess.TimeoutExpired as exc:
                duration = time.monotonic() - t0
                stdout = trim_output((exc.stdout or ""))
                stderr = trim_output((exc.stderr or ""))
                msg = f"timeout after {timeout_seconds}s"
                records.append(
                    {
                        "id": case_id,
                        "name": name,
                        "required": required,
                        "status": "FAIL",
                        "exitCode": 124,
                        "durationSec": duration,
                        "stdout": stdout,
                        "stderr": f"{msg}\n{stderr}",
                        "tags": case.get("tags", []),
                        "owner": case.get("owner", ""),
                        "businessJourney": case.get("businessJourney", ""),
                        "quarantine": case.get("quarantine", {"enabled": False}),
                    }
                )
                print(f"[FAIL] {case_id} {msg}", file=sys.stderr)
                total_failures += 1
                if required:
                    required_failures += 1
                    if args.fail_fast:
                        print(f"[INFO] fail-fast triggered by required case {case_id}", file=sys.stderr)
                        break

    finished = dt.datetime.now(dt.timezone.utc)
    report_dir = (repo_root / args.report_dir).resolve()
    report_dir.mkdir(parents=True, exist_ok=True)
    stamp = finished.strftime("%Y%m%d-%H%M%S")
    report_base = f"{stamp}-{args.suite}"
    metrics = build_suite_metrics(records)
    business_journeys = build_business_journeys(records)

    json_report_path = report_dir / f"{report_base}.json"
    md_report_path = report_dir / f"{report_base}.md"

    report_payload = {
        "suite": args.suite,
        "startedAt": started.isoformat(),
        "finishedAt": finished.isoformat(),
        "requiredFailures": required_failures,
        "totalFailures": total_failures,
        "caseCount": len(records),
        "metrics": metrics,
        "businessJourneys": business_journeys,
        "cases": records,
    }
    json_report_path.write_text(json.dumps(report_payload, ensure_ascii=False, indent=2), encoding="utf-8")
    md_report_path.write_text(
        render_md_report(
            args.suite,
            started.isoformat(),
            finished.isoformat(),
            records,
            required_failures,
            metrics,
            business_journeys,
        ),
        encoding="utf-8",
    )
    if args.junit_path:
        junit_report_path = Path(args.junit_path).expanduser().resolve()
    else:
        junit_report_path = report_dir / f"{report_base}.xml"
    junit_report_path.parent.mkdir(parents=True, exist_ok=True)
    junit_report_path.write_text(
        render_junit_report(args.suite, records, started.isoformat(), finished.isoformat()),
        encoding="utf-8",
    )

    print(f"[INFO] report json: {json_report_path}")
    print(f"[INFO] report md  : {md_report_path}")
    print(f"[INFO] report xml : {junit_report_path}")
    print(
        f"[INFO] summary: cases={len(records)} totalFailures={total_failures} "
        f"requiredFailures={required_failures} quarantineFailures={metrics['quarantineFailureCount']} "
        f"ownerCoverage={metrics['ownerCoverage']:.2%}"
    )
    if required_failures > 0:
        print(f"[FAIL] suite {args.suite} has {required_failures} required case failure(s)", file=sys.stderr)
        return 1
    print(f"[PASS] suite {args.suite} completed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
