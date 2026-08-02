from pathlib import Path
import unittest


REPOSITORY_ROOT = Path(__file__).resolve().parents[4]


class Sprint83LegacyDbtSurfaceRetirementTest(unittest.TestCase):
    def test_legacy_dbt_execution_and_shared_git_surfaces_are_physically_retired(self):
        retired_paths = (
            "bin/dts-deploy",
            "bin/dts-deploy-env.sh",
            "bin/dts-dbt-import",
            "bin/dts-pack",
            "services/dts-airflow/dags/dwh/dwh_dbt_dbt_manual.py",
            "services/dts-airflow/dags/dwh/dwh_project_management_dbt_manual.py",
            "source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/DbtGitResource.java",
            "source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtGitService.java",
            "tests/web-e2e/specs/biz/elt-development-center-smoke.spec.ts",
            "tests/web-e2e/pages/ModelingPage.ts",
        )

        self.assertEqual(
            [path for path in retired_paths if (REPOSITORY_ROOT / path).exists()],
            [],
        )

    def test_frontend_does_not_export_shared_dbt_git_routes(self):
        platform_api = (
            REPOSITORY_ROOT / "source/dts-platform-webapp/src/api/platformApi.ts"
        ).read_text(encoding="utf-8")

        self.assertNotIn("/etl/dbt/git/", platform_api)

        active_elt_mocks = (
            REPOSITORY_ROOT / "tests/web-e2e/support/platform-elt-smoke-mock.ts"
        ).read_text(encoding="utf-8")
        self.assertNotIn("installPlatformSqlModelingMocks", active_elt_mocks)
        self.assertNotIn("/etl/dbt/dag/ready", active_elt_mocks)
        self.assertNotIn("dwh_project_management_dbt_manual", active_elt_mocks)


if __name__ == "__main__":
    unittest.main()
