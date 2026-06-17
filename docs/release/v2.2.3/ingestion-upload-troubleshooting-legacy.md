# Legacy 环境入湖文件上传 400 排查手册

适用场景：鲲鹏 + 银河麒麟现场，使用 `docker-compose.legacy.yml`，Docker 版本较老，Compose 为 `docker-compose` v1。现象通常是用户新建数据入湖任务时上传 Excel/CSV，页面没有明显提示，Chrome Network 里看到 400，`dts-platform` 日志出现 `Ingestion file upload failed`。

## 注意事项

- 所有命令默认在部署目录执行，例如 `/opt/prod/s10/v2.2.3`。
- 现场使用老版本 Docker 和 `docker-compose` v1，命令统一使用 `docker-compose`，不要使用新版 `docker compose` 插件命令。
- 不要回传密钥明文。涉及 `DTS_INFRA_ENCRYPTION_KEY`、`AIRFLOW_FERNET_KEY`、`AIRFLOW__CORE__FERNET_KEY` 时，只回传 `SET/EMPTY`、长度、错误日志即可。
- `AIRFLOW_FERNET_KEY` 不要随意更换。它用于 Airflow 元数据库敏感字段加密，随意替换可能导致已有 Connection/Variable 无法解密。
- `DTS_INFRA_ENCRYPTION_KEY` 也不要在已有上传密文数据的环境里随意更换。它用于 DTS 上传文件密文落盘和后续 Addax 解密，替换后历史上传密文可能无法解密。

## 快速结论

`DTS_INFRA_ENCRYPTION_KEY` 是上传 Excel/CSV 的关键配置。`dts-ingestion` 在解析文件类型之前会先检查该 key 是否可用；如果为空或非法，会直接返回 400，表现为所有 Excel/CSV 都失败。

`AIRFLOW_FERNET_KEY` 不是上传预览接口的直接依赖。它是 Airflow 自身配置，主要影响 Airflow 元数据库里的 Connection/Variable 加解密和 DAG 运行时配置读取，不会直接导致 `/api/ingestion/files/upload-and-parse` 上传预览接口 400。

## 1. 记录 Docker 和 Compose 版本

```bash
cd /opt/prod/s10/v2.2.3

docker version
docker-compose version
docker-compose -f docker-compose.legacy.yml ps
```

判断：

- 如果 `docker-compose version` 显示 v1，这是现场预期路径。
- 如果服务未运行，先不要改配置，先保存 `ps` 输出和对应服务日志。

## 2. 检查 .env 中关键变量是否存在

只检查长度，不打印明文：

```bash
cd /opt/prod/s10/v2.2.3

awk '
  /^DTS_INFRA_ENCRYPTION_KEY=/ {
    v=$0; sub(/^[^=]*=/, "", v);
    print "DTS_INFRA_ENCRYPTION_KEY=" (length(v) > 0 ? "SET" : "EMPTY") ", len=" length(v)
  }
  /^DTS_INFRA_KEY_VERSION=/ {
    v=$0; sub(/^[^=]*=/, "", v);
    print "DTS_INFRA_KEY_VERSION=" (length(v) > 0 ? v : "EMPTY")
  }
  /^AIRFLOW_FERNET_KEY=/ {
    v=$0; sub(/^[^=]*=/, "", v);
    print "AIRFLOW_FERNET_KEY=" (length(v) > 0 ? "SET" : "EMPTY") ", len=" length(v)
  }
' .env
```

预期：

- `DTS_INFRA_ENCRYPTION_KEY=SET`，长度通常为 44。
- `DTS_INFRA_KEY_VERSION=v1`。
- `AIRFLOW_FERNET_KEY=SET`，长度通常为 43 或 44。

处理：

- 如果 `DTS_INFRA_ENCRYPTION_KEY=EMPTY`，上传 Excel/CSV 会在解析前失败。需要补齐 key，并重建 `dts-ingestion`，建议同时重建 `dts-platform`。
- 如果 `DTS_INFRA_KEY_VERSION=EMPTY`，设置为 `v1`。
- 如果只有 `AIRFLOW_FERNET_KEY` 异常，优先排查 Airflow，不应先把它归因为上传预览 400。

## 3. 检查运行中容器是否拿到变量

`.env` 有值不代表运行中的容器已经拿到值。老版本 `docker-compose` v1 下，如果修改 `.env` 后没有 recreate，容器仍可能使用旧环境变量。

