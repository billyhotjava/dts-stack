# T02: IndicatorRunTracker 采集服务

**优先级**: P1
**状态**: READY
**依赖**: T01

## 目标
dbt run 完成后，自动采集 ind_* 模型的计算结果写入 gov_indicator_run。

## 技术设计

### 采集入口
在现有 `DbtRunResultService.syncFromRunResults()` 执行完毕后，调用 IndicatorRunTracker：

```java
@Service
public class IndicatorRunTracker {
    
    /**
     * dbt run 完成后调用
     * @param dbtRunId dbt 运行 ID
     */
    public void captureResults(String dbtRunId) {
        // 1. 查找所有 status=COMMITTED/PUBLISHED 的指标定义
        List<GovIndicatorDefinition> indicators = indicatorRepo
            .findByStatusIn(List.of("COMMITTED", "PUBLISHED"));
        
        for (GovIndicatorDefinition def : indicators) {
            try {
                captureOne(def, dbtRunId);
            } catch (Exception e) {
                log.warn("Failed to capture indicator {}: {}", def.getCode(), e.getMessage());
            }
        }
    }
    
    private void captureOne(GovIndicatorDefinition def, String dbtRunId) {
        // 1. 查询 ADS 表获取最新聚合值
        String sql = buildAggregateQuery(def);
        BigDecimal currentValue = queryValue(sql);
        
        // 2. 获取上次值
        Optional<GovIndicatorRun> lastRun = runRepo
            .findTopByIndicatorIdOrderByRunAtDesc(def.getId());
        BigDecimal previousValue = lastRun.map(GovIndicatorRun::getComputedValue).orElse(null);
        
        // 3. 计算变化率
        BigDecimal changeRate = computeChangeRate(currentValue, previousValue);
        
        // 4. 写入 run 记录
        GovIndicatorRun run = new GovIndicatorRun();
        run.setIndicatorId(def.getId());
        run.setStatus("SUCCESS");
        run.setComputedValue(currentValue);
        run.setPreviousValue(previousValue);
        run.setChangeRate(changeRate);
        run.setDbtRunId(dbtRunId);
        
        runRepo.save(run);
    }
    
    private String buildAggregateQuery(GovIndicatorDefinition def) {
        // 从 ADS 表查最新周期的聚合值
        // SELECT {code} FROM ind_{code} ORDER BY report_period DESC LIMIT 1
        // 或 SELECT AVG({code}) FROM ind_{code} WHERE report_period = (SELECT MAX(...))
    }
}
```

### 钩子接入点
修改 `DbtRunResultService` 或 `EtlResource` 中 dbt run 完成回调处，调用 `indicatorRunTracker.captureResults(dbtRunId)`。

## 影响范围
| 文件 | 改动 |
|------|------|
| 新增 `IndicatorRunTracker.java` | 采集服务 |
| 修改 `DbtRunResultService.java` | 新增钩子调用 |

## 验证
- [ ] dbt run 后自动生成 gov_indicator_run 记录
- [ ] computed_value 正确
- [ ] change_rate 计算正确
