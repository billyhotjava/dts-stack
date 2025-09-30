import { useState } from "react";
import { Icon } from "@/components/icon";
import { Button } from "@/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Textarea } from "@/ui/textarea";
import { Switch } from "@/ui/switch";

export default function DataStoragePage() {
  const [nameservices, setNameservices] = useState("ns1");
  const [nnRpcAddrs, setNnRpcAddrs] = useState("ns1.nn1=nn1.example.com:8020\nns1.nn2=nn2.example.com:8020");
  const [nnHttpAddrs, setNnHttpAddrs] = useState("ns1.nn1.http-address=nn1.example.com:9870\nns1.nn2.http-address=nn2.example.com:9870");
  const [user, setUser] = useState("hdfs");
  const [useKerberos, setUseKerberos] = useState(true);
  const [defaultFs, setDefaultFs] = useState("hdfs://ns1");
  const [basePath, setBasePath] = useState("/dts/platform");
  const [testResult, setTestResult] = useState<null | { ok: boolean; message: string }>(null);

  const onTest = async () => {
    setTestResult(null);
    await new Promise((r) => setTimeout(r, 500));
    const ok = Boolean(nameservices && defaultFs && basePath);
    setTestResult({ ok, message: ok ? "HDFS 可达（模拟）" : "请完善名称服务与默认FS" });
  };

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle className="text-base">数据存储（HDFS）</CardTitle>
          <CardDescription>配置 HDFS 集群作为平台默认存储。</CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label>名称服务（nameservices）</Label>
              <Input value={nameservices} onChange={(e) => setNameservices(e.target.value)} />
              <p className="text-xs text-muted-foreground">如开启 HA，需在 core-site/hdfs-site 中一致。</p>
            </div>
            <div className="space-y-2">
              <Label>默认 FS</Label>
              <Input value={defaultFs} onChange={(e) => setDefaultFs(e.target.value)} placeholder="hdfs://ns1 或 hdfs://nn:8020" />
            </div>

            <div className="space-y-2 md:col-span-2">
              <Label>HA RPC 地址映射</Label>
              <Textarea rows={4} value={nnRpcAddrs} onChange={(e) => setNnRpcAddrs(e.target.value)} />
              <p className="text-xs text-muted-foreground">示例：ns1.nn1=nn1.example.com:8020</p>
            </div>
            <div className="space-y-2 md:col-span-2">
              <Label>HTTP 服务地址（可选）</Label>
              <Textarea rows={3} value={nnHttpAddrs} onChange={(e) => setNnHttpAddrs(e.target.value)} />
            </div>

            <div className="space-y-2">
              <Label>代理用户（提交/预览使用）</Label>
              <Input value={user} onChange={(e) => setUser(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>Kerberos</Label>
              <div className="flex items-center gap-2">
                <Switch checked={useKerberos} onCheckedChange={setUseKerberos} />
                <span className="text-xs text-muted-foreground">若启用，需在数据源处完成 KDC/Principal 设置</span>
              </div>
            </div>

            <div className="space-y-2 md:col-span-2">
              <Label>平台基础路径</Label>
              <Input value={basePath} onChange={(e) => setBasePath(e.target.value)} placeholder="/dts/platform" />
            </div>
          </div>

          <div className="flex items-center gap-2">
            <Button type="button" onClick={onTest}>
              <Icon icon="solar:wifi-router-bold" className="mr-1" /> 测试连接
            </Button>
            <Button type="button" variant="outline" disabled>
              <Icon icon="solar:save-2-bold" className="mr-1" /> 保存（示例）
            </Button>
            {testResult && (
              <span className={`text-sm ${testResult.ok ? "text-emerald-600" : "text-rose-600"}`}>{testResult.message}</span>
            )}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">最佳实践</CardTitle>
          <CardDescription>HDFS HA 与安全配置要点</CardDescription>
        </CardHeader>
        <CardContent>
          <ul className="list-disc pl-5 space-y-2 text-sm text-muted-foreground">
            <li>启用 HA：配置 nameservices、namenode RPC/HTTP 地址映射，客户端引用统一的 ns。</li>
            <li>权限：按业务目录进行最小授权，平台组件使用独立技术账号。</li>
            <li>安全：Kerberos 与 Ranger/Sentry 结合统一控制；日志与审计统一归集。</li>
          </ul>
        </CardContent>
      </Card>
    </div>
  );
}

