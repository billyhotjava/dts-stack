# F4 性能诊断与验证记录

## 修复前现场（2026-09-08）
- 源码/部署checkout：200812f71094a78020d866a79ee9257be7ba02d4，开发目录无改动；deploy存在既有未跟踪交付/验收文件，保持不动。
- 平台日志18:07–18:09反复 Hikari total=10, active=10, idle=0，30000ms超时，waiting=9–14；DefaultDestinationSyncService同步失败，调度任务也出现连接超时。
- PG读取pg_stat_activity：8条idle in transaction约34秒，最后语句为modeling_warehouse_plan owner/department查询；并非已证明数据库锁等待。
- CPU快照 platform=1.14%，PG=18.02%；不据此泛化全部时段资源使用。
- CUA实际读取已登录 https://bi.yuzhicloud.com/#/data-modeling/dimensions/workbench：17条，首屏10行，物化/目录/分析三列均读取失败。
- G0沿用既有正式Maven容器及deploy Vitest入口；浏览器/实例/真实样本可访问。当前浏览器不是Chrome95证明。

## 验收登记
| IT | 内容 | 状态 |
|---|---|---|
| IT-25 | 聚合事务及身份回归 | PENDING |
| IT-26 | 限流/部分失败/刷新/过期响应 | PENDING |
| IT-27 | 首屏分阶段加载 | PENDING |
| IT-28 | 正式构建/制品/部署/运行时延/Chrome95 | PENDING |

原始诊断日志临时保存在/tmp/dts-modeling-performance-platform.log，不提交含请求身份的全量日志；以上保留最小必要事实。
