# DTS OpManager

`dts-opmanager` 是 DTS 产品的独立运维与离线升级控制台，用于在现场通过 Web UI 完成升级包登记、配置预检、配置差异处理、镜像加载和容器重建。它不是 `dts-upgrade-lite` 的封装，也不依赖主 DTS 系统登录态。

## 功能范围

- 后端：Spring Boot 3.4.5 / Java 21。
- 前端：React 18 / Vite，由同一个 Spring Boot 应用托管静态资源。
- 浏览器兼容：生产构建面向 Chrome 95。
- 状态存储：现场部署默认写入 `${OPMANAGER_HOME}/data`。
- 升级工作区：固定使用 `images/`、`dts-stack/`、`misc/` 三个顶层目录。
- 运行环境：通过 Docker API 获取容器状态、加载镜像、重建 Compose 服务。
- 部署约束：不依赖 Keycloak、DTS 主 PostgreSQL、Traefik 或 systemd。

## 本地开发

后端：

```bash
mvn test
mvn clean package
java -jar target/dts-opmanager-2.2.3-SNAPSHOT.jar
```

`mvn clean package` 会构建 React 前端，并把生成文件复制到可执行 jar 中。只有当前端文件已经生成到 `src/main/resources/static` 时，才使用 `-Dskip.webapp=true`。

前端：

```bash
cd src/main/webapp
pnpm install
pnpm dev
pnpm build
```

Vite 开发服务会把 `/api` 代理到 `http://localhost:18090`。

## 配置项

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `OPMANAGER_SERVER_PORT` | `18095` | `java -jar` 运行时 HTTP 端口；Docker 镜像内部端口固定为 `18090` |
| `OPMANAGER_HOME` | `/opt/dts-opmanager` | 现场宿主机上的 OpManager 根目录；`deploy/`、`data/`、`packages/` 都放在这个目录下 |
| `OPMANAGER_DATA_DIR` | `${OPMANAGER_HOME}/data` | 状态、上传文件、任务记录目录；由 `deploy/docker-compose.yml` 从 `OPMANAGER_HOME` 派生 |
| `OPMANAGER_PACKAGE_ROOTS` | `${OPMANAGER_HOME}/packages` | 升级工作区根目录，只保留 `images/`、`dts-stack/`、`misc/`；由 `deploy/docker-compose.yml` 从 `OPMANAGER_HOME` 派生 |
| `OPMANAGER_TARGET_STACK_DIR` | `/opt/dts-stack` | 现场 DTS stack 运行目录 |
| `OPMANAGER_DOCKER_ENABLED` | `true` | 是否启用 Docker 状态检查和执行能力 |
| `OPMANAGER_PORTAINER_URL` | 空 | 可选的 Portainer 外链入口 |

## 编译 OpManager 运行包

不要直接运行下面这种命令作为第一步：

```bash
docker build -t dts-opmanager:2.2.3 -f Dockerfile .
```

当前 `Dockerfile` 是运行镜像 Dockerfile，只负责把已经生成的 `target/dts-opmanager-*.jar` 复制进镜像。如果直接执行裸 `docker build`，会因为 jar 不存在而报错：

```text
COPY target/dts-opmanager-*.jar ... COPY failed: no source files were specified
```

正确方式是使用 `build-image.sh`：

```bash
cd /opt/prod/s10/v2.2.3/opmanager

./build-image.sh \
  --tag dts-opmanager:2.2.3 \
  --output dts-opmanager-runtime-2.2.3-linux-arm64.tar.gz
```

这个脚本会一次完成：

1. 使用 Node 容器构建前端。
2. 使用 Maven 容器构建 `target/dts-opmanager-*.jar`。
3. 构建 `dts-opmanager:2.2.3` 运行镜像。
4. 生成现场运行包 `dts-opmanager-runtime-2.2.3-linux-arm64.tar.gz`。

运行包内包含：

