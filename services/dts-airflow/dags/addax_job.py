from __future__ import annotations

import os
from datetime import datetime

from airflow import DAG
from airflow.providers.docker.operators.docker import DockerOperator
from docker.types import Mount

ADDAX_IMAGE = os.getenv("ADDAX_IMAGE", "wgzhao/addax:0.59.1")
ADDAX_JOB_DIR = os.getenv("ADDAX_JOB_DIR", "/opt/prod/s10/dts-stack/services/dts-addax/jobs")


with DAG(
    dag_id="addax_job",
    schedule=None,
    start_date=datetime(2024, 1, 1),
    catchup=False,
    tags=["addax", "etl"],
) as dag:
    run_cmd = [
        "python",
        "/opt/addax/bin/addax.py",
        "{{ dag_run.conf.get('job_path', '/opt/addax/jobs/job.json') }}",
    ]

    addax_run = DockerOperator(
        task_id="addax_run",
        image=ADDAX_IMAGE,
        api_version="auto",
        auto_remove=True,
        docker_url="unix://var/run/docker.sock",
        command=run_cmd,
        mounts=[
            Mount(source=ADDAX_JOB_DIR, target="/opt/addax/jobs", type="bind"),
        ],
        environment={},
        tty=True,
    )
