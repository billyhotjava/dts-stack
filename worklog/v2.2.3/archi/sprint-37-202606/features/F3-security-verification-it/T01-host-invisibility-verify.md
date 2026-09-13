# T01: root 不可见 + 磁盘零明文残留 实测

**优先级**: P0
**状态**: READY
**依赖**: F1, F2

## 目标

以 root 视角实测：宿主机文件系统目录无法查看上传文件明文；作业执行前/中/后磁盘均无明文 Excel/CSV。

## 测试先行（RED — 验收脚本即测试）

- 上传一个含已知敏感关键字的 xlsx，触发入湖。
- 断言 A（静态）：`sudo cat {STACK_ROOT}/services/dts-airflow/dags/uploads/*.enc` 输出为二进制密文，`strings` 不含已知关键字、不含 `PK\x03\x04`。
- 断言 B（无明文）：`sudo grep -rl "<已知关键字>" {STACK_ROOT}/services/dts-airflow/dags/` 作业前/中/后均无命中（明文不落 bind 树）。
- 断言 C（密文性）：`.enc` 保留 `o+r`（Addax 跨 UID 读密文所需）；安全由密文性保证（断言 A/B），不依赖权限收紧。
- 断言 D（运行期）：作业执行窗口内，`sudo ls {STACK_ROOT}/.../uploads` 仍只有 `.enc`；明文仅在 Addax 容器 tmpfs（`docker exec <addax> ls $TMPDIR` 可见，宿主机磁盘不可见）。
- 断言 E（上传服务容器临时目录 — **Sprint-37 H1 修复验证，旧实现的盲点**）：上传 Excel/CSV 必先经 **dts-platform（入湖上传第一跳代理 `IngestionTaskProxyResource /files/upload*`）→ dts-ingestion** 两容器的 Spring/Tomcat multipart 处理与 POI 解析；早期实现因未配 `file-size-threshold` 且容器无 tmpfs，明文会落入容器可写层（宿主机 overlay2 `diff/`）。本断言对**两个容器**实测：
  - **tmpfs 就位**：`docker exec dts-platform sh -lc 'mount | grep " /apptmp "'` 与 dts-ingestion 同样确认 `/apptmp` 为 `tmpfs`（非容器可写层）；两容器 `JAVA_TOOL_OPTIONS` 含 `-Djava.io.tmpdir=/apptmp`。
  - **overlay 零明文**：上传进行中，`m=$(docker inspect -f '{{.GraphDriver.Data.UpperDir}}' <容器>)`，`sudo find "$m" -path '*tmp*' -newermt '-60 seconds'` 对两容器均无上传明文命中；`sudo grep -rl "<已知关键字>" "$m"` 两容器 overlay diff 层均无命中（旧实现会在 Tomcat multipart 临时文件处命中）。

## 技术设计（验证手段）

- 编写 `it/verify-host-invisibility.sh`：自动上传→触发→轮询作业→三阶段扫描磁盘→出 PASS/FAIL。
- 在真实 compose 跑（优先鲲鹏/麒麟）；证据（终端输出、扫描结果）归档 `it/evidence/host-invisibility/`。

## 影响范围

- `worklog/v2.2.3/sprint-37-202606/it/verify-host-invisibility.sh`（新增）
- `worklog/v2.2.3/sprint-37-202606/it/evidence/host-invisibility/`

## 验证

- [ ] 宿主机 root 仅见密文，`strings`/`grep` 无明文关键字。
- [ ] 作业前/中/后 bind 树零明文。
- [ ] `.enc` 为密文（strings 无明文关键字），权限策略不影响安全。
- [ ] **（H1）dts-platform 与 dts-ingestion 的 `/apptmp` 均为 tmpfs，上传窗口内两容器 overlay diff 层零明文（断言 E）。**

## 完成标准

- [ ] 「宿主机目录（含 root）不可见明文」目标实测达成。
