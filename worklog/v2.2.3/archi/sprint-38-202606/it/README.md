# Sprint-38 集成测试

**状态**: IN_PROGRESS（用例已定义，部分证据已补充）

## 环境

- mock API server：WireMock 容器（compose 编入测试 profile），预置多资源/4 种分页/4 种鉴权/429/5xx/超大响应桩
- 测试库：独立 PG schema，不动现网 ODS

## 用例矩阵

| ID | 场景 | 覆盖 | 证据 |
|----|------|------|------|
| IT-00 | API 契约运行态：真实 dts-ingestion 返回 contractVersion=1.2.0，authProviders.enabled 与 connector capability 口径一致 | F1-T01/F1-T03 | `evidence/api-contract-live-20260612.txt` |
| IT-01 | 端到端主链路：建 API 数据源→建任务→Airflow 触发→执行器抓取→raw 落 ODS→checkpoint 推进→第二轮增量只取新数据 | F1/F2/F3 | `evidence/api-end-to-end-20260612.txt` |
| IT-02 | 密钥安全：DAG 文件/容器 env/Airflow Variable/应用日志四处断言无明文凭据；GET 数据源接口不回显 | F1-T02 | `evidence/api-secret-security-20260612.txt` |
| IT-03 | 鉴权矩阵：bearer/apikey(header+query)/basic/OAuth2(含 token 过期刷新、401 重取) | F2-T03 | 部分：`evidence/api-test-connection-mock-api-20260612.txt` 覆盖连接测试 bearer 成功与 401 AUTH 分类 |
| IT-04 | 分页矩阵：page/offset/token/nextUrl/Link 头各翻 3+ 页到尾；不足页不提前停 | F2-T04 | 待补 |
| IT-05 | 游标语义：数值/datetime 游标推进；lookback 重叠无重复行；backfill 不动 checkpoint | F2-T04/T05, F3-T03 | 部分：`evidence/api-raw-landing-idempotency-20260612.txt` 覆盖 raw 同批重放零重复 |
| IT-06 | 容错：资源 A 失败资源 B 提交；429 退避；超大响应截断分类 DATA_QUALITY；失败自动重试拾取 | F2-T05, F3-T02 | 待补 |
| IT-07 | SSRF/TLS：私网 next_url 拦截；自签证书默认拒、配 CA 通过 | F2-T06 | 待补 |
| IT-08 | 旧路径迁移：存量 API 任务 DAG 重生成、checkpoint 衔接、env 引用 0 残留 | F5-T02 | `evidence/api-dag-migration-20260612.txt` |
| IT-09 | 回归：JDBC 与文件入湖各跑一轮，行为无变化 | 全局 | `evidence/jdbc-file-regression-20260612.txt` |
| IT-10 | 前端 E2E：数据源创建(敏感不回显)→任务向导→执行详情展示 | F4 | 待补 |

## 规则

- 每条用例完成后在本表登记证据路径（截图/日志/SQL 输出存本目录）
- IT-01/02/09 为 sprint DONE 的硬门槛
