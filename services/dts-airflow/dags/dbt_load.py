from __future__ import annotations

import os
from datetime import datetime

from airflow import DAG
from airflow.providers.docker.operators.docker import DockerOperator
from docker.types import Mount

DBT_IMAGE = os.getenv("DBT_IMAGE", "ghcr.io/dbt-labs/dbt-core:1.11.2")
OM_INGEST_IMAGE = os.getenv("OPENMETADATA_INGEST_IMAGE", "openmetadata/ingestion:1.11.5")
DBT_PROJECT_DIR = os.getenv("DBT_PROJECT_DIR", "/opt/prod/s10/dts-stack/services/dts-dbt")
DBT_PROFILES_DIR = os.getenv("DBT_PROFILES_DIR", "/opt/prod/s10/dts-stack/services/dts-dbt/profiles")
OM_INGEST_CONFIG_DIR = os.getenv(
    "OPENMETADATA_INGEST_CONFIG_DIR", "/opt/prod/s10/dts-stack/services/dts-openmetadata/ingestion"
)
OM_API_ENDPOINT = os.getenv("OPENMETADATA_API_ENDPOINT", "http://dts-openmetadata:8585/api/v1")
OM_AUTH_TOKEN = os.getenv("OPENMETADATA_AUTH_TOKEN", "")


def build_model_selector(dag_run):
    if dag_run and dag_run.conf:
        return dag_run.conf.get("models", "")
    return ""


def build_target(dag_run):
    if dag_run and dag_run.conf:
        return dag_run.conf.get("target", "")
    return ""


def build_vars(dag_run):
    if dag_run and dag_run.conf:
        return dag_run.conf.get("vars", {})
    return {}


with DAG(
    dag_id="dbt_load",
    schedule=None,
    start_date=datetime(2024, 1, 1),
    catchup=False,
    tags=["dbt", "etl"],
) as dag:
    run_cmd = [
        "bash",
        "-lc",
        "python -m pip install --no-cache-dir dbt-postgres && "
        + "dbt deps --profiles-dir /root/.dbt --project-dir /opt/dbt && "
        + "dbt run --profiles-dir /root/.dbt --project-dir /opt/dbt "
        + "{% if dag_run and dag_run.conf and dag_run.conf.get('target') %}--target {{ dag_run.conf.get('target') }} {% endif %}"
        + "{% if dag_run and dag_run.conf and dag_run.conf.get('models') %}--models {{ dag_run.conf.get('models') }} {% endif %}",
    ]
    test_cmd = [
        "bash",
        "-lc",
        "python -m pip install --no-cache-dir dbt-postgres && "
        + "dbt test --profiles-dir /root/.dbt --project-dir /opt/dbt "
        + "{% if dag_run and dag_run.conf and dag_run.conf.get('target') %}--target {{ dag_run.conf.get('target') }} {% endif %}"
        + "{% if dag_run and dag_run.conf and dag_run.conf.get('models') %}--models {{ dag_run.conf.get('models') }} {% endif %}"
        + " && dbt docs generate --profiles-dir /root/.dbt --project-dir /opt/dbt",
    ]

    dbt_run = DockerOperator(
        task_id="dbt_run",
        image=DBT_IMAGE,
        api_version="auto",
        auto_remove=True,
        docker_url="unix://var/run/docker.sock",
        command=run_cmd,
        mounts=[
            Mount(source=DBT_PROJECT_DIR, target="/opt/dbt", type="bind"),
            Mount(source=DBT_PROFILES_DIR, target="/root/.dbt", type="bind"),
        ],
        environment={},
        tty=True,
    )

    dbt_test = DockerOperator(
        task_id="dbt_test",
        image=DBT_IMAGE,
        api_version="auto",
        auto_remove=True,
        docker_url="unix://var/run/docker.sock",
        command=test_cmd,
        mounts=[
            Mount(source=DBT_PROJECT_DIR, target="/opt/dbt", type="bind"),
            Mount(source=DBT_PROFILES_DIR, target="/root/.dbt", type="bind"),
        ],
        environment={},
        tty=True,
    )

    om_ingest = DockerOperator(
        task_id="openmetadata_ingest",
        image=OM_INGEST_IMAGE,
        api_version="auto",
        auto_remove=True,
        docker_url="unix://var/run/docker.sock",
        command=["metadata", "ingest", "-c", "/opt/openmetadata/ingestion/dbt.yml"],
        mounts=[
            Mount(source=DBT_PROJECT_DIR, target="/opt/dbt", type="bind"),
            Mount(source=OM_INGEST_CONFIG_DIR, target="/opt/openmetadata/ingestion", type="bind"),
        ],
        environment={
            "OPENMETADATA_API_ENDPOINT": OM_API_ENDPOINT,
            "OPENMETADATA_AUTH_TOKEN": OM_AUTH_TOKEN,
            "OPENMETADATA_DBT_SERVICE": os.getenv("OPENMETADATA_DBT_SERVICE", "hive"),
        },
        tty=True,
    )

    dbt_run >> dbt_test >> om_ingest