```text
deploy/docker-compose.yml
deploy/env.example
deploy/start.sh
deploy/dts-opmanager-2.2.3-linux-arm64.tar
data/
packages/
README.md
```

鲲鹏 / 麒麟 ARM64 环境下也使用同一个脚本。脚本会参考 `builds/dts-build.sh` 的处理方式，在 ARM64 上自动为 Maven 容器增加：

- `JAVA_HOME=/opt/java/openjdk`
- `seccomp=unconfined`
- `nproc=65535:65535`

如果需要强制拉取基础镜像：

```bash
DOCKER_BUILD_PULL=1 ./build-image.sh \
  --tag dts-opmanager:2.2.3 \
  --output dts-opmanager-runtime-2.2.3-linux-arm64.tar.gz
```

如果现场 Maven 容器仍然受限，也可以使用宿主机 Maven：

```bash
./build-image.sh \
  --host-maven \
  --tag dts-opmanager:2.2.3 \
  --output dts-opmanager-runtime-2.2.3-linux-arm64.tar.gz
```

构建完成后确认镜像架构：

```bash
docker image inspect dts-opmanager:2.2.3 --format '{{.Architecture}}'
```

鲲鹏环境期望输出：

```text
arm64
```

## 现场运行

现场不需要拷贝 `opmanager/` 源码目录，只拷贝两个包：

```text
dts-opmanager-runtime-2.2.3-linux-arm64.tar.gz   # OpManager 运行包
dts-opmanager-upgrade-<时间>.tar.gz               # DTS 业务升级包
```

现场只需要指定一个 OpManager 根目录，后续目录都以它为基础。下面以 `/opt/dts-opmanager` 为例：

```bash
export OPMANAGER_HOME=/opt/dts-opmanager

mkdir -p "$OPMANAGER_HOME"
tar -xzf dts-opmanager-runtime-2.2.3-linux-arm64.tar.gz -C "$OPMANAGER_HOME"
```

目录含义：

| 目录 | 用途 |
| --- | --- |
| `${OPMANAGER_HOME}/deploy` | 放 `docker-compose.yml`、`.env`、`start.sh`、OpManager 镜像 tar |
| `${OPMANAGER_HOME}/data` | OpManager 自己的状态、任务记录和上传临时文件 |
| `${OPMANAGER_HOME}/packages` | DTS 升级工作区，顶层只放 `images/`、`dts-stack/`、`misc/` |

启动 OpManager：

```bash
cd "$OPMANAGER_HOME/deploy"
./start.sh
```

`start.sh` 会自动：

- 按当前解压目录生成或更新 `.env` 里的 `OPMANAGER_HOME`。
- 执行 `docker load -i dts-opmanager-*.tar`。
- 使用 `docker compose` 或旧版 `docker-compose` 启动服务。

现场已有 DTS stack 运行目录不需要写进 `.env`；启动后在页面“概览”里填写，例如 `/data/dts-stack`。部署 compose 默认把宿主机 `/opt` 和 `/data` 挂进容器，因此 DTS stack 建议放在这两个根目录下。

验证：

```bash
docker ps | grep dts-opmanager
curl http://127.0.0.1:18095/actuator/health
```

浏览器访问：

```text
http://<server-ip>:18095/
```

然后把 DTS 升级包放到默认升级目录并解压：

```bash
cp dts-opmanager-upgrade-*.tar.gz "$OPMANAGER_HOME/packages/"
tar -xzf "$OPMANAGER_HOME/packages"/dts-opmanager-upgrade-*.tar.gz -C "$OPMANAGER_HOME/packages"
```

## 生成升级包

联网编译服务器用主工程的 `builds/dts-build.sh` 生成一个可搬运的 opmanager 升级包：

```bash
cd /opt/prod/s10/v2.2.3

./builds/dts-build.sh \
  --image dts-admin dts-admin-webapp dts-ingestion dts-platform dts-platform-webapp dts-analytics dts-metrics \
  --legacy \
  --opmanager-package
```