```bash
cd /opt/prod/s10/v2.2.3

docker-compose -f docker-compose.legacy.yml exec -T dts-ingestion sh -lc '
echo "service=dts-ingestion"
echo "DTS_INFRA_ENCRYPTION_KEY=$([ -n "$DTS_INFRA_ENCRYPTION_KEY" ] && echo SET || echo EMPTY), len=${#DTS_INFRA_ENCRYPTION_KEY}"
echo "DTS_INFRA_KEY_VERSION=${DTS_INFRA_KEY_VERSION:-EMPTY}"
echo "DTS_ADDAX_JOB_DIR=${DTS_ADDAX_JOB_DIR:-EMPTY}"
echo "DTS_AIRFLOW_DAGS_DIR=${DTS_AIRFLOW_DAGS_DIR:-EMPTY}"
echo "JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS:-EMPTY}"
'

docker-compose -f docker-compose.legacy.yml exec -T dts-platform sh -lc '
echo "service=dts-platform"
echo "DTS_INFRA_ENCRYPTION_KEY=$([ -n "$DTS_INFRA_ENCRYPTION_KEY" ] && echo SET || echo EMPTY), len=${#DTS_INFRA_ENCRYPTION_KEY}"
echo "DTS_INFRA_KEY_VERSION=${DTS_INFRA_KEY_VERSION:-EMPTY}"
echo "DTS_INGESTION_BASE_URL=${DTS_INGESTION_BASE_URL:-EMPTY}"
echo "DTS_INGESTION_SERVICE_NAME=${DTS_INGESTION_SERVICE_NAME:-EMPTY}"
echo "JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS:-EMPTY}"
'
```

Airflow 的 Fernet key 单独检查：

```bash
cd /opt/prod/s10/v2.2.3

docker-compose -f docker-compose.legacy.yml exec -T dts-airflow-webserver sh -lc '
echo "service=dts-airflow-webserver"
echo "AIRFLOW__CORE__FERNET_KEY=$([ -n "$AIRFLOW__CORE__FERNET_KEY" ] && echo SET || echo EMPTY), len=${#AIRFLOW__CORE__FERNET_KEY}"
'

docker-compose -f docker-compose.legacy.yml exec -T dts-airflow-scheduler sh -lc '
echo "service=dts-airflow-scheduler"
echo "AIRFLOW__CORE__FERNET_KEY=$([ -n "$AIRFLOW__CORE__FERNET_KEY" ] && echo SET || echo EMPTY), len=${#AIRFLOW__CORE__FERNET_KEY}"
'
```

判断：

- `.env` 有值但 `dts-ingestion` 容器里 `DTS_INFRA_ENCRYPTION_KEY=EMPTY`：容器未重建或不是从当前目录的 `.env` 启动。
- `dts-platform` 有值但 `dts-ingestion` 为空：上传仍会失败，因为实际加密落盘在 `dts-ingestion`。
- Airflow Fernet key 为空不直接解释上传预览 400，但会影响后续 Airflow 任务。

## 4. 检查日志关键字

平台侧日志：

```bash
cd /opt/prod/s10/v2.2.3

egrep -n "Ingestion file upload failed|Ingestion file upload error|文件上传失败" logs/dts-platform/app.log | tail -50
```

Ingestion 侧日志：

```bash
cd /opt/prod/s10/v2.2.3

egrep -n "Upload and parse file failed|加密密钥未配置|无法创建上传目录|读取上传文件失败|不支持的文件类型|dts.platform.infra.encryption-key" logs/dts-ingestion/app.log | tail -80
```

说明：

- 新增日志补丁后，`dts-ingestion` 在 INFO 级别会打印 `Upload and parse file failed: name=..., size=..., contentType=..., previewLimit=..., sheetIndex=..., sheetName=...`，并带异常栈。
- 如果平台侧有 `Ingestion file upload failed status=400 body=`，但 ingestion 侧完全没有 `Upload and parse file failed`，可能是现场镜像还没有包含日志补丁，或者请求没有真正到达 `dts-ingestion`。
- 如果 ingestion 侧有 `加密密钥未配置`，根因为 `DTS_INFRA_ENCRYPTION_KEY` 未进入运行中 `dts-ingestion`。
- 如果 ingestion 侧有 `dts.platform.infra.encryption-key 不是合法的 Base64 编码` 或 `解码后长度必须为 16/24/32 字节`，根因为 key 格式非法。
- 如果 ingestion 侧有 `无法创建上传目录`，转到目录权限排查。
- 如果 ingestion 侧有 `读取上传文件失败`，重点检查上传临时目录 `/apptmp`、文件大小、容器 tmpfs。

## 5. 检查上传目录和临时目录权限

