# Sprint-47 集成测试计划（dts-metrics 退役）

## 目标
证明 dts-metrics 安全退役、平台原生页无缝承接、可回退、无残留。

## 证据
| 证据 | 落点 | 状态 |
|------|------|------|
| 退役决策（依赖审计 + 数据处置 + 平价） | F1 决策书 | READY |
| 灰度切断观察（UI→API 分步无异常） | F2-T01 观察记录 | READY |
| 回退演练（恢复路由+服务） | F2-T03 runbook 演练 | READY |
| 平台依赖清理后回归绿 | F3 clean test | READY |
| 退役后全量回归 + grep 残留清零 | F4-T03 回归清单 | READY |

## 验收命令
```bash
# 部署侧
docker compose -f docker-compose-app.yml config   # 移除后校验合法、无 dts-metrics
# 平台侧
cd source && ./mvnw -pl dts-platform clean test
# 残留扫描
grep -rn "dts-metrics\|/api/metrics\|:8084" docker-compose*.yml source/dts-platform/src/main worklog 2>/dev/null  # 仅余归档/记忆说明
```

## 阻断条件（任一触发即不可 DONE）
- 平价未达（SP-3 未完成）却推进退役 → UX 倒退。
- 切断后 `/metrics`/`/api/metrics` 出现 503（重定向/收敛缺失）。
- 平台 service-auth 清理误伤其他内部调用方（既有安全测试回归）。
- 无回退预案/未保留回退窗口就移除服务。
- 删除 git 历史（应归档而非毁史）。

## 手测要点（现场，gated）
- 灰度切 `/metrics` UI 路由后旧链接重定向原生页。
- 切 `/api/metrics` 后平台语义建模端到端正常。
- 回退演练：恢复路由 + 重启 dts-metrics → 旧路径可用。
