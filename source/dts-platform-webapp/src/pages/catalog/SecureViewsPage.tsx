import { useEffect, useState } from "react";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { toast } from "sonner";
import { listDatasets, previewSecurityViews, applyPolicy, listSecurityViews, rebuildSecureView } from "@/api/platformApi";

type DatasetLite = { id: string; name: string };

export default function SecureViewsPage() {
  const [datasets, setDatasets] = useState<DatasetLite[]>([]);
  const [datasetId, setDatasetId] = useState<string>("");
  const [preview, setPreview] = useState<Record<string, string> | null>(null);
  const [busy, setBusy] = useState(false);
  const [views, setViews] = useState<any[]>([]);

  const fetchDatasets = async () => {
    try {
      const resp = (await listDatasets({ page: 0, size: 100 })) as any;
      const content = (resp?.content ?? []).map((it: any) => ({ id: String(it.id), name: String(it.name) }));
      setDatasets(content);
      if (!datasetId && content.length) setDatasetId(content[0].id);
    } catch (e) {
      console.error(e);
      toast.error("加载数据集失败");
    }
  };

  useEffect(() => {
    void fetchDatasets();
  }, []);

  const onPreview = async () => {
    if (!datasetId) return;
    try {
      const data = (await previewSecurityViews(datasetId)) as any;
      setPreview(data || {});
      // also refresh current views from backend
      try {
        const list = (await listSecurityViews(datasetId)) as any[];
        setViews(list || []);
      } catch (e) {
        console.warn("list security views failed", e);
      }
    } catch (e) {
      console.error(e);
      toast.error("预览失败");
    }
  };

  const onGenerate = async () => {
    setBusy(true);
    try {
      await applyPolicy(datasetId);
      await onPreview();
      toast.success("已生成/更新安全视图");
    } finally {
      setBusy(false);
    }
  };

  const onRollback = async () => {
    setBusy(true);
    try {
      setPreview(null);
      toast.success("已回滚安全视图（模拟）");
    } finally {
      setBusy(false);
    }
  };

  const onRebuild = async () => {
    setBusy(true);
    try {
      await onPreview();
      toast.success("已一键重建（模拟，仅刷新 SQL）");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <CardTitle className="text-base">安全视图编排</CardTitle>
          <div className="flex items-center gap-2">
            <Label className="text-sm">数据集</Label>
            <Select value={datasetId} onValueChange={setDatasetId}>
              <SelectTrigger className="w-[260px]">
                <SelectValue placeholder="选择数据集" />
              </SelectTrigger>
              <SelectContent>
                {datasets.map((d) => (
                  <SelectItem key={d.id} value={d.id}>
                    {d.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button variant="outline" onClick={() => void fetchDatasets()}>刷新</Button>
            <Button onClick={() => void onPreview()} disabled={!datasetId}>
              预览 SQL
            </Button>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex flex-wrap items-center gap-2">
            <Button onClick={() => void onGenerate()} disabled={!datasetId || busy}>
              生成/更新视图
            </Button>
            <Button variant="outline" onClick={() => void onRollback()} disabled={!datasetId || busy}>
              回滚视图
            </Button>
            <Button variant="secondary" onClick={() => void onRebuild()} disabled={!datasetId || busy}>
              一键重建
            </Button>
          </div>

          {preview && (
            <div className="grid gap-3 md:grid-cols-2">
              {Object.entries(preview).map(([k, v]) => (
                <div key={k} className="rounded border bg-muted/30 p-2">
                  <div className="mb-1 text-xs font-semibold">{k}</div>
                  <pre className="whitespace-pre-wrap text-xs">{String(v)}</pre>
                </div>
              ))}
            </div>
          )}

          {views.length > 0 && (
            <div className="mt-4">
              <div className="mb-2 text-sm font-medium">已生成安全视图</div>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[720px] table-fixed border-collapse text-sm">
                  <thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
                    <tr>
                      <th className="px-3 py-2">ID</th>
                      <th className="px-3 py-2">视图名</th>
                      <th className="px-3 py-2">级别</th>
                      <th className="px-3 py-2">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {views.map((v) => (
                      <tr key={v.id} className="border-b last:border-b-0">
                        <td className="px-3 py-2 text-xs text-muted-foreground">{v.id}</td>
                        <td className="px-3 py-2">{v.viewName}</td>
                        <td className="px-3 py-2 text-xs">{v.level}</td>
                        <td className="px-3 py-2">
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={async () => {
                              try {
                                await rebuildSecureView(v.id);
                                toast.success("已重建视图：" + v.viewName);
                              } catch (e) {
                                console.error(e);
                                toast.error("重建失败");
                              }
                            }}
                          >
                            重建
                          </Button>
                        </td>
                      </tr>)
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