上传预览的公共链路会写入 `/opt/airflow/dags/uploads`，同时 Java multipart 和 POI 临时文件会进入 `/apptmp`。

```bash
cd /opt/prod/s10/v2.2.3

docker-compose -f docker-compose.legacy.yml exec -T dts-ingestion sh -lc '
set -eu
echo "service=dts-ingestion"
id
echo "check dirs"
ls -ld /apptmp /opt/airflow /opt/airflow/dags 2>&1 || true
mkdir -p /opt/airflow/dags/uploads
ls -ld /opt/airflow/dags/uploads
echo "write probe: /opt/airflow/dags/uploads"
touch /opt/airflow/dags/uploads/.dts_upload_probe
rm -f /opt/airflow/dags/uploads/.dts_upload_probe
echo "write probe: /apptmp"
touch /apptmp/.dts_tmp_probe
rm -f /apptmp/.dts_tmp_probe
echo "OK"
'
```

宿主机侧也检查：

```bash
cd /opt/prod/s10/v2.2.3

ls -ld services/dts-airflow services/dts-airflow/dags services/dts-airflow/dags/uploads logs/dts-ingestion logs/dts-platform 2>&1 || true
getenforce 2>/dev/null || true
```

处理：

- 如果 `/opt/airflow/dags/uploads` 不可写，修复宿主机 `services/dts-airflow/dags` 权限后重试。
- 如果 `/apptmp` 不存在或不可写，检查 `docker-compose.legacy.yml` 的 `tmpfs` 是否被老 Docker 正确挂载；必要时重建容器。
- 如果 Kylin 环境 SELinux/安全策略导致 bind mount 不可写，先保存 `getenforce` 和目录权限输出，再按现场安全策略修复标签或权限。

## 6. 用最小 CSV 直接验证 ingestion 上传接口

这个验证绕过浏览器和前端，只确认 `dts-ingestion` 上传解析链路本身是否可用。

```bash
cd /opt/prod/s10/v2.2.3

docker-compose -f docker-compose.legacy.yml exec -T dts-ingestion sh -lc '
cat >/tmp/dts-upload-probe.csv <<EOF
col_a,col_b
1,2
EOF

curl -sS -w "\nHTTP_CODE=%{http_code}\n" \
  -H "X-DTS-Service: dts-platform" \
  -F "file=@/tmp/dts-upload-probe.csv;type=text/csv" \
  "http://127.0.0.1:8083/api/ingestion/files/upload-and-parse?previewLimit=5"
'
```

判断：

- `HTTP_CODE=200`：`dts-ingestion` 上传解析基本正常，继续看 `dts-platform` 代理、页面错误提示或权限。
- `HTTP_CODE=400`：看 `logs/dts-ingestion/app.log` 里的 `Upload and parse file failed` 具体异常。
- `HTTP_CODE=401/403`：服务间认证头未被当前镜像接受，先保留输出，再从页面复现并看 platform/ingestion 双侧日志。
- 连接失败：检查 `dts-ingestion` 容器健康状态和端口监听。

## 7. 常见根因和处理动作

| 证据 | 判断 | 处理 |
| --- | --- | --- |
| `dts-ingestion` 容器里 `DTS_INFRA_ENCRYPTION_KEY=EMPTY` | 上传解析前置加密配置缺失 | 补齐 `.env`，重建 `dts-ingestion`；建议同时重建 `dts-platform` |
| `.env` 有 key，但容器里为空 | 修改 `.env` 后未 recreate，或不是从当前目录启动 | 使用 legacy compose v1 在当前部署目录 recreate |
| `加密密钥未配置，禁止上传涉密文件` | DTS 上传文件密文落盘 key 不可用 | 修复 `DTS_INFRA_ENCRYPTION_KEY` 和 `DTS_INFRA_KEY_VERSION` |
| `不是合法的 Base64 编码` | key 格式非法 | 使用合法 Base64 AES key，解码后长度必须为 16/24/32 字节 |
| `无法创建上传目录` | `/opt/airflow/dags/uploads` 不可写 | 修复 `services/dts-airflow/dags` 宿主机目录权限或安全标签 |
| `读取上传文件失败` | multipart 临时文件或文件大小问题 | 检查 `/apptmp` tmpfs、容器空间、文件大小 |
| 最小 CSV 直连 200，但页面仍 400 | ingestion 正常，问题在 platform 代理、前端提示或用户权限链路 | 收集 `dts-platform` 日志和浏览器 Network response |
| ingestion 没有新增 INFO 日志 | 现场运行镜像未包含日志补丁，或请求未到 ingestion | 确认镜像版本并重建/替换 `dts-ingestion` |