默认输出目录会区分现场类型：

```text
builds/opmanager-dist/                 # 普通 DTS 升级包
builds/opmanager-legacy-dist/          # legacy 升级包，仅用于鲲鹏/麒麟现场
```

带 `--legacy` 时会输出到 `builds/opmanager-legacy-dist/dts-opmanager-upgrade-<时间>.tar.gz`。如果需要指定输出目录：

```bash
./builds/dts-build.sh \
  --image dts-admin dts-admin-webapp dts-ingestion dts-platform dts-platform-webapp dts-analytics dts-metrics \
  --legacy \
  --opmanager-output /tmp/release
```

生成的 `tar.gz` 解开后必须是：

```text
images/      # 本次镜像列表 docker save 生成的 tar 文件
dts-stack/   # 剔除源码、日志、git 信息后的 DTS stack 工程目录
misc/        # release-manifest、checksums、merge-rules 等杂项
```

现场把这个包放进 OpManager 默认升级目录并解压：

```bash
cp dts-opmanager-upgrade-*.tar.gz /opt/dts-opmanager/packages/
tar -xzf /opt/dts-opmanager/packages/dts-opmanager-upgrade-*.tar.gz -C /opt/dts-opmanager/packages
```

`tar.gz` 文件可以保留在目录里；opmanager 校验工作区时会忽略顶层的 `*.tar.gz` / `*.tgz` 文件。

## 页面操作流程

1. 打开 `http://<server-ip>:18095/`。
2. 在“概览”页填写现场已有 DTS stack 运行目录，例如 `/data/dts-stack`。
3. 在“升级包”页扫描默认升级目录：`/opt/dts-opmanager/packages`。
4. 在“配置预检”页比较现场 DTS stack 和升级包配置。
5. 对 `.env`、compose、MDM 等受保护文件逐项确认差异。
6. 必要时使用中间的“替换”按钮，用右侧升级包文件覆盖左侧现场文件；系统会先生成备份。
7. 在工作区页加载 `images/*.tar`。
8. 重建容器并观察运行状态。

## 常见问题

### `chdir to cwd ("/opt/dts-opmanager") ... no such file or directory`

原因：旧版 OpManager 镜像把应用工作目录放在 `/opt/dts-opmanager`，而部署 compose 又会挂载宿主机 `/opt`，导致镜像内工作目录被覆盖。

处理：重新使用当前版本 `build-image.sh` 生成运行包。当前镜像的应用工作目录已经改为 `/app/dts-opmanager`，宿主机 `/opt` 挂载不会再覆盖应用目录。

### `COPY target/dts-opmanager-*.jar ... no source files`

原因：直接运行了裸 `docker build`，但没有先生成 jar。

处理：

```bash
./build-image.sh --tag dts-opmanager:2.2.3 --output dts-opmanager-runtime-2.2.3-linux-arm64.tar.gz
```

### `Failed to start thread "GC Thread#0"`

原因：鲲鹏 / 麒麟 Docker 环境对 JVM 线程创建有限制，Maven 不能在普通 Dockerfile `RUN` 阶段可靠启动。

处理：使用 `build-image.sh`，不要把 Maven 放回 Dockerfile。

### `[opmanager-build] ERROR: 'mvn' not found in PATH`

原因：旧版脚本会误读主工程构建变量 `LEGACY_USE_HOST_MAVEN=1`，从而切到宿主机 Maven。现场或编译机没有安装 `mvn` 时就会失败。

处理：使用当前版本 `build-image.sh`。当前脚本只在显式传 `--host-maven` 或设置 `OPMANAGER_USE_HOST_MAVEN=1` 时才使用宿主机 Maven；默认使用 Maven 容器。

### 浏览器提示 JS MIME 类型是 `application/octet-stream`

原因通常是 jar 内静态资源缺失或资源 hash 不匹配。

处理：重新执行 `build-image.sh`，确保前端资源和 jar 一起重新生成。
