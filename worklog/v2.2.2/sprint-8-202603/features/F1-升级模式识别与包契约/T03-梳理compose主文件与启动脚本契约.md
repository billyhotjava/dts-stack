# T03: 梳理 compose 主文件与启动脚本契约

**优先级**: P1
**状态**: DONE
**依赖**: T01

## 目标
厘清 `init.sh`、`start.sh`、`stop.sh`、`dev-up.sh` 与各 compose 文件的真实关系，为升级器决定启动/停机策略。

## 技术设计
- 识别顺序固定为：
  - 先读取旧目录 `.env`
  - 若 `LEGACY_STACK=true`，直接进入 legacy 分支
  - 否则按 `DEPLOY_MODE` 进入 normal 分支
- compose 主文件选择规则：
  - legacy：运行态主文件固定为 `docker-compose.legacy.yml`
  - normal-single：`docker-compose.yml` + `docker-compose-app.yml`
  - 其他 `ha2/cluster` 先保留为后续扩展，不纳入首批自动升级
- 脚本契约结论：
  - `init.sh` 已正确识别 `LEGACY_STACK` 并切换到 `docker-compose.legacy.yml`
  - `start.sh` / `stop.sh` 只按 `DEPLOY_MODE` 选择 compose，不识别 `LEGACY_STACK`
  - 升级器不能直接套用 `start.sh` / `stop.sh` 的默认模式推断
- 升级器执行策略：
  - 停机校验前先输出识别到的运行模式和 compose 主文件
  - 日志和 summary 必须记录本次升级实际使用的 compose 文件
  - legacy 分支不做 `base + app -> legacy` 的现场重组，只合并现有 `docker-compose.legacy.yml`

## 影响范围
- `init.sh`
- `start.sh`
- `stop.sh`
- `docker-compose.yml`
- `docker-compose-app.yml`
- `docker-compose.legacy.yml`
- 升级 runbook
- 现场操作手册

## 验证
- [ ] 形成脚本与 compose 关系图
- [ ] legacy/normal 的 compose 选择规则写入 runbook
- [ ] 升级器设计不再依赖 `start.sh` / `stop.sh` 的错误默认行为

## 完成标准
- [ ] 升级器不再依赖错误的 compose 假设
- [ ] legacy 模式和 normal 模式的 compose 主文件选择有明确门禁
