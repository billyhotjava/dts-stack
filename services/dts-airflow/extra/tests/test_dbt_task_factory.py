from datetime import datetime, timedelta, timezone
import importlib.util
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
    @classmethod
    def setUpClass(cls):
        cls.factory = load_factory()

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
            "runtimeProfileId": (
                "H83-CERT-RT01-LINUX-AMD64-EVIDENCE-"
                "bcd2fc84b05b9508990538c6642be7ac7b35073ec034e6b1980b22f556345a68"
            ),
            "candidateProfileId": (
                "H83-RT01-LINUX-AMD64-DBT11022-PG1100-LOCK-"
                "01d7c02b6bf4fefdfc188cbf9ef8aed4fb243c227c060103f195c4ca45af5f02"
            ),
            "dbtCoreVersion": "1.10.22",
            "dbtPostgresVersion": "1.10.0",
            "adapter": "postgres",
            "databaseType": "PostgreSQL",
            "requirementsLockSha256": (
                "01d7c02b6bf4fefdfc188cbf9ef8aed4fb243c227c060103f195c4ca45af5f02"
            ),
            "candidateImageDigest": (
                "sha256:fe1d15f1b4215e693dadfc1d99be2ae07c7e50e8df144504005feb41cc7686b7"
            ),
            "imageRef": (
                "registry.example/dts-dbt@sha256:"
                "2f6dddb7237fdb7141f452b6d09da0379ef7569f2f82560473f304b265cbbd85"
            ),
            "evidenceManifestSha256": (
                "bcd2fc84b05b9508990538c6642be7ac7b35073ec034e6b1980b22f556345a68"
            ),
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
        lease_id=None,
        run_group_id=None,
        running=True,
    ):
        runtime = self.runtime_spec()
        return (
            200,
            json.dumps(
                {
                    "Id": container_id or "a" * 64,
                    "Config": {
                        "Labels": {
                            "com.yuzhi.dts.dbt.profile-lease-id":
                                lease_id or runtime["profileLeaseId"],
                            "com.yuzhi.dts.dbt.pipeline-run-group-id":
                                run_group_id
                                or runtime["pipelineRunGroupId"],
                        }
                    },
                    "State": {"Running": running},
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

    def test_builds_argument_array_from_fixed_roots_and_opaque_ids(self):
        command = self.factory._build_docker_command(
            self.runtime_spec(),
            image=self.runtime_spec()["imageRef"],
            project_host_root="/srv/dts/services/dts-dbt",
            profile_host_root="/dev/shm/dts-dbt-runtime",
            docker_network="dts-core",
        )

        self.assertIsInstance(command, list)
        self.assertEqual(
            command[:5],
            [
                "docker",
                "--host",
                "unix:///var/run/docker.sock",
                "run",
                "--rm",
            ],
        )
        self.assertIn("--name", command)
        self.assertIn(
            "dts-dbt-20000000-0000-0000-0000-000000000002",
            command,
        )
        self.assertIn(
            "com.yuzhi.dts.dbt.profile-lease-id="
            "20000000-0000-0000-0000-000000000002",
            command,
        )
        self.assertIn(
            "com.yuzhi.dts.dbt.pipeline-run-group-id="
            "10000000-0000-0000-0000-000000000001",
            command,
        )
        self.assertIn(
            "/srv/dts/services/dts-dbt/.dts-scoped-runs/"
            "candidate-" + "a" * 64 + ":/opt/dbt",
            command,
        )
        self.assertIn(
            "/dev/shm/dts-dbt-runtime/"
            "20000000-0000-0000-0000-000000000002:/root/.dbt:ro",
            command,
        )
        self.assertIn("--select", command)
        self.assertIn("model_a model_b", command)
        self.assertNotIn("shell=True", " ".join(command))
        self.assertNotIn("password", " ".join(command).lower())

    def test_daemon_cleanup_kills_container_when_graceful_stop_is_insufficient(
        self,
    ):
        container_name = (
            "dts-dbt-20000000-0000-0000-0000-000000000002"
        )
        container_id = "a" * 64
        responses = iter(
            [
                self.container_inspection(),
                (500, b""),
                self.container_inspection(),
                (204, b""),
                self.container_inspection(running=False),
            ]
        )

        with mock.patch.object(
            self.factory,
            "_docker_engine_request",
            side_effect=lambda *_args: next(responses),
        ) as request:
            self.factory._stop_dbt_container(
                container_name,
                self.runtime_spec()["profileLeaseId"],
                self.runtime_spec()["pipelineRunGroupId"],
            )

        self.assertEqual(
            [call.args[:2] for call in request.call_args_list],
            [
                ("GET", f"/containers/{container_name}/json"),
                (
                    "POST",
                    "/containers/"
                    f"{container_id}/stop?t=10",
                ),
                ("GET", f"/containers/{container_id}/json"),
                ("POST", f"/containers/{container_id}/kill"),
                ("GET", f"/containers/{container_id}/json"),
            ],
        )

    def test_daemon_cleanup_fails_when_container_is_still_running(self):
        container_name = (
            "dts-dbt-20000000-0000-0000-0000-000000000002"
        )
        responses = iter(
            [
                self.container_inspection(),
                (500, b""),
                self.container_inspection(),
                (500, b""),
                self.container_inspection(),
            ]
        )

        with mock.patch.object(
            self.factory,
            "_docker_engine_request",
            side_effect=lambda *_args: next(responses),
        ):
            with self.assertRaisesRegex(
                RuntimeError,
                "could not be confirmed stopped",
            ):
                self.factory._stop_dbt_container(
                    container_name,
                    self.runtime_spec()["profileLeaseId"],
                    self.runtime_spec()["pipelineRunGroupId"],
                )

    def test_daemon_cleanup_treats_missing_container_as_stopped(self):
        container_name = (
            "dts-dbt-20000000-0000-0000-0000-000000000002"
        )

        with mock.patch.object(
            self.factory,
            "_docker_engine_request",
            return_value=(404, b""),
        ) as request:
            self.factory._stop_dbt_container(
                container_name,
                self.runtime_spec()["profileLeaseId"],
                self.runtime_spec()["pipelineRunGroupId"],
            )

        self.assertEqual(request.call_count, 1)
        self.assertEqual(
            request.call_args_list[-1].args[:2],
            ("GET", f"/containers/{container_name}/json"),
        )

    def test_daemon_cleanup_refuses_a_container_owned_by_another_run(self):
        runtime = self.runtime_spec()
        container_name = "dts-dbt-" + runtime["profileLeaseId"]

        with mock.patch.object(
            self.factory,
            "_docker_engine_request",
            return_value=self.container_inspection(
                run_group_id="10000000-0000-0000-0000-000000000099"
            ),
        ) as request:
            with self.assertRaisesRegex(
                RuntimeError,
                "ownership does not match runtime",
            ):
                self.factory._stop_dbt_container(
                    container_name,
                    runtime["profileLeaseId"],
                    runtime["pipelineRunGroupId"],
                )

        self.assertEqual(
            [call.args[0] for call in request.call_args_list],
            ["GET"],
        )

    def test_daemon_cleanup_refuses_kill_after_owner_changes(self):
        runtime = self.runtime_spec()
        container_name = "dts-dbt-" + runtime["profileLeaseId"]
        container_id = "a" * 64
        responses = iter(
            [
                self.container_inspection(),
                (500, b""),
                self.container_inspection(
                    run_group_id="10000000-0000-0000-0000-000000000099"
                ),
            ]
        )

        with mock.patch.object(
            self.factory,
            "_docker_engine_request",
            side_effect=lambda *_args: next(responses),
        ) as request:
            with self.assertRaisesRegex(
                RuntimeError,
                "ownership does not match runtime",
            ):
                self.factory._stop_dbt_container(
                    container_name,
                    runtime["profileLeaseId"],
                    runtime["pipelineRunGroupId"],
                )

        self.assertEqual(
            [call.args[:2] for call in request.call_args_list],
            [
                ("GET", f"/containers/{container_name}/json"),
                (
                    "POST",
                    f"/containers/{container_id}/stop?t=10",
                ),
                ("GET", f"/containers/{container_id}/json"),
            ],
        )
        self.assertNotIn(
            f"/containers/{container_name}/kill",
            [call.args[1] for call in request.call_args_list],
        )

    def test_daemon_cleanup_refuses_kill_after_container_identity_changes(
        self,
    ):
        runtime = self.runtime_spec()
        container_name = "dts-dbt-" + runtime["profileLeaseId"]
        container_id = "a" * 64
        replacement_id = "b" * 64
        responses = iter(
            [
                self.container_inspection(container_id=container_id),
                (500, b""),
                self.container_inspection(container_id=replacement_id),
            ]
        )

        with mock.patch.object(
            self.factory,
            "_docker_engine_request",
            side_effect=lambda *_args: next(responses),
        ) as request:
            with self.assertRaisesRegex(
                RuntimeError,
                "identity does not match",
            ):
                self.factory._stop_dbt_container(
                    container_name,
                    runtime["profileLeaseId"],
                    runtime["pipelineRunGroupId"],
                )

        self.assertEqual(
            [call.args[:2] for call in request.call_args_list],
            [
                ("GET", f"/containers/{container_name}/json"),
                (
                    "POST",
                    f"/containers/{container_id}/stop?t=10",
                ),
                ("GET", f"/containers/{container_id}/json"),
            ],
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
                self.runtime_spec()["imageRef"],
                "/srv/dts/services/dts-dbt",
                "/dev/shm/dts-dbt-runtime",
                "dts-core",
            )
        with self.assertRaises(ValueError):
            self.factory._build_docker_command(
                invalid_lease,
                self.runtime_spec()["imageRef"],
                "/srv/dts/services/dts-dbt",
                "/dev/shm/dts-dbt-runtime",
                "dts-core",
            )

    def test_release_runtime_rejects_certification_or_digest_drift(self):
        wrong_adapter = {**self.runtime_spec(), "adapter": "duckdb"}
        wrong_digest = {
            **self.runtime_spec(),
            "imageRef": "registry.example/dts-dbt@sha256:" + "f" * 64,
        }
        missing_profile = dict(self.runtime_spec())
        missing_profile.pop("runtimeProfileId")

        for runtime in (wrong_adapter, wrong_digest, missing_profile):
            with self.subTest(runtime=runtime):
                with self.assertRaises(ValueError):
                    self.factory._validate_runtime_spec(runtime)

    def test_release_runtime_rejects_revoked_r1_derivative(self):
        revoked = {
            **self.runtime_spec(),
            "imageRef": (
                "registry.example/dts-dbt@sha256:"
                "423926d8ce77a9bdce23db23501910843e9c7b17476b1c025320a3098e2d33f8"
            ),
        }

        with self.assertRaisesRegex(ValueError, "not certified"):
            self.factory._validate_runtime_spec(revoked)

    def test_operational_runtime_does_not_require_release_certification(self):
        runtime = {
            key: value
            for key, value in self.runtime_spec().items()
            if key not in self.factory._RUNTIME_CERTIFICATION_KEYS
        }
        runtime["runPurpose"] = "OPERATIONAL_RUN"

        normalized = self.factory._validate_runtime_spec(runtime)

        self.assertEqual(normalized["runPurpose"], "OPERATIONAL_RUN")
        self.assertNotIn("imageRef", normalized)

    def test_operational_build_uses_platform_certified_image(self):
        runtime = {
            key: value
            for key, value in self.runtime_spec().items()
            if key not in self.factory._RUNTIME_CERTIFICATION_KEYS
        }
        runtime["runPurpose"] = "OPERATIONAL_RUN"
        task_instance = mock.Mock()
        task_instance.xcom_pull.return_value = runtime
        command = ["docker", "run", "certified-image", "build"]
        process = FakeProcess([0])

        with (
            mock.patch.dict(
                os.environ,
                {
                    "DBT_IMAGE": "attacker.example/dbt:mutable",
                    "DTS_DBT_RUNTIME_CERTIFICATION_IMAGE_REF": (
                        "registry.example/dts-dbt@sha256:"
                        "2f6dddb7237fdb7141f452b6d09da0379ef7569f2f82560473f304b265cbbd85"
                    ),
                },
                clear=False,
            ),
            mock.patch.object(
                self.factory,
                "_platform_request",
                return_value=self.lease_response(),
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
        ):
            self.factory._dbt_build_task(ti=task_instance)

        self.assertEqual(
            build.call_args.kwargs["image"],
            (
                "registry.example/dts-dbt@sha256:"
                "2f6dddb7237fdb7141f452b6d09da0379ef7569f2f82560473f304b265cbbd85"
            ),
        )

    def test_operational_image_rejects_mutable_or_revoked_runtime(self):
        for image_ref in (
            "dts-dbt:1.10.0",
            (
                "registry.example/dts-dbt@sha256:"
                "423926d8ce77a9bdce23db23501910843e9c7b17476b1c025320a3098e2d33f8"
            ),
        ):
            with self.subTest(image_ref=image_ref):
                with (
                    mock.patch.dict(
                        os.environ,
                        {
                            "DTS_DBT_RUNTIME_CERTIFICATION_IMAGE_REF": image_ref,
                        },
                        clear=False,
                    ),
                    self.assertRaisesRegex(ValueError, "not certified"),
                ):
                    self.factory._operational_image_ref()

    def test_short_build_does_not_send_an_unnecessary_lease_renewal(self):
        runtime = self.runtime_spec()
        task_instance = mock.Mock()
        task_instance.xcom_pull.return_value = runtime
        command = ["docker", "run", "dts-dbt:1.10.0", "build"]
        process = FakeProcess([0])
        paths = []

        with (
            mock.patch.dict(
                os.environ,
                {"DBT_IMAGE": "attacker.example/dbt:mutable"},
            ),
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
        self.assertEqual(
            build.call_args.kwargs["image"],
            runtime["imageRef"],
        )
        legacy_run.assert_not_called()

    def test_long_build_renews_profile_lease_more_than_once(self):
        runtime = self.runtime_spec()
        task_instance = mock.Mock()
        task_instance.xcom_pull.return_value = runtime
        command = ["docker", "run", "dts-dbt:1.10.0", "build"]
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
        command = ["docker", "run", "dts-dbt:1.10.0", "build"]
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
        command = ["docker", "run", "dts-dbt:1.10.0", "build"]
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
                "_stop_dbt_container",
            ) as stop_container,
        ):
            with self.assertRaisesRegex(RuntimeError, "renewal rejected"):
                self.factory._dbt_build_task(ti=task_instance)

        stop_container.assert_called_once_with(
            "dts-dbt-" + runtime["profileLeaseId"],
            runtime["profileLeaseId"],
            runtime["pipelineRunGroupId"],
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
        command = ["docker", "run", "dts-dbt:1.10.0", "build"]
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
                "_stop_dbt_container",
            ) as stop_container,
        ):
            with self.assertRaisesRegex(
                RuntimeError,
                "renew response does not match runtime",
            ):
                self.factory._dbt_build_task(ti=task_instance)

        stop_container.assert_called_once_with(
            "dts-dbt-" + runtime["profileLeaseId"],
            runtime["profileLeaseId"],
            runtime["pipelineRunGroupId"],
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
        command = ["docker", "run", "dts-dbt:1.10.0", "build"]
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
                "_stop_dbt_container",
            ) as stop_container,
        ):
            with self.assertRaisesRegex(RuntimeError, "renewal rejected"):
                self.factory._dbt_build_task(ti=task_instance)

        stop_container.assert_called_once_with(
            "dts-dbt-" + runtime["profileLeaseId"],
            runtime["profileLeaseId"],
            runtime["pipelineRunGroupId"],
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
        command = ["docker", "run", "dts-dbt:1.10.0", "build"]
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
                "_stop_dbt_container",
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
        command = ["docker", "run", "dts-dbt:1.10.0", "build"]
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
                "_stop_dbt_container",
            ) as stop_container,
        ):
            with self.assertRaises(
                self.factory.DbtBuildProcessError
            ) as raised:
                self.factory._dbt_build_task(ti=task_instance)

        stop_container.assert_called_once_with(
            "dts-dbt-" + runtime["profileLeaseId"],
            runtime["profileLeaseId"],
            runtime["pipelineRunGroupId"],
        )
        self.assertEqual(raised.exception.returncode, 7)
        self.assertNotIn("model_a model_b", str(raised.exception))
        self.assertNotIn("/dev/shm", str(raised.exception))
        self.assertEqual(process.terminate_calls, 0)
        self.assertEqual(process.kill_calls, 0)

    def test_popen_failure_still_cleans_the_deterministic_container(self):
        runtime = self.runtime_spec()
        task_instance = mock.Mock()
        task_instance.xcom_pull.return_value = runtime
        command = ["docker", "run", "dts-dbt:1.10.0", "build"]

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
                "_stop_dbt_container",
            ) as stop_container,
        ):
            with self.assertRaisesRegex(
                OSError,
                "process acquisition failed",
            ):
                self.factory._dbt_build_task(ti=task_instance)

        stop_container.assert_called_once_with(
            "dts-dbt-" + runtime["profileLeaseId"],
            runtime["profileLeaseId"],
            runtime["pipelineRunGroupId"],
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
                "_stop_dbt_container",
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
                "_stop_dbt_container",
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
                "_stop_dbt_container",
                side_effect=lambda *_args: events.append("cleanup"),
            ) as stop_container,
        ):
            self.factory._finalize_task(
                ti=task_instance,
                dag_run=dag_run,
            )

        stop_container.assert_called_once_with(
            "dts-dbt-" + runtime["profileLeaseId"],
            runtime["profileLeaseId"],
            runtime["pipelineRunGroupId"],
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

    def test_factory_source_has_one_subprocess_owner(self):
        source = FACTORY_PATH.read_text(encoding="utf-8")

        self.assertIn("subprocess.Popen(command)", source)
        self.assertIn("is_paused_upon_creation=False", source)
        self.assertNotIn("subprocess.run(", source)
        self.assertNotIn("shell=True", source)
        self.assertNotIn("|| true", source)
        self.assertNotIn("BashOperator", source)
        self.assertEqual(
            source.count(
                '"docker", "--host", _DOCKER_ENGINE_HOST, "run"'
            ),
            1,
        )


if __name__ == "__main__":
    unittest.main()
