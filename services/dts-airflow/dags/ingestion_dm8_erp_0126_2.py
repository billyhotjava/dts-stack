from __future__ import annotations

import os
from datetime import datetime

from airflow import DAG
from airflow.providers.docker.operators.docker import DockerOperator
from docker.types import Mount

ADDAX_IMAGE = os.getenv("ADDAX_IMAGE", "quay.io/wgzhao/addax:6.0.8")
ADDAX_JOB_DIR = os.getenv("ADDAX_JOB_DIR", "/opt/prod/s10/dts-stack/services/dts-addax/jobs")
ADDAX_DRIVER_DIR = os.getenv("ADDAX_DRIVER_DIR", "")
ADDAX_DRIVER_JARS = os.getenv("ADDAX_DRIVER_JARS", "")


def build_driver_mounts():
    mounts = []
    if not ADDAX_DRIVER_DIR or not ADDAX_DRIVER_JARS:
        return mounts
    target_dirs = [
        "/opt/addax/plugin/reader/rdbmsreader/lib",
        "/opt/addax/plugin/reader/rdbmsreader/libs",
        "/opt/addax/plugin/writer/rdbmswriter/lib",
        "/opt/addax/plugin/writer/rdbmswriter/libs",
    ]
    for jar_name in [jar.strip() for jar in ADDAX_DRIVER_JARS.split(",") if jar.strip()]:
        host_path = os.path.join(ADDAX_DRIVER_DIR, jar_name)
        if not os.path.exists(host_path):
            continue
        for target_dir in target_dirs:
            mounts.append(
                Mount(
                    source=host_path,
                    target=f"{target_dir}/{jar_name}",
                    type="bind",
                    read_only=True,
                )
            )
    return mounts


with DAG(
    dag_id="ingestion_dm8_erp_0126_2",
    schedule=None,
    start_date=datetime(2024, 1, 1),
    catchup=False,
    tags=["addax", "etl", "ingestion", "rdbmsreader", "dm8_erp_0126"],
) as dag:
    run_cmd = [
        "sh",
        "-lc",
        "/opt/addax/bin/addax.sh {{ dag_run.conf.get('job_path', '/opt/addax/jobs/job.json') }}",
    ]

    addax_run = DockerOperator(
        task_id="addax_run",
        image=ADDAX_IMAGE,
        api_version="auto",
        auto_remove=True,
        docker_url="unix://var/run/docker.sock",
        command=run_cmd,
        mount_tmp_dir=False,
        mounts=[
            Mount(source=ADDAX_JOB_DIR, target="/opt/addax/jobs", type="bind"),
            *build_driver_mounts(),
        ],
        environment={},
        tty=True,
    )
