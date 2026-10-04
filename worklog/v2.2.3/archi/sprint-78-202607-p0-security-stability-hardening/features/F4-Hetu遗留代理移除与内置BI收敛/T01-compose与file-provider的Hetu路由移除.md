# T01：compose 与 file provider 的 Hetu 路由移除

**优先级**：P0
**状态**：DONE（compose 双文件 + file provider 已移除并验证；docker-label 运行时清理属部署动作，见 it-06 证据第 6 节）
**依赖**：无

## 目标

`docker-compose-app.yml` 与 `services/dts-proxy/dynamic/traefik-dynamic.yml` 中的 hetu 代理面全部删除，配置校验通过。

## 技术设计（Contract-first）

- **输入契约**：无（静态配置变更）。
- **输出契约**：F4 契约表的"路由（移除后）"行为；`docker compose -f docker-compose-app.yml config` 无 hetu 相关内容且校验通过。
- **数据流**：无运行态数据流；Traefik 热加载 file provider，compose label 随重建生效。
- **错误路径**：误删非 hetu 路由 → `docker compose config` diff 评审兜底（G2 变更范围守卫）；file provider 语法错误 → Traefik 启动/热加载日志可见，本任务以静态校验 + 容器日志检查收尾。
- **复用点**：既有主 UI 路由规则（账本#13 的 1435 行）作为回落承载；`.bak.bi_yuzhicloud_20260412161329` 作为删除内容对照（账本#14）。
- **实现方案**：
  1. 删除 compose 1486-1543 的 9 组 `hetu-*` router label 及其 middleware label（`hetu-strip-*`、`hetu-upstream-host`、`hetu-allow-anonymous`，账本#13）。
  2. 删除 1482 行 `hetu.upstream` extra_hosts 条目。
  3. 清理 1435 行主 UI router 中为 hetu 让路的 8 个 `!PathPrefix` 排除项（保留 `/api`、`/admin/api`、`/analytics`、`/bi/api` 等非 hetu 排除）。
  4. traefik-dynamic.yml 删除 hetu service、9 组 `hetu-*-fallback` router、`hetu-*` middleware 段（账本#14 的 :10-185）；保留文件其余内容。
  5. 顺带定位 init.sh/.env 模板中 `HETU_UPSTREAM_IP` 生成点并标记 deprecated（sprint README 开放问题；不删客户 .env 既有值）。
- **禁止**：保留任何形式的"停用开关"（ADR-78-07）；改动与 hetu 无关的路由与 middleware。

## 影响范围

- `docker-compose-app.yml`（:1435、:1482、:1486-1543）
- `services/dts-proxy/dynamic/traefik-dynamic.yml`
- `init.sh` / `.env.example`（HETU_UPSTREAM_IP 退役标注）
- 如 dev/legacy compose 存在 hetu 引用，一并处理并记录（实施期一次 grep 确认范围）

## 验证（RED→GREEN）

- [ ] `grep -in "hetu" docker-compose-app.yml services/dts-proxy/dynamic/traefik-dynamic.yml` 无命中（.bak 除外）。
- [ ] `docker compose -f docker-compose-app.yml config` 校验通过；`git diff` 仅触及预期行。
- [ ] Traefik 容器日志无 file provider 配置错误。

## Definition of Done

- [ ] 架构：配置校验输出 + diff 评审
- [ ] UI：本任务无 UI（T02/T03 覆盖）
- [ ] 切片：证据入 `it/evidence/it-06-hetu-removal/`
- [ ] 无占位证据
