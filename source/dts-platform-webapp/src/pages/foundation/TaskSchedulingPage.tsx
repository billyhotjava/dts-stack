import { useState } from "react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Button } from "@/ui/button";
import { submitEtlJob, getJobRunStatus } from "@/api/platformApi";

export default function TaskSchedulingPage() {
  const [jobId, setJobId] = useState("");
  const [runId, setRunId] = useState("");
  const [lastRun, setLastRun] = useState<any | null>(null);

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle className="text-base">任务调度</CardTitle>
          <CardDescription>此页面暂为占位，后续可接入 Airflow/DolphinScheduler 等。</CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground mb-4">
            支持：任务编排、依赖管理、定时/事件触发、告警与重试策略、运行日志、历史版本、DAG 可视化。
          </p>
          <div className="grid gap-2 md:grid-cols-[1fr,auto]">
            <Input placeholder="输入ETL作业编号（UUID）" value={jobId} onChange={(e) => setJobId(e.target.value)} />
            <Button onClick={async () => {
              if (!jobId) return;
              const r = (await submitEtlJob(jobId)) as any;
              setRunId(String(r.id || ""));
              setLastRun(r);
            }}>登记提交</Button>
          </div>
          <div className="mt-3 grid gap-2 md:grid-cols-[1fr,auto]">
            <Input placeholder="输入运行编号 查询状态" value={runId} onChange={(e) => setRunId(e.target.value)} />
            <Button variant="outline" onClick={async () => {
              if (!runId) return;
              const r = (await getJobRunStatus(runId)) as any;
              setLastRun(r);
            }}>查询状态</Button>
          </div>
          {lastRun && (
            <div className="mt-4 rounded border bg-muted/30 p-2 text-xs">
              <div className="mb-1 font-semibold">运行信息</div>
              <pre className="whitespace-pre-wrap">{JSON.stringify(lastRun, null, 2)}</pre>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
