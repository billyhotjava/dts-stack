# Sprint-23 发布与回滚 Runbook

## 发布前检查
- 确认当前版本分支为 `v2.2.3`
- 确认 `platform`、`analytics`、`platform-webapp`、`admin-webapp` 最新构建通过
- 确认 `portal_sessions` 迁移脚本已在目标环境评估
- 准备 1 个同浏览器双 tab 验证账号
- 准备 1 个跨浏览器接管验证账号

## 发布窗口观测项
- `platform`:
  - 登录成功率
  - `/api/session/current` 2xx 比例
  - `session_taken_over` / `session_expired` 数量
- `analytics`:
  - `/bi/api/screens/*` 401 比例
  - bearer fallback 告警次数
- `proxy`:
  - `/api/forward-auth` 401/5xx 比例
- 浏览器:
  - 是否出现闪屏
  - 是否出现“异地登录”误报
  - 大屏 5 到 6 分钟附近是否开始退化

## 发布后 5 分钟检查
- 执行手工清单中的场景 A
- 执行手工清单中的场景 D
- 检查关键日志是否可串联同一请求链路

## 发布后 30 分钟检查
- 执行手工清单中的场景 C
- 确认无 401 风暴
- 确认无持续 redirect 风暴

## 触发回滚条件
- 同浏览器双 tab 无法稳定共存
- 大屏预览再次出现 5 到 6 分钟退化
- `/bi/api/screens/*` 出现持续 401 风暴
- 发布后普遍出现登录页闪屏或无限跳转

## 回滚步骤
1. 停止继续灰度或全量。
2. 回退 `platform`、`analytics`、`platform-webapp`、`admin-webapp` 到上一个稳定镜像。
3. 恢复旧版代理配置。
4. 清理本次发布产生的异常浏览器会话。
5. 重新执行场景 A 和场景 C，确认故障消失。

## 故障定位顺序
1. 浏览器 Network 先看 `/api/session/current`
2. 再看 `/api/forward-auth`
3. 再看 `/bi/api/screens/:id`
4. 最后对照 `platform` 和 `analytics` 日志

## 证据归档
- 手工验证记录: `assets/manual-verification-checklist.md`
- 发布记录: `assets/release-*.md`
- 日志摘录: `assets/logs-*.md`
- HAR: `assets/network-*.har`
