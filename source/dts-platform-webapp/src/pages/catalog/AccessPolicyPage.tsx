import { useEffect, useMemo, useState } from "react";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Textarea } from "@/ui/textarea";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/ui/tabs";
import { toast } from "sonner";
import {
  getAccessPolicy,
  listDatasets,
  previewMasking,
  previewSecurityViews,
  upsertAccessPolicy,
  getEffectivePolicy,
  applyPolicy,
} from "@/api/platformApi";

const MASKING = ["NONE", "PARTIAL", "HASH", "TOKENIZE", "CUSTOM"] as const;
type MaskingType = (typeof MASKING)[number];

type DatasetLite = { id: string; name: string };

export default function AccessPolicyPage() {
  const [datasets, setDatasets] = useState<DatasetLite[]>([]);
  const [datasetId, setDatasetId] = useState<string>("");
  const [allowRoles, setAllowRoles] = useState<string>("ROLE_PUBLIC,ROLE_INTERNAL");
  const [rowFilter, setRowFilter] = useState<string>("");
  const [defaultMasking, setDefaultMasking] = useState<MaskingType>("NONE");
  const [loading, setLoading] = useState(false);

  // Preview inputs
  const [previewValue, setPreviewValue] = useState("13812345678");
  const [previewStrategy, setPreviewStrategy] = useState<MaskingType>("PARTIAL");
  const [previewMasked, setPreviewMasked] = useState<string>("");
  const [viewsPreview, setViewsPreview] = useState<Record<string, string> | null>(null);
  const [effective, setEffective] = useState<Record<string, any> | null>(null);
  const [applying, setApplying] = useState(false);

  const canSave = useMemo(() => Boolean(datasetId), [datasetId]);

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

  const fetchPolicy = async (id: string) => {
    if (!id) return;
    setLoading(true);
    try {
      const p = (await getAccessPolicy(id)) as any;
      if (p) {
        setAllowRoles(String(p.allowRoles ?? ""));
        setRowFilter(String(p.rowFilter ?? ""));
        setDefaultMasking((p.defaultMasking ?? "NONE") as MaskingType);
      }
    } catch (e) {
      console.error(e);
      toast.error("加载策略失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void fetchDatasets();
  }, []);

  useEffect(() => {
    if (datasetId) void fetchPolicy(datasetId);
  }, [datasetId]);

  const onSave = async () => {
    if (!datasetId) return;
    try {
      await upsertAccessPolicy(datasetId, { allowRoles, rowFilter, defaultMasking });
      toast.success("已保存策略");
    } catch (e) {
      console.error(e);
      toast.error("保存失败");
    }
  };

  const onPreviewMask = async () => {
    try {
      const r = (await previewMasking({ value: previewValue, function: previewStrategy })) as any;
      setPreviewMasked(String(r?.output ?? r?.masked ?? ""));
    } catch (e) {
      console.error(e);
      toast.error("预览失败");
    }
  };

  const onPreviewViews = async () => {
    if (!datasetId) return;
    try {
      const vv = (await previewSecurityViews(datasetId)) as any;
      setViewsPreview(vv || {});
      toast.success("已生成视图预览");
    } catch (e) {
      console.error(e);
      toast.error("视图预览失败");
    }
  };

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <CardTitle className="text-base">分级保护与访问策略</CardTitle>
          <div className="flex flex-wrap items-center gap-2">
            <Select value={datasetId} onValueChange={setDatasetId}>
              <SelectTrigger className="w-[240px]">
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
            <Button variant="outline" onClick={() => void fetchDatasets()}>
              刷新
            </Button>
            <Button onClick={onSave} disabled={!canSave || loading}>
              保存
            </Button>
            <Button
              variant="outline"
              onClick={async () => {
                if (!datasetId) return;
                try {
                  const eff = (await getEffectivePolicy(datasetId)) as any;
                  setEffective(eff || null);
                } catch (e) {
                  console.error(e);
                  toast.error("获取有效策略失败");
                }
              }}
              disabled={!datasetId}
            >
              查看有效策略
            </Button>
            <Button
              onClick={async () => {
                if (!datasetId) return;
                setApplying(true);
                try {
                  await applyPolicy(datasetId);
                  toast.success("策略已生效");
                } catch (e) {
                  console.error(e);
                  toast.error("生效失败");
                } finally {
                  setApplying(false);
                }
              }}
              disabled={!datasetId || applying}
            >
              {applying ? "生效中…" : "策略生效"}
            </Button>
          </div>
        </CardHeader>
        <CardContent className="grid gap-4 md:grid-cols-2">
          <div className="grid gap-3">
            <div className="grid gap-2">
              <Label>允许角色（Keycloak 角色，逗号分隔）</Label>
              <Input value={allowRoles} onChange={(e) => setAllowRoles(e.target.value)} />
            </div>
            <div className="grid gap-2">
              <Label>行级过滤表达式（RLS）</Label>
              <Textarea value={rowFilter} onChange={(e) => setRowFilter(e.target.value)} rows={4} />
            </div>
            <div className="grid gap-2">
              <Label>默认兜底策略</Label>
              <Select value={defaultMasking} onValueChange={(v) => setDefaultMasking(v as MaskingType)}>
                <SelectTrigger className="w-[200px]">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {MASKING.map((m) => (
                    <SelectItem key={m} value={m}>
                      {m}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          <div className="grid gap-3">
            <Tabs defaultValue="api">
              <TabsList>
                <TabsTrigger value="api">接口层校验</TabsTrigger>
                <TabsTrigger value="view">视图层校验</TabsTrigger>
              </TabsList>
              <TabsContent value="api" className="space-y-3 pt-2">
                <div className="grid gap-2">
                  <Label>策略</Label>
                  <Select value={previewStrategy} onValueChange={(v) => setPreviewStrategy(v as MaskingType)}>
                    <SelectTrigger className="w-[200px]">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {MASKING.map((m) => (
                        <SelectItem key={m} value={m}>
                          {m}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="grid gap-2">
                  <Label>样例值</Label>
                  <Input value={previewValue} onChange={(e) => setPreviewValue(e.target.value)} />
                </div>
                <div className="flex items-center gap-2">
                  <Button variant="outline" onClick={() => setPreviewMasked("")}>清空</Button>
                  <Button onClick={() => void onPreviewMask()}>预览脱敏</Button>
                </div>
                {previewMasked && (
                  <div className="rounded border bg-muted/30 p-2 text-sm">
                    结果：{previewMasked}
                  </div>
                )}
              </TabsContent>

              <TabsContent value="view" className="space-y-3 pt-2">
                <div className="flex items-center gap-2">
                  <Button onClick={() => void onPreviewViews()} disabled={!datasetId}>
                    生成四档视图 SQL
                  </Button>
                </div>
                {viewsPreview && (
                  <div className="grid gap-3 md:grid-cols-2">
                    {Object.entries(viewsPreview).map(([k, v]) => (
                      <div key={k} className="rounded border bg-muted/30 p-2">
                        <div className="mb-1 text-xs font-semibold">{k}</div>
                        <pre className="whitespace-pre-wrap text-xs">{String(v)}</pre>
                      </div>
                    ))}
                  </div>
                )}
              </TabsContent>
            </Tabs>
          </div>
        </CardContent>
      </Card>

      {effective && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Effective Policy</CardTitle>
          </CardHeader>
          <CardContent>
            <pre className="text-xs whitespace-pre-wrap">{JSON.stringify(effective, null, 2)}</pre>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
