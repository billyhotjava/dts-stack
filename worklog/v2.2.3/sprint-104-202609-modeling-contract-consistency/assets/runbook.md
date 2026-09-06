# Sprint-104 复合粒度键 dbt 验证运行手册

## 范围

本手册覆盖 `dts-platform` 的工作区引导宏及 `dts-platform-webapp` 的正式交付范围。`dts-dbt:1.10.0` 仅是一次性专项测试运行时，不是本项构建或拟部署服务。它不创建常驻服务、不覆写业务表，也不替代模型发布或物化验收。

## 前置检查

在 `/opt/prod/s10/deploy` 执行，并只使用已提交、已拉取的源码：

```bash
git pull --ff-only
git rev-parse HEAD
docker image inspect dts-dbt:1.10.0 --format '{{.Id}}'
test -f data/sprint104-acceptance/profiles/profiles.yml
```

当前正式构建候选 SHA 为 `f14830709`；执行前必须以实际 `git rev-parse HEAD` 复核。记录实际提交、镜像完整 ID 和运行时间到本任务的验收记录。不要在命令输出、日志或证据文件打印数据库密码。

## 执行

从受控测试配置向当前 shell 注入 `SPRINT104_DBT_USER` 与 `SPRINT104_DBT_PASSWORD` 后，运行一次性容器。凭据仅作为环境变量传递，不写入文件。

```bash
docker run --rm --network host --entrypoint python3 \
  -e SPRINT104_DBT_USER -e SPRINT104_DBT_PASSWORD \
  -v /opt/prod/s10/deploy:/repo:ro \
  -v /opt/prod/s10/deploy/data/sprint104-acceptance:/evidence \
  dts-dbt:1.10.0 \
  /repo/worklog/v2.2.3/sprint-104-202609-modeling-contract-consistency/it/run-composite-dbt.py \
  --profiles-dir /evidence/profiles --profile dts_sprint104 --target acceptance \
  --source-file /repo/source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtConfigService.java \
  --evidence /evidence/T04-composite-dbt.json
```

脚本退出码 `0` 表示五个预期断言均符合；`2` 表示前置条件缺失；`3` 表示结果与预期不符；`4` 表示证据写入失败。每次执行前删除或另存旧证据，避免混淆记录。

## 判定与停止条件

证据 JSON 必须同时显示：两个合法键顺序为 `0`，两个重复组合和一个空键样例为 `1`。凭据缺失、镜像或提交不符、宏来源摘要变化、或任何结果不符时停止，不将结果标记为发布验收，并转交相应源码或部署负责人处理。
