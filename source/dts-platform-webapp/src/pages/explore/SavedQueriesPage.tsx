import { useEffect, useState } from "react";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Textarea } from "@/ui/textarea";
import { toast } from "sonner";
import { createSavedQuery, deleteSavedQuery, listSavedQueries, runSavedQuery, listResultSets, previewResultSet, deleteResultSet, cleanupExpiredResultSets } from "@/api/platformApi";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "@/ui/dialog";

type SavedQuery = { id: string; name?: string; title?: string; sqlText?: string; datasetId?: string };

type ResultSet = { id: string; storageUri: string; columns: string; rowCount?: number; expiresAt?: string };

export default function SavedQueriesPage() {
  const [items, setItems] = useState<SavedQuery[]>([]);
  const [resultSets, setResultSets] = useState<ResultSet[]>([]);
  const [title, setTitle] = useState("");
  const [sql, setSql] = useState("SELECT 1 AS col_1");
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewHeaders, setPreviewHeaders] = useState<string[]>([]);
  const [previewRows, setPreviewRows] = useState<any[]>([]);
  const [previewMasking, setPreviewMasking] = useState<any>(null);

  const load = async () => {
    try {
      const data: any = await listSavedQueries();
      setItems(Array.isArray(data) ? data : []);
      const rs: any = await listResultSets();
      setResultSets(Array.isArray(rs) ? rs : []);
    } catch (e) {
      console.error(e);
      toast.error("加载保存的查询失败");
    }
  };

  useEffect(() => { load(); }, []);

  const handleCreate = async () => {
    if (!title.trim() || !sql.trim()) {
      toast.error("请输入标题和 SQL");
      return;
    }
    try {
      await createSavedQuery({ name: title, title, sqlText: sql });
      toast.success("已保存查询");
      setTitle("");
      setSql("SELECT 1 AS col_1");
      await load();
    } catch (e) {
      console.error(e);
      toast.error("保存失败");
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteSavedQuery(id);
      toast.success("已删除");
      await load();
    } catch (e) {
      console.error(e);
      toast.error("删除失败");
    }
  };

  const handleRun = async (id: string) => {
    try {
      await runSavedQuery(id);
      toast.success("已执行保存的查询（查看工作台结果）");
    } catch (e) {
      console.error(e);
      toast.error("执行失败");
    }
  };

  const handlePreview = async (id: string) => {
    try {
      const resp: any = await previewResultSet(id, 10);
      const headers: string[] = Array.isArray(resp?.headers) ? resp.headers : [];
      const rows: any[] = Array.isArray(resp?.rows) ? resp.rows : [];
      setPreviewHeaders(headers);
      setPreviewRows(rows);
      setPreviewMasking(resp?.masking ?? null);
      setPreviewOpen(true);
    } catch (e) {
      console.error(e);
      toast.error("预览失败");
    }
  };

  return (
    <>
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle className="text-base">新建保存的查询</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="space-y-1">
            <Label>标题</Label>
            <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="标题" />
          </div>
          <div className="space-y-1">
            <Label>SQL</Label>
            <Textarea rows={4} value={sql} onChange={(e) => setSql(e.target.value)} />
          </div>
          <div>
            <Button onClick={handleCreate}>保存</Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">已保存的查询</CardTitle>
        </CardHeader>
        <CardContent>
          {items.length ? (
            <div className="overflow-hidden rounded-md border">
              <table className="w-full border-collapse text-sm">
                <thead className="bg-muted/50">
                  <tr>
                    <th className="border-b px-3 py-2 text-left font-medium">名称</th>
                    <th className="border-b px-3 py-2 text-left font-medium">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((it) => (
                    <tr key={it.id} className="border-b last:border-b-0">
                      <td className="px-3 py-2">{it.title || it.name || it.id}</td>
                      <td className="px-3 py-2 space-x-2">
                        <Button size="sm" variant="outline" onClick={() => handleRun(it.id)}>执行</Button>
                        <Button size="sm" variant="ghost" onClick={() => handleDelete(it.id)}>删除</Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="text-sm text-muted-foreground">暂无保存的查询</div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle className="text-base">结果集管理</CardTitle>
            <div className="flex items-center gap-2">
              <Button variant="outline" size="sm" onClick={async () => { try { await load(); toast.success("已刷新"); } catch {} }}>刷新</Button>
              <Button size="sm" onClick={async () => {
                try {
                  const r: any = await cleanupExpiredResultSets();
                  toast.success(`已清理 ${r?.deleted ?? 0} 条过期结果集`);
                  await load();
                } catch (e) {
                  console.error(e);
                  toast.error("清理失败");
                }
              }}>清理过期</Button>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {resultSets.length ? (
            <div className="overflow-hidden rounded-md border">
              <table className="w-full border-collapse text-sm">
                <thead className="bg-muted/50">
                  <tr>
                    <th className="border-b px-3 py-2 text-left font-medium">ID</th>
                    <th className="border-b px-3 py-2 text-left font-medium">列</th>
                    <th className="border-b px-3 py-2 text-left font-medium">行数</th>
                    <th className="border-b px-3 py-2 text-left font-medium">过期时间</th>
                    <th className="border-b px-3 py-2 text-left font-medium">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {resultSets.map((rs) => (
                    <tr key={rs.id} className="border-b last:border-b-0">
                      <td className="px-3 py-2">{rs.id}</td>
                      <td className="px-3 py-2">{rs.columns}</td>
                      <td className="px-3 py-2">{rs.rowCount ?? "-"}</td>
                      <td className="px-3 py-2">{rs.expiresAt ?? "-"}</td>
                      <td className="px-3 py-2 space-x-2">
                        <Button size="sm" variant="outline" onClick={() => handlePreview(rs.id)}>预览</Button>
                        <Button size="sm" variant="ghost" onClick={async () => {
                          try {
                            await deleteResultSet(rs.id);
                            toast.success("已删除结果集");
                            await load();
                          } catch (e) {
                            console.error(e);
                            toast.error("删除失败");
                          }
                        }}>删除</Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="text-sm text-muted-foreground">暂无结果集</div>
          )}
        </CardContent>
      </Card>
    </div>
    <Dialog open={previewOpen} onOpenChange={setPreviewOpen}>
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <DialogTitle>结果集预览</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          {previewMasking ? (
            <div className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-700">
              {previewMasking.mode === "policy" ? (
                <div>
                  <p className="font-medium">按策略脱敏</p>
                  {Array.isArray(previewMasking.rules) && previewMasking.rules.length ? (
                    <p>规则：{previewMasking.rules.map((r: any) => `${r.column}:${r.fn}`).join(", ")}</p>
                  ) : null}
                  {previewMasking.default ? <p>默认：{previewMasking.default}</p> : null}
                </div>
              ) : (
                <div>
                  <p className="font-medium">启发式脱敏</p>
                  {Array.isArray(previewMasking.columns) && previewMasking.columns.length ? (
                    <p>列：{previewMasking.columns.join(", ")}</p>
                  ) : (
                    <p>未识别到敏感列</p>
                  )}
                </div>
              )}
            </div>
          ) : null}
          <div className="overflow-auto rounded-md border">
            <table className="w-full border-collapse text-xs">
              <thead className="bg-muted/50">
                <tr>
                  {previewHeaders.map((h) => (
                    <th key={h} className="border-b px-2 py-1 text-left font-medium">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {previewRows.map((row, i) => (
                  <tr key={`r-${i}`} className="border-b last:border-b-0">
                    {previewHeaders.map((h) => (
                      <td key={h} className="px-2 py-1">{String(row[h] ?? "")}</td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => setPreviewOpen(false)}>关闭</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
    </>
  );
}
