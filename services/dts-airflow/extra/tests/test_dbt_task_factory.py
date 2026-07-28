import importlib.util
import os
from pathlib import Path
import unittest
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
        }

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
            image="dts-dbt:1.10.0",
            project_host_root="/srv/dts/services/dts-dbt",
            profile_host_root="/dev/shm/dts-dbt-runtime",
            docker_network="dts-core",
        )

        self.assertIsInstance(command, list)
        self.assertEqual(command[:3], ["docker", "run", "--rm"])
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
                "dts-dbt:1.10.0",
                "/srv/dts/services/dts-dbt",
                "/dev/shm/dts-dbt-runtime",
                "dts-core",
            )
        with self.assertRaises(ValueError):
            self.factory._build_docker_command(
                invalid_lease,
                "dts-dbt:1.10.0",
                "/srv/dts/services/dts-dbt",
                "/dev/shm/dts-dbt-runtime",
                "dts-core",
            )

    def test_factory_source_has_one_checked_subprocess_owner(self):
        source = FACTORY_PATH.read_text(encoding="utf-8")

        self.assertIn("subprocess.run(command, check=True)", source)
        self.assertNotIn("shell=True", source)
        self.assertNotIn("|| true", source)
        self.assertNotIn("BashOperator", source)
        self.assertEqual(source.count("docker\", \"run"), 1)


if __name__ == "__main__":
    unittest.main()
