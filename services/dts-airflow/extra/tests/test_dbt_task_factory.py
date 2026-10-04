from datetime import datetime, timedelta, timezone
import importlib.util
import io
import json
import os
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest import mock
from uuid import UUID


FACTORY_PATH = (
    Path(__file__).resolve().parents[1]
    / "dts_runtime"
    / "dbt_task_factory.py"
)
MANAGED_DBT_WRAPPER_PATH = (
    FACTORY_PATH.parents[4]
    / "services"
    / "dts-dbt"
    / "run-model-build.sh"
)


def load_factory():
    spec = importlib.util.spec_from_file_location(
        "dts_runtime.dbt_task_factory",
        FACTORY_PATH,
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class FakeProcess:
    def __init__(self, wait_results):
        self.wait_results = list(wait_results)
        self.wait_timeouts = []
        self.returncode = None
        self.terminate_calls = 0
        self.kill_calls = 0

    def wait(self, timeout=None):
        self.wait_timeouts.append(timeout)
        result = self.wait_results.pop(0)
        if isinstance(result, BaseException):
            raise result
        self.returncode = result
        return result

    def poll(self):
        return self.returncode

    def terminate(self):
        self.terminate_calls += 1

    def kill(self):
        self.kill_calls += 1


class DbtTaskFactoryTest(unittest.TestCase):
    def test_platform_error_logs_only_safe_diagnostic_identities(self):
        correlation = "711387e4-faa4-4e23-82c3-8b3221e8929c"
        cases = [
            ({"code": "MODEL_RELEASE_CURRENT_PHYSICAL_OBSERVATION_REQUIRED",
              "correlationId": correlation, "message": "secret-token dynamic-path"}, True),
            ({"code": "secret-token\nforged-log", "correlationId": "secret-token"}, False),
            (["secret-token"], False),
        ]
        for body, valid in cases:
            with self.subTest(body=body), mock.patch.dict(os.environ, {
                "DTS_PLATFORM_INTERNAL_BASE_URL": "http://dts-platform:8080",
                "DTS_AIRFLOW_TO_PLATFORM_TOKEN": "secret-token",
            }), mock.patch.object(self.factory.urllib_request, "urlopen", side_effect=
                self.factory.urllib_error.HTTPError("http://internal/dynamic-path", 422, "Rejected", None,
                    io.BytesIO(json.dumps(body).encode("utf-8")))
            ):
                with self.assertRaises(RuntimeError) as raised:
                    self.factory._platform_request("/dynamic-path")
                message = str(raised.exception)
                self.assertIn("HTTP 422", message)
                self.assertNotIn("secret-token", message)
                self.assertNotIn("dynamic-path", message)
                if valid:
                    self.assertIn("code=MODEL_RELEASE_CURRENT_PHYSICAL_OBSERVATION_REQUIRED", message)
                    self.assertIn(f"correlationId={correlation}", message)
                else:
                    self.assertNotIn("code=", message)

    @classmethod
    def setUpClass(cls):
        cls.factory = load_factory()

    def setUp(self):
        runtime = mock.patch.object(
            self.factory,
            "_require_dbt_runtime_container",
            return_value="a" * 64,
        )
        runtime.start()
        self.addCleanup(runtime.stop)

    def runtime_spec(self):
        return {
            "pipelineRunGroupId": "10000000-0000-0000-0000-000000000001",
            "runPurpose": "RELEASE_BUILD",
            "projectBundleChecksum": "a" * 64,
            "selector": "model_a model_b",
            "targetName": "dev",
            "profileLeaseId": "20000000-0000-0000-0000-000000000002",
            "expiresAt": "2026-07-27T12:00:00Z",
            "credentialVersionRef": "sha256:" + "b" * 64,
        }

    def lease_response(self, *, lease_id=None, expires_at=None):
        runtime = self.runtime_spec()
        return {
            "profileLeaseId": lease_id or runtime["profileLeaseId"],
            "targetName": runtime["targetName"],
            "expiresAt": expires_at or "2099-07-30T12:00:00Z",
            "credentialVersionRef": runtime["credentialVersionRef"],
        }

    def container_inspection(
        self,
        *,
        container_id=None,
        service_label="dts-dbt",
        running=True,
        project_rw=True,
        profile_rw=False,
    ):
        return (
            200,
            json.dumps(
                {
                    "Id": container_id or "a" * 64,
                    "Config": {
                        "Labels": {
                            "com.docker.compose.service": service_label,
                        }
                    },
                    "State": {"Running": running},
                    "Mounts": [
                        {"Destination": "/opt/dbt", "RW": project_rw},
                        {
                            "Destination": "/run/dts-dbt-runtime",
                            "RW": profile_rw,
                        },
                    ],
                },
                separators=(",", ":"),
            ).encode("utf-8"),
        )

    def test_rejects_runtime_overrides_in_dag_run_conf(self):
        allowed = {
            "pipelineRunGroupId": "10000000-0000-0000-0000-000000000001",
            "candidateId": "30000000-0000-0000-0000-000000000003",
            "candidateVersion": 3,
            "attempt": 1,
            "runPurpose": "RELEASE_BUILD",
            "runtimeSpecToken": "opaque",
            "bundleChecksum": "c" * 64,
        }

        self.assertEqual(
            self.factory._validate_release_build_conf(allowed),
            allowed,
        )
        for forbidden in (
            "projectDir",
            "selector",
            "target",
            "profile",
            "image",
            "command",
            "callable",
        ):
            with self.subTest(forbidden=forbidden):
                with self.assertRaisesRegex(ValueError, "unsupported"):
                    self.factory._validate_release_build_conf(
                        {**allowed, forbidden: "attacker-controlled"}
                    )

    def test_accepts_only_server_owned_manual_operational_run_conf(self):
        allowed = {
            "pipelineRunGroupId": "10000000-0000-0000-0000-000000000001",
            "bindingId": "40000000-0000-0000-0000-000000000004",
            "bindingVersion": 2,
            "runPurpose": "OPERATIONAL_RUN",
            "triggerType": "MANUAL",
            "runtimeSpecToken": "opaque",
            "bundleChecksum": "c" * 64,
        }

        self.assertEqual(
            self.factory._validate_operational_run_conf(allowed),
            allowed,
        )
        for forbidden in ("projectDir", "selector", "target", "profile"):
            with self.subTest(forbidden=forbidden):
                with self.assertRaisesRegex(ValueError, "unsupported"):
                    self.factory._validate_operational_run_conf(
                        {**allowed, forbidden: "attacker-controlled"}
                    )

    def test_cron_prepare_opens_the_durable_run_before_consuming_runtime(self):
        class DagRun:
            conf = {}
            dag_run_id = "scheduled__2026-07-28T02:00:00+00:00"
            logical_date = "2026-07-28T02:00:00+00:00"
            run_type = "scheduled"

        calls = []
        original = self.factory._platform_request

        def fake_request(path, **kwargs):
            calls.append((path, kwargs))
            if path.endswith("/scheduled-runs/open"):
                return {
                    "pipelineRunGroupId":
                        "10000000-0000-0000-0000-000000000001",
                    "bindingId":
                        "40000000-0000-0000-0000-000000000004",
                    "bindingVersion": 2,
                    "runPurpose": "OPERATIONAL_RUN",
                    "triggerType": "CRON",
                    "runtimeSpecToken": "opaque",
                    "bundleChecksum": "c" * 64,
                }
            return {
                **self.runtime_spec(),
                "runPurpose": "OPERATIONAL_RUN",
                "projectBundleChecksum": "c" * 64,
            }

        self.factory._platform_request = fake_request
        try:
            runtime = self.factory._prepare_runtime_task(
                purpose="OPERATIONAL_RUN",
                binding_id="40000000-0000-0000-0000-000000000004",
                deployment_checksum="d" * 64,
                dag_run=DagRun(),
            )
        finally:
            self.factory._platform_request = original

        self.assertEqual(runtime["runPurpose"], "OPERATIONAL_RUN")
        self.assertTrue(calls[0][0].endswith("/scheduled-runs/open"))
        self.assertEqual(
            calls[0][1]["payload"]["dagRunId"],
            DagRun.dag_run_id,
        )
        self.assertEqual(
            calls[0][1]["payload"]["deploymentChecksum"],
            "d" * 64,
        )
        self.assertTrue(calls[1][0].endswith("/runtime-specs/consume"))

    def test_builds_exec_argument_array_for_the_managed_dbt_service(self):
        command = self.factory._build_docker_command(
            self.runtime_spec(),
            container_name="dts-dbt",
            project_container_root="/opt/dbt",
            profile_container_root="/run/dts-dbt-runtime",
        )

        self.assertEqual(
            command,
            [
                "docker",
                "--host",
                "unix:///var/run/docker.sock",
                "exec",
                "--workdir",
                "/opt/dbt/.dts-scoped-runs/candidate-" + "a" * 64,
                "dts-dbt",
                "/bin/sh",
                "/opt/dbt/run-model-build.sh",
                "build",
                "20000000-0000-0000-0000-000000000002",
                "a" * 64,
                "dev",
                "model_a model_b",
                "/run/dts-dbt-runtime",
            ],
        )
        self.assertNotIn("run", command)
        self.assertNotIn("--env", command)
        self.assertNotIn("shell=True", " ".join(command))
        self.assertNotIn("password", " ".join(command).lower())

    def test_inspection_accepts_the_running_managed_dbt_service(self):
        with mock.patch.object(
            self.factory,
            "_docker_engine_request",
            return_value=self.container_inspection(),
        ) as request:
            inspected = self.factory._inspect_dbt_runtime_container("dts-dbt")

        self.assertEqual(inspected, ("a" * 64, True))
        request.assert_called_once_with("GET", "/containers/dts-dbt/json")

    def test_inspection_reports_a_stopped_managed_service(self):
        with mock.patch.object(
            self.factory,
            "_docker_engine_request",
            return_value=self.container_inspection(running=False),
        ):
            inspected = self.factory._inspect_dbt_runtime_container("dts-dbt")

        self.assertEqual(inspected, ("a" * 64, False))

    def test_cleanup_treats_a_missing_runtime_service_as_stopped(self):
        with mock.patch.object(
            self.factory,
            "_docker_engine_request",
            return_value=(404, b""),
        ) as request:
            self.factory._stop_dbt_execution(
                "dts-dbt",
                self.runtime_spec()["profileLeaseId"],
                "/opt/dbt",
            )

        request.assert_called_once_with("GET", "/containers/dts-dbt/json")

    def test_inspection_rejects_a_container_outside_the_managed_service(self):
        with mock.patch.object(
            self.factory,
            "_docker_engine_request",
            return_value=self.container_inspection(
                service_label="attacker-service"
            ),
        ) as request:
            with self.assertRaisesRegex(
                RuntimeError,
                "not the managed service",
            ):
                self.factory._inspect_dbt_runtime_container("dts-dbt")

        request.assert_called_once_with("GET", "/containers/dts-dbt/json")

    def test_inspection_rejects_a_writable_runtime_profile_mount(self):
        with mock.patch.object(
            self.factory,
            "_docker_engine_request",
            return_value=self.container_inspection(profile_rw=True),
        ):
            with self.assertRaisesRegex(
                RuntimeError,
                "profile mount is not read-only",
            ):
                self.factory._inspect_dbt_runtime_container("dts-dbt")

    def test_cleanup_invokes_the_wrapper_without_stopping_the_service(self):
        runtime = self.runtime_spec()
        with (
            mock.patch.object(
                self.factory,
                "_docker_engine_request",
                return_value=self.container_inspection(),
            ),
            mock.patch.object(self.factory.subprocess, "run") as run,
        ):
            self.factory._stop_dbt_execution(
                "dts-dbt",
                runtime["profileLeaseId"],
                "/opt/dbt",
            )

        run.assert_called_once_with(
            [
                "docker",
                "--host",
                "unix:///var/run/docker.sock",
                "exec",
                "dts-dbt",
                "/bin/sh",
                "/opt/dbt/run-model-build.sh",
                "stop",
                runtime["profileLeaseId"],
            ],
            check=True,
            timeout=25.0,
        )

    def test_rejects_path_traversal_and_non_uuid_lease(self):
        invalid_bundle = {
            **self.runtime_spec(),
            "projectBundleChecksum": "../escape",
        }
        invalid_lease = {
            **self.runtime_spec(),
            "profileLeaseId": "../escape",
        }

        with self.assertRaises(ValueError):
            self.factory._build_docker_command(
                invalid_bundle,
                "dts-dbt",
                "/opt/dbt",
                "/run/dts-dbt-runtime",
            )
        with self.assertRaises(ValueError):
            self.factory._build_docker_command(
                invalid_lease,
                "dts-dbt",
                "/opt/dbt",
                "/run/dts-dbt-runtime",
            )

    def test_runtime_ignores_legacy_image_certification_fields(self):
        legacy_runtime = {
            **self.runtime_spec(),
            "adapter": "duckdb",
            "imageRef": "attacker.example/dbt:mutable",
        }

        normalized = self.factory._validate_runtime_spec(legacy_runtime)

        self.assertNotIn("adapter", normalized)
        self.assertNotIn("imageRef", normalized)

    def test_release_and_operational_runs_share_the_managed_dbt_service(self):
        release = self.runtime_spec()
        operational = {**release, "runPurpose": "OPERATIONAL_RUN"}

        release_command = self.factory._build_docker_command(
            release,
            "dts-dbt",
            "/opt/dbt",
            "/run/dts-dbt-runtime",
        )
        operational_command = self.factory._build_docker_command(
            operational,
            "dts-dbt",
            "/opt/dbt",
            "/run/dts-dbt-runtime",
        )

        self.assertEqual(release_command, operational_command)
        self.assertEqual(release_command[6], "dts-dbt")

    def test_short_build_does_not_send_an_unnecessary_lease_renewal(self):
        runtime = self.runtime_spec()
        task_instance = mock.Mock()
        task_instance.xcom_pull.return_value = runtime
        command = ["docker", "exec", "dts-dbt", "build"]
        process = FakeProcess([0])
        paths = []

        with (
            mock.patch.object(
                self.factory,
                "_platform_request",
                side_effect=lambda path, **_kwargs: (
                    paths.append(path) or self.lease_response()
                ),
            ),
            mock.patch.object(
                self.factory,
                "_build_docker_command",
                return_value=command,
            ) as build,
            mock.patch.object(
                self.factory.subprocess,
                "Popen",
                return_value=process,
            ),
            mock.patch.object(self.factory.subprocess, "run") as legacy_run,
        ):
            self.factory._dbt_build_task(ti=task_instance)

        self.assertEqual(
            paths,
            [
                "/api/internal/modeling/materialization/profile-leases/"
                f"{runtime['profileLeaseId']}/consume"
            ],
        )
        self.assertEqual(process.wait_timeouts, [60.0])
        self.assertEqual(build.call_args.kwargs["container_name"], "dts-dbt")
        self.assertEqual(
            build.call_args.kwargs["profile_container_root"],
            "/run/dts-dbt-runtime",
        )
        legacy_run.assert_not_called()

    def test_long_build_renews_profile_lease_more_than_once(self):
        runtime = self.runtime_spec()
        task_instance = mock.Mock()
        task_instance.xcom_pull.return_value = runtime
        command = ["docker", "exec", "dts-dbt", "build"]
        timeout = self.factory.subprocess.TimeoutExpired(command, 60.0)
        process = FakeProcess([timeout, timeout, 0])
        paths = []

        with (
            mock.patch.object(
                self.factory,
                "_platform_request",
                side_effect=lambda path, **_kwargs: (
                    paths.append(path) or self.lease_response()
                ),
            ),
            mock.patch.object(
                self.factory,
                "_build_docker_command",
                return_value=command,
            ),
            mock.patch.object(
                self.factory.subprocess,
                "Popen",
                return_value=process,
            ) as popen,
            mock.patch.object(self.factory.subprocess, "run") as legacy_run,
        ):
            self.factory._dbt_build_task(ti=task_instance)

        popen.assert_called_once_with(command)
        self.assertEqual(
            paths,
            [
                "/api/internal/modeling/materialization/profile-leases/"
                f"{runtime['profileLeaseId']}/consume",
                "/api/internal/modeling/materialization/profile-leases/"
                f"{runtime['profileLeaseId']}/renew",
                "/api/internal/modeling/materialization/profile-leases/"
                f"{runtime['profileLeaseId']}/renew",
            ],
        )
        self.assertEqual(process.wait_timeouts, [60.0, 60.0, 60.0])
        self.assertEqual(process.terminate_calls, 0)
        legacy_run.assert_not_called()

    def test_profile_lease_renew_interval_has_lower_and_upper_bounds(self):
        now = datetime(2026, 7, 30, 12, 0, tzinfo=timezone.utc)

        self.assertEqual(
            self.factory._profile_lease_renew_interval(
                (now + timedelta(seconds=12)).isoformat(),
                now,
            ),
            5.0,
        )
        self.assertEqual(
            self.factory._profile_lease_renew_interval(
                (now + timedelta(hours=1)).isoformat(),
                now,
            ),
            60.0,
        )

    def test_profile_lease_renew_interval_rejects_invalid_or_unsafe_expiry(self):
        now = datetime(2026, 7, 30, 12, 0, tzinfo=timezone.utc)

        for expires_at in (
            "not-a-date",
            "2026-07-30T12:01:00",
            (now + timedelta(seconds=10)).isoformat(),
        ):
            with self.subTest(expires_at=expires_at):
                with self.assertRaisesRegex(
                    RuntimeError,
                    "expiresAt is invalid|expires too soon",
                ):
                    self.factory._profile_lease_renew_interval(
                        expires_at,
                        now,
                    )

    def test_renew_response_recalculates_the_next_wait_interval(self):
        runtime = self.runtime_spec()
        task_instance = mock.Mock()
        task_instance.xcom_pull.return_value = runtime
        command = ["docker", "exec", "dts-dbt", "build"]
        process = FakeProcess(
            [
                self.factory.subprocess.TimeoutExpired(command, 60.0),
                0,
            ]
        )

        with (
            mock.patch.object(
                self.factory,
                "_platform_request",
                return_value=self.lease_response(),
            ),
            mock.patch.object(
                self.factory,
                "_validated_profile_lease_renew_interval",
                side_effect=[60.0, 5.0],
            ),
            mock.patch.object(
                self.factory,
                "_build_docker_command",
                return_value=command,
            ),
            mock.patch.object(
                self.factory.subprocess,
                "Popen",
                return_value=process,
            ),
        ):
            self.factory._dbt_build_task(ti=task_instance)

        self.assertEqual(process.wait_timeouts, [60.0, 5.0])

    def test_build_fails_closed_when_profile_lease_consume_errors(self):
        task_instance = mock.Mock()
        task_instance.xcom_pull.return_value = self.runtime_spec()

        with (
            mock.patch.object(
                self.factory,
                "_platform_request",
                side_effect=RuntimeError("profile lease consume rejected"),
            ),
            mock.patch.object(self.factory, "_build_docker_command") as build,
            mock.patch.object(self.factory.subprocess, "Popen") as popen,
        ):
            with self.assertRaisesRegex(RuntimeError, "consume rejected"):
                self.factory._dbt_build_task(ti=task_instance)

        build.assert_not_called()
        popen.assert_not_called()

    def test_build_terminates_and_waits_when_profile_lease_renewal_errors(self):
        runtime = self.runtime_spec()
        task_instance = mock.Mock()
        task_instance.xcom_pull.return_value = runtime
        command = ["docker", "exec", "dts-dbt", "build"]
        process = FakeProcess(
            [
                self.factory.subprocess.TimeoutExpired(command, 60.0),
                0,
            ]
        )
        calls = 0

        def request(_path, **_kwargs):
            nonlocal calls
            calls += 1
            if calls == 1:
                return self.lease_response()
            raise RuntimeError("profile lease renewal rejected")

        with (
            mock.patch.object(
                self.factory,
                "_platform_request",
                side_effect=request,
            ),
            mock.patch.object(
                self.factory,
                "_build_docker_command",
                return_value=command,
            ),
            mock.patch.object(
                self.factory.subprocess,
                "Popen",
                return_value=process,
            ),
            mock.patch.object(
                self.factory,
                "_stop_dbt_execution",
            ) as stop_container,
        ):
            with self.assertRaisesRegex(RuntimeError, "renewal rejected"):
                self.factory._dbt_build_task(ti=task_instance)

        stop_container.assert_called_once_with(
            "dts-dbt",
            runtime["profileLeaseId"],
            "/opt/dbt",
        )
        self.assertEqual(process.terminate_calls, 1)
        self.assertEqual(
            process.wait_timeouts,
            [
                60.0,
                self.factory._PROCESS_TERMINATE_TIMEOUT_SECONDS,
            ],
        )

    def test_build_terminates_and_waits_when_renewed_lease_id_mismatches(self):
        runtime = self.runtime_spec()
        task_instance = mock.Mock()
        task_instance.xcom_pull.return_value = runtime
        command = ["docker", "exec", "dts-dbt", "build"]
        process = FakeProcess(
            [
                self.factory.subprocess.TimeoutExpired(command, 60.0),
                0,
            ]
        )
        responses = iter(
            [
                self.lease_response(),
                self.lease_response(
                    lease_id="20000000-0000-0000-0000-000000000099"
                ),
            ]
        )

        with (
            mock.patch.object(
                self.factory,
                "_platform_request",
                side_effect=lambda _path, **_kwargs: next(responses),
            ),
            mock.patch.object(
                self.factory,
                "_build_docker_command",
                return_value=command,
            ),
            mock.patch.object(
                self.factory.subprocess,
                "Popen",
                return_value=process,
            ),
            mock.patch.object(
                self.factory,
                "_stop_dbt_execution",
            ) as stop_container,
        ):
            with self.assertRaisesRegex(
                RuntimeError,
                "renew response does not match runtime",
            ):
                self.factory._dbt_build_task(ti=task_instance)

        stop_container.assert_called_once_with(
            "dts-dbt",
            runtime["profileLeaseId"],
            "/opt/dbt",
        )
        self.assertEqual(process.terminate_calls, 1)
        self.assertEqual(
            process.wait_timeouts,
            [
                60.0,
                self.factory._PROCESS_TERMINATE_TIMEOUT_SECONDS,
            ],
        )

    def test_renewal_error_kills_process_when_terminate_does_not_finish(self):
        runtime = self.runtime_spec()
        task_instance = mock.Mock()
        task_instance.xcom_pull.return_value = runtime
        command = ["docker", "exec", "dts-dbt", "build"]
        terminate_timeout = self.factory.subprocess.TimeoutExpired(
            command,
            self.factory._PROCESS_TERMINATE_TIMEOUT_SECONDS,
        )
        process = FakeProcess(
            [
                self.factory.subprocess.TimeoutExpired(command, 60.0),
                terminate_timeout,
                0,
            ]
        )
        responses = iter(
            [
                self.lease_response(),
                RuntimeError("profile lease renewal rejected"),
            ]
        )

        def request(_path, **_kwargs):
            response = next(responses)
            if isinstance(response, BaseException):
                raise response
            return response

        with (
            mock.patch.object(
                self.factory,
                "_platform_request",
                side_effect=request,
            ),
            mock.patch.object(
                self.factory,
                "_build_docker_command",
                return_value=command,
            ),
            mock.patch.object(
                self.factory.subprocess,
                "Popen",
                return_value=process,
            ),
            mock.patch.object(
                self.factory,
                "_stop_dbt_execution",
            ) as stop_container,
        ):
            with self.assertRaisesRegex(RuntimeError, "renewal rejected"):
                self.factory._dbt_build_task(ti=task_instance)

        stop_container.assert_called_once_with(
            "dts-dbt",
            runtime["profileLeaseId"],
            "/opt/dbt",
        )
        self.assertEqual(process.terminate_calls, 1)
        self.assertEqual(process.kill_calls, 1)
        self.assertEqual(
            process.wait_timeouts,
            [
                60.0,
                self.factory._PROCESS_TERMINATE_TIMEOUT_SECONDS,
                self.factory._PROCESS_KILL_TIMEOUT_SECONDS,
            ],
        )

    def test_cleanup_failure_does_not_mask_profile_lease_renewal_error(self):
        runtime = self.runtime_spec()
        task_instance = mock.Mock()
        task_instance.xcom_pull.return_value = runtime
        command = ["docker", "exec", "dts-dbt", "build"]
        process = FakeProcess(
            [
                self.factory.subprocess.TimeoutExpired(command, 60.0),
                OSError("process wait failed"),
                OSError("process kill wait failed"),
            ]
        )
        responses = iter(
            [
                self.lease_response(),
                RuntimeError("profile lease renewal rejected"),
            ]
        )

        def request(_path, **_kwargs):
            response = next(responses)
            if isinstance(response, BaseException):
                raise response
            return response

        with (
            mock.patch.object(
                self.factory,
                "_platform_request",
                side_effect=request,
            ),
            mock.patch.object(
                self.factory,
                "_build_docker_command",
                return_value=command,
            ),
            mock.patch.object(
                self.factory.subprocess,
                "Popen",
                return_value=process,
            ),
            mock.patch.object(
                self.factory,
                "_stop_dbt_execution",
                side_effect=OSError("docker engine unavailable"),
            ),
        ):
            with self.assertRaisesRegex(RuntimeError, "renewal rejected"):
                self.factory._dbt_build_task(ti=task_instance)

        self.assertEqual(process.terminate_calls, 1)
        self.assertEqual(process.kill_calls, 1)
        self.assertEqual(
            process.wait_timeouts,
            [
                60.0,
                self.factory._PROCESS_TERMINATE_TIMEOUT_SECONDS,
                self.factory._PROCESS_KILL_TIMEOUT_SECONDS,
            ],
        )

    def test_nonzero_build_exit_preserves_code_without_exposing_command(self):
        runtime = self.runtime_spec()
        task_instance = mock.Mock()
        task_instance.xcom_pull.return_value = runtime
        command = ["docker", "exec", "dts-dbt", "build"]
        process = FakeProcess([7])

        with (
            mock.patch.object(
                self.factory,
                "_platform_request",
                return_value=self.lease_response(),
            ),
            mock.patch.object(
                self.factory,
                "_build_docker_command",
                return_value=command,
            ),
            mock.patch.object(
                self.factory.subprocess,
                "Popen",
                return_value=process,
            ),
            mock.patch.object(
                self.factory,
                "_stop_dbt_execution",
            ) as stop_container,
        ):
            with self.assertRaises(
                self.factory.DbtBuildProcessError
            ) as raised:
                self.factory._dbt_build_task(ti=task_instance)

        stop_container.assert_called_once_with(
            "dts-dbt",
            runtime["profileLeaseId"],
            "/opt/dbt",
        )
        self.assertEqual(raised.exception.returncode, 7)
        self.assertNotIn("model_a model_b", str(raised.exception))
        self.assertNotIn("/dev/shm", str(raised.exception))
        self.assertEqual(process.terminate_calls, 0)
        self.assertEqual(process.kill_calls, 0)

    def test_popen_failure_still_cleans_the_managed_execution(self):
        runtime = self.runtime_spec()
        task_instance = mock.Mock()
        task_instance.xcom_pull.return_value = runtime
        command = ["docker", "exec", "dts-dbt", "build"]

        with (
            mock.patch.object(
                self.factory,
                "_platform_request",
                return_value=self.lease_response(),
            ),
            mock.patch.object(
                self.factory,
                "_build_docker_command",
                return_value=command,
            ),
            mock.patch.object(
                self.factory.subprocess,
                "Popen",
                side_effect=OSError("process acquisition failed"),
            ),
            mock.patch.object(
                self.factory,
                "_stop_dbt_execution",
            ) as stop_container,
        ):
            with self.assertRaisesRegex(
                OSError,
                "process acquisition failed",
            ):
                self.factory._dbt_build_task(ti=task_instance)

        stop_container.assert_called_once_with(
            "dts-dbt",
            runtime["profileLeaseId"],
            "/opt/dbt",
        )

    def test_build_fails_closed_when_consumed_profile_lease_id_mismatches(self):
        runtime = self.runtime_spec()
        mismatched_lease_id = "20000000-0000-0000-0000-000000000099"
        task_instance = mock.Mock()
        task_instance.xcom_pull.return_value = runtime

        with (
            mock.patch.object(
                self.factory,
                "_platform_request",
                return_value={"profileLeaseId": mismatched_lease_id},
            ),
            mock.patch.object(self.factory, "_build_docker_command") as build,
            mock.patch.object(self.factory.subprocess, "Popen") as popen,
        ):
            with self.assertRaisesRegex(
                RuntimeError,
                "consume response does not match runtime",
            ):
                self.factory._dbt_build_task(ti=task_instance)

        build.assert_not_called()
        popen.assert_not_called()

    def test_finalizer_does_not_delete_lease_when_cleanup_is_unconfirmed(self):
        runtime = self.runtime_spec()
        task_instance = mock.Mock()
        task_instance.xcom_pull.return_value = runtime
        dag_run = mock.Mock()
        dag_run.get_task_instances.return_value = [
            SimpleNamespace(task_id=task_id, state="success")
            for task_id in (
                self.factory._PREPARE_TASK_ID,
                self.factory._BUILD_TASK_ID,
                self.factory._SYNC_TASK_ID,
            )
        ]
        requests = []

        with (
            mock.patch.object(
                self.factory,
                "_platform_request",
                side_effect=lambda path, **kwargs: (
                    requests.append((path, kwargs)) or {}
                ),
            ),
            mock.patch.object(
                self.factory,
                "_stop_dbt_execution",
                side_effect=RuntimeError("cleanup unconfirmed"),
            ),
        ):
            with self.assertRaisesRegex(RuntimeError, "cleanup unconfirmed"):
                self.factory._finalize_task(
                    ti=task_instance,
                    dag_run=dag_run,
                )

        self.assertFalse(
            any(kwargs.get("method") == "DELETE" for _, kwargs in requests)
        )

    def test_finalizer_fails_run_when_prepare_runtime_has_no_xcom(self):
        runtime = self.runtime_spec()
        task_instance = mock.Mock()
        task_instance.xcom_pull.return_value = None
        dag_run = mock.Mock()
        dag_run.conf = {
            "pipelineRunGroupId": runtime["pipelineRunGroupId"],
            "candidateId": "30000000-0000-0000-0000-000000000003",
            "candidateVersion": 3,
            "attempt": 1,
            "runPurpose": "RELEASE_BUILD",
            "runtimeSpecToken": "opaque",
            "bundleChecksum": "c" * 64,
        }
        dag_run.get_task_instances.return_value = [
            SimpleNamespace(
                task_id=self.factory._PREPARE_TASK_ID,
                state="failed",
            )
        ]

        with (
            mock.patch.object(
                self.factory,
                "_platform_request",
                return_value={},
            ) as platform_request,
            mock.patch.object(
                self.factory,
                "_stop_dbt_execution",
            ) as stop_container,
        ):
            with self.assertRaisesRegex(
                RuntimeError,
                "materialization upstream task failed",
            ):
                self.factory._finalize_task(
                    ti=task_instance,
                    dag_run=dag_run,
                )

        platform_request.assert_called_once_with(
            "/api/internal/modeling/materialization/run-groups/"
            + runtime["pipelineRunGroupId"]
            + "/finalize",
            payload={"outcome": "FAILED"},
        )
        stop_container.assert_not_called()

    def test_finalizer_deletes_lease_only_after_cleanup_is_confirmed(self):
        runtime = self.runtime_spec()
        task_instance = mock.Mock()
        task_instance.xcom_pull.return_value = runtime
        dag_run = mock.Mock()
        dag_run.get_task_instances.return_value = [
            SimpleNamespace(task_id=task_id, state="success")
            for task_id in (
                self.factory._PREPARE_TASK_ID,
                self.factory._BUILD_TASK_ID,
                self.factory._SYNC_TASK_ID,
            )
        ]
        events = []

        with (
            mock.patch.object(
                self.factory,
                "_platform_request",
                side_effect=lambda _path, **kwargs: (
                    events.append(
                        "delete"
                        if kwargs.get("method") == "DELETE"
                        else "finalize"
                    )
                    or {}
                ),
            ),
            mock.patch.object(
                self.factory,
                "_stop_dbt_execution",
                side_effect=lambda *_args: events.append("cleanup"),
            ) as stop_container,
        ):
            self.factory._finalize_task(
                ti=task_instance,
                dag_run=dag_run,
            )

        stop_container.assert_called_once_with(
            "dts-dbt",
            runtime["profileLeaseId"],
            "/opt/dbt",
        )
        self.assertEqual(events, ["finalize", "cleanup", "delete"])

    def test_platform_request_errors_do_not_expose_dynamic_lease_path(self):
        lease_id = self.runtime_spec()["profileLeaseId"]
        path = (
            "/api/internal/modeling/materialization/profile-leases/"
            f"{lease_id}/renew"
        )
        full_url = "http://dts-platform:8080" + path

        with (
            mock.patch.dict(
                os.environ,
                {
                    "DTS_PLATFORM_INTERNAL_BASE_URL":
                        "http://dts-platform:8080",
                    "DTS_AIRFLOW_TO_PLATFORM_TOKEN": "opaque",
                },
            ),
            mock.patch.object(
                self.factory.urllib_request,
                "urlopen",
                side_effect=self.factory.urllib_error.HTTPError(
                    full_url,
                    409,
                    "Conflict",
                    None,
                    None,
                ),
            ),
        ):
            with self.assertRaises(RuntimeError) as raised:
                self.factory._platform_request(path)

        message = str(raised.exception)
        self.assertIn("HTTP 409", message)
        self.assertNotIn(path, message)
        self.assertNotIn(lease_id, message)

    def test_factory_source_has_one_build_process_and_controlled_stop(self):
        source = FACTORY_PATH.read_text(encoding="utf-8")

        self.assertIn("subprocess.Popen(command)", source)
        self.assertIn("is_paused_upon_creation=False", source)
        self.assertIn("subprocess.run(", source)
        self.assertNotIn("shell=True", source)
        self.assertNotIn("|| true", source)
        self.assertNotIn("BashOperator", source)
        self.assertEqual(
            source.count(
                '"docker", "--host", _DOCKER_ENGINE_HOST, "exec"'
            ),
            2,
        )
        self.assertNotIn('"run", "--rm"', source)

    def test_managed_wrapper_fixes_runtime_paths_and_serializes_builds(self):
        source = MANAGED_DBT_WRAPPER_PATH.read_text(encoding="utf-8")

        self.assertIn("PROJECT_ROOT=/opt/dbt", source)
        self.assertIn("PROFILE_ROOT=/run/dts-dbt-runtime", source)
        self.assertIn("verify-dbt-runtime", source)
        self.assertIn("setsid flock -x", source)
        self.assertIn("dbt build", source)
        self.assertIn("build) build_run", source)
        self.assertIn("stop) stop_run", source)
        self.assertNotIn("docker run", source)


if __name__ == "__main__":
    unittest.main()
