# 性能与稳定性探针

**日期**：2026-07-27  
**环境**：本机 compose，真实认证，HTTPS，经 proxy → dts-platform → PostgreSQL  
**样本**：APPLICATION `42fe5aa7-7d89-46fd-88b9-4baae9b314a4`  
**次数**：每个端点 100 次串行请求

| API | HTTP 200 | min | median | P95 | max | mean | 预算 |
|---|---:|---:|---:|---:|---:|---:|---:|
| `GET /api/modeling/model-specs/{id}` | 100/100 | 11.012ms | 12.934ms | 16.562ms | 28.699ms | 13.472ms | P95 ≤ 1s |
| `GET /api/modeling/model-specs/{id}/stage-gates` | 100/100 | 14.049ms | 19.096ms | 24.094ms | 28.175ms | 19.075ms | P95 ≤ 1s |

结论：PASS。探针只记录状态与时延，不保存 Cookie 或响应中的业务数据。
