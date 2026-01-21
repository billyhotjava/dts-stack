"""Register OpenMetadata managed APIs plugin with Airflow."""

try:
    from openmetadata_managed_apis.plugin import (
        RestApiPlugin as OpenMetadataManagedApisPlugin,
    )
except Exception:  # pragma: no cover - runtime import hook
    from airflow.plugins_manager import AirflowPlugin

    class OpenMetadataManagedApisPlugin(AirflowPlugin):
        name = "openmetadata_managed_apis_unavailable"
