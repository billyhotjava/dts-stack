# IT-06 runtime preflight commands

执行时间：2026-07-31 02:43～02:45 CST

```bash
./mvnw -ntp \
  -Dtest=DbtRuntimeProfileLeaseRepositoryTest,DbtRuntimeProfileLeaseServiceTest,DbtRuntimeProfileLeaseInternalResourceTest,DbtRuntimeProfileLeaseInternalAuthorizationTest \
  test
/usr/bin/python3 services/dts-airflow/extra/tests/test_dbt_task_factory.py -q

docker exec dts-airflow-scheduler python -c \
  "from airflow.settings import Session; from sqlalchemy import text; s=Session(); print('running_dagruns='+str(s.execute(text(\"select count(*) from dag_run where state='running'\")).scalar())); print('active_task_instances='+str(s.execute(text(\"select count(*) from task_instance where state in ('running','queued','scheduled')\")).scalar())); s.close()"
docker compose -f docker-compose-app.yml restart \
  dts-airflow-scheduler dts-airflow-webserver
docker exec dts-airflow-webserver curl -fsS \
  http://127.0.0.1:8080/api/v1/health
sha256sum services/dts-airflow/extra/dts_runtime/dbt_task_factory.py
docker exec dts-airflow-scheduler \
  sha256sum /opt/airflow/extra/dts_runtime/dbt_task_factory.py
```

精确结果：

```text
Java 23/23, BUILD SUCCESS
Python 28/28, OK
running_dagruns=0
active_task_instances=0
scheduler=healthy
triggerer=healthy
webserver=healthy
host/container factory sha256=d676a17140d269ece3e917ebf410692c3493df9b0afcc2a8c3a1569f053d38ad
```