## 8. 修改 .env 后的重建命令

如果只是补齐或修正 `DTS_INFRA_ENCRYPTION_KEY` / `DTS_INFRA_KEY_VERSION`，使用老版 Compose v1：

```bash
cd /opt/prod/s10/v2.2.3

docker-compose -f docker-compose.legacy.yml up -d --force-recreate --no-deps dts-ingestion
docker-compose -f docker-compose.legacy.yml up -d --force-recreate --no-deps dts-platform
docker-compose -f docker-compose.legacy.yml ps dts-ingestion dts-platform
```

然后重新执行：

```bash
docker-compose -f docker-compose.legacy.yml exec -T dts-ingestion sh -lc '
echo "DTS_INFRA_ENCRYPTION_KEY=$([ -n "$DTS_INFRA_ENCRYPTION_KEY" ] && echo SET || echo EMPTY), len=${#DTS_INFRA_ENCRYPTION_KEY}"
echo "DTS_INFRA_KEY_VERSION=${DTS_INFRA_KEY_VERSION:-EMPTY}"
'
```

如果需要部署包含 `FileUploadResource.java` 日志补丁的新镜像，现场可构建时参考：

```bash
cd /opt/prod/s10/v2.2.3

SAVE_IMAGE_TARS=false LEGACY_USE_HOST_MAVEN=1 ./builds/dts-build.sh --image dts-ingestion --legacy --no-save
docker-compose -f docker-compose.legacy.yml up -d --force-recreate --no-deps dts-ingestion
```

如果现场通过离线镜像包交付，则先加载新镜像，再执行上面的 `up -d --force-recreate --no-deps dts-ingestion`。

## 9. 建议现场回传的最小信息包

不要包含密钥明文。

```bash
cd /opt/prod/s10/v2.2.3

OUT="upload-diagnosis-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"

{
  date
  docker version
  docker-compose version
  docker-compose -f docker-compose.legacy.yml ps
} > "$OUT/runtime.txt" 2>&1

awk '
  /^DTS_INFRA_ENCRYPTION_KEY=/ {
    v=$0; sub(/^[^=]*=/, "", v);
    print "DTS_INFRA_ENCRYPTION_KEY=" (length(v) > 0 ? "SET" : "EMPTY") ", len=" length(v)
  }
  /^DTS_INFRA_KEY_VERSION=/ {
    v=$0; sub(/^[^=]*=/, "", v);
    print "DTS_INFRA_KEY_VERSION=" (length(v) > 0 ? v : "EMPTY")
  }
  /^AIRFLOW_FERNET_KEY=/ {
    v=$0; sub(/^[^=]*=/, "", v);
    print "AIRFLOW_FERNET_KEY=" (length(v) > 0 ? "SET" : "EMPTY") ", len=" length(v)
  }
' .env > "$OUT/env-safe.txt" 2>&1

docker-compose -f docker-compose.legacy.yml exec -T dts-ingestion sh -lc '
echo "DTS_INFRA_ENCRYPTION_KEY=$([ -n "$DTS_INFRA_ENCRYPTION_KEY" ] && echo SET || echo EMPTY), len=${#DTS_INFRA_ENCRYPTION_KEY}"
echo "DTS_INFRA_KEY_VERSION=${DTS_INFRA_KEY_VERSION:-EMPTY}"
echo "DTS_ADDAX_JOB_DIR=${DTS_ADDAX_JOB_DIR:-EMPTY}"
echo "DTS_AIRFLOW_DAGS_DIR=${DTS_AIRFLOW_DAGS_DIR:-EMPTY}"
ls -ld /apptmp /opt/airflow/dags /opt/airflow/dags/uploads 2>&1 || true
' > "$OUT/ingestion-env-and-dirs.txt" 2>&1

egrep -n "Ingestion file upload failed|Ingestion file upload error|文件上传失败" logs/dts-platform/app.log | tail -80 > "$OUT/platform-upload-errors.txt" 2>&1 || true
egrep -n "Upload and parse file failed|加密密钥未配置|无法创建上传目录|读取上传文件失败|不支持的文件类型|dts.platform.infra.encryption-key" logs/dts-ingestion/app.log | tail -120 > "$OUT/ingestion-upload-errors.txt" 2>&1 || true

tar czf "$OUT.tar.gz" "$OUT"
echo "$OUT.tar.gz"
```

回传 `upload-diagnosis-*.tar.gz` 即可。
