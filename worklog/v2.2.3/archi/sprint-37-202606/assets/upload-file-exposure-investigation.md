# 入湖上传文件明文暴露 — 现状调查报告

> 调查日期：2026-06-08
> 代码基线：v2.2.3
> 触发：现场（鲲鹏+麒麟 OS）确认上传 Excel 是否明文、能否从宿主机查看、是否被自动清理

## 1. 存储位置与加密状态

| 项 | 结论 | 证据 |
|----|------|------|
| 落盘方式 | **明文落盘，无加密** | `FileUploadService.java:89` `Files.copy(file.getInputStream(), hostPath, REPLACE_EXISTING)`；仅 `sha256()`（L135-152）算校验和 |
| 落盘目录 | `{jobDir}/uploads/{UUID前8}_{原名}` | `FileUploadService.java:78-87`（`UPLOADS_SUBDIR="uploads"` L35） |
| jobDir 来源 | 运行时 `IngestionSettings.jobDir`，回退 `DTS_ADDAX_JOB_DIR`（默认 `/opt/airflow/dags`） | `FileUploadService.java:290-297`；`application.yml:55`；`docker-compose-app.yml:715` |
| 宿主机映射 | **bind mount**：容器 `/opt/airflow/dags` ⟷ 宿主机 `{STACK_ROOT}/services/dts-airflow/dags` | `docker-compose-app.yml:783-788` |
| 文件权限 | **全员可读（o+r）** | 部署 init `chmod o+r`（`docker-compose-app.yml:200-201`，legacy/dev 同） |

**判定**：上传文件明文存在宿主机 `{STACK_ROOT}/services/dts-airflow/dags/uploads/`，任意宿主机用户（含 root）可直接 `ls`/`cat`。

## 2. Addax 容器共享机制

Addax 由 Airflow 调度时动态拉起（非常驻服务），通过 bind mount 读同一宿主机目录：

- `AirflowDagService.java:507-524`：`DockerOperator(auto_remove=True, mount_tmp_dir=False, mounts=[Mount(source=ADDAX_JOB_DIR, target="/opt/addax/jobs", type="bind"), ...])`
- `ADDAX_JOB_DIR = {STACK_ROOT}/services/dts-airflow/dags`（`docker-compose-app.yml:215`）
- Addax 启动入口是 dts 自有 wrapper `com.yuzhi.dts.addax.AddaxEnvRunner`（`addax-env-runner.jar`），源码 `services/dts-airflow/runner/`。

## 3. 「文件会不会被自动删除」逐一排除

| 机制 | 删 uploads 文件? | 依据 |
|------|------------------|------|
| 容器 `auto_remove=True` | ❌ 否 | bind mount，删容器不碰宿主机文件 |
| `docker compose up --force-recreate` | ❌ 否 | bind 不受影响；dags 是纯 bind 非 volume（`docker-compose-app.yml:250-251`）；force-recreate 连 named volume 都不删 |
| `--renew-anon-volumes` / `down -v` | ❌ 否（对 bind） | 仅影响匿名/命名卷 |
| `dts-reset` 出厂重置 | ❌ 否 | `bin/dts-reset:82-90` 只删 `*.py`/`*.json` 与子目录 `dwh ods ads exchange __pycache__`，**uploads 不在列表**，Excel 后缀也不匹配 |
| 升级脚本 | ❌ 未发现 | grep `bin/dts-upgrade*`、`dts-upgrade-common.sh` 无 dags/uploads 清理 |
| 正常入湖成功 | ❌ 否 | 无清理调用 |
| Level 3 `FULL_CASCADE` 回滚 | ✅ 是（唯一） | `DataRollbackService.java:255` → `FileUploadService.cleanupForTask()`（`:268-288`），仅 `executeLevel3Task`（`:231`）调用 |

**结论**：没有任何常规机制会自动删除 uploads 明文。代码与脚本均倾向**保留**（连 factory reset 都跳过 uploads）。

## 4. 现场 uploads 为空的真实可能原因

既然无自动删除机制，现场为空最可能是：

1. **现场入湖未走文件上传**（数据源为数据库 JDBC，如达梦/Oracle），`isFileSourceType` 为假，uploads 本就一直空 —— **最可能**。
2. **写入路径 ≠ 查看路径**：现场 `IngestionSettings.jobDir` 或 `DTS_ADDAX_JOB_DIR` 配到别处，与查看目录不一致。
3. 曾对相关任务执行 Level 3 `FULL_CASCADE` 回滚。

**现场验证命令**：

```bash
# A. 实际写入目录内容
docker exec dts-ingestion sh -lc 'echo "jobDir=$DTS_ADDAX_JOB_DIR"; ls -la /opt/airflow/dags/uploads 2>/dev/null'
# B. 宿主机 bind 目录
ls -la "${STACK_ROOT:-/opt/prod/s10/v2.2.3}/services/dts-airflow/dags/uploads"
# C. 是否存在文件上传类型的入湖任务（若全 DB 源则本就为空）
docker exec dts-pg psql -U dts_platform -d dts_platform \
  -c "select source_type, count(*) from ingestion_task group by source_type order by 2 desc;"
# D. Airflow 侧 Addax 读取路径是否与 A 一致
docker exec dts-airflow-scheduler sh -lc 'echo "ADDAX_JOB_DIR=$ADDAX_JOB_DIR"'
```

## 5. 整改方案技术支点（已验证可行）

- **现成加密**：`InfraSettingsCryptoService`（dts-ingestion）`encrypt/decrypt/randomIv/currentKeyVersion`，AES/GCM/NoPadding，密钥 `DTS_INFRA_ENCRYPTION_KEY`。
- **解密注入点**：`AddaxEnvRunner.run()` 在 `renderTemplate` 后、`runAddax` 前插入解密到 tmpfs，复用现成 `finally Files.deleteIfExists` 擦除；`writeTempJob` 已用 `TMPDIR`，DAG 把它指向 tmpfs 即明文不落盘。
- **Addax 零改动**：从 job.json 指定路径读明文，不感知加解密。

→ 据此立项 **Sprint-37（数据入湖上传文件加密专项）**，目标：磁盘恒密文、明文仅内存、宿主机（含 root）目录不可见明文、不影响 Addax 功能。
