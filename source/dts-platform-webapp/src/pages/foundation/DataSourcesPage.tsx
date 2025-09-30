import { useState } from "react";
import { Icon } from "@/components/icon";
import { Button } from "@/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Textarea } from "@/ui/textarea";
import { Switch } from "@/ui/switch";
import { Badge } from "@/ui/badge";

type AuthMethod = "keytab" | "password";

export default function DataSourcesPage() {
  const [name, setName] = useState("");
  const [type, setType] = useState("HIVE");
  const [principal, setPrincipal] = useState("");
  const [realm, setRealm] = useState("");
  const [kdc, setKdc] = useState("");
  const [authMethod, setAuthMethod] = useState<AuthMethod>("keytab");
  const [keytab, setKeytab] = useState<File | null>(null);
  const [password, setPassword] = useState("");
  const [krbConf, setKrbConf] = useState("");
  const [jdbcUrl, setJdbcUrl] = useState("jdbc:hive2://host:10000/default;principal=...@REALM");
  const [testResult, setTestResult] = useState<null | { ok: boolean; message: string }>(null);
  const [spnego, setSpnego] = useState(false);

  const onPickKeytab = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0] || null;
    setKeytab(f);
  };

  const onTest = async () => {
    // Placeholder: simulate connection test
    setTestResult(null);
    await new Promise((r) => setTimeout(r, 600));
    const ok = Boolean(name && principal && realm && (authMethod === "keytab" ? keytab : password));
    setTestResult({ ok, message: ok ? "已成功连接（模拟）" : "请填写必填项后再试" });
  };

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle className="text-base">数据源（Kerberos 认证）</CardTitle>
          <CardDescription>当前仅演示 Kerberos 连接信息的配置，未接入后端保存。</CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label>数据源名称</Label>
              <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="如：生产 Hive" />
            </div>
            <div className="space-y-2">
              <Label>类型</Label>
              <Select value={type} onValueChange={setType}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="HIVE">Hive</SelectItem>
                  <SelectItem value="HBASE">HBase</SelectItem>
                  <SelectItem value="KAFKA">Kafka</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label>Kerberos Principal</Label>
              <Input value={principal} onChange={(e) => setPrincipal(e.target.value)} placeholder="hive/_HOST@EXAMPLE.COM" />
            </div>
            <div className="space-y-2">
              <Label>Realm</Label>
              <Input value={realm} onChange={(e) => setRealm(e.target.value)} placeholder="EXAMPLE.COM" />
            </div>
            <div className="space-y-2">
              <Label>KDC 地址</Label>
              <Input value={kdc} onChange={(e) => setKdc(e.target.value)} placeholder="kdc1.example.com:88,kdc2.example.com:88" />
            </div>
            <div className="space-y-2">
              <Label>SPNEGO</Label>
              <div className="flex items-center gap-2">
                <Switch checked={spnego} onCheckedChange={setSpnego} />
                <span className="text-xs text-muted-foreground">启用浏览器 SPNEGO（需要网关与浏览器支持）</span>
              </div>
            </div>

            <div className="space-y-2 md:col-span-2">
              <Label>认证方式</Label>
              <div className="flex flex-wrap items-center gap-2">
                <Button type="button" variant={authMethod === "keytab" ? "default" : "outline"} size="sm" onClick={() => setAuthMethod("keytab")}>
                  Keytab
                </Button>
                <Button type="button" variant={authMethod === "password" ? "default" : "outline"} size="sm" onClick={() => setAuthMethod("password")}>
                  密码
                </Button>
                <Badge variant="outline" className="ml-2">推荐：Keytab</Badge>
              </div>
            </div>

            {authMethod === "keytab" ? (
              <div className="space-y-2 md:col-span-2">
                <Label>上传 Keytab</Label>
                <Input type="file" accept=".keytab" onChange={onPickKeytab} />
                {keytab && <p className="text-xs text-muted-foreground">已选择：{keytab.name}</p>}
              </div>
            ) : (
              <div className="space-y-2 md:col-span-2">
                <Label>密码</Label>
                <Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="Kerberos 密码" />
              </div>
            )}

            <div className="space-y-2 md:col-span-2">
              <Label>krb5.conf（可选）</Label>
              <Textarea rows={6} value={krbConf} onChange={(e) => setKrbConf(e.target.value)} placeholder="[libdefaults]\n default_realm = EXAMPLE.COM\n ..." />
              <p className="text-xs text-muted-foreground">如不填写，将按平台默认策略生成。</p>
            </div>

            <div className="space-y-2 md:col-span-2">
              <Label>JDBC / 连接串</Label>
              <Input value={jdbcUrl} onChange={(e) => setJdbcUrl(e.target.value)} />
              <p className="text-xs text-muted-foreground">示例（Hive）：jdbc:hive2://host:10000/default;principal=hive/_HOST@EXAMPLE.COM</p>
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
          <CardTitle className="text-base">接入须知</CardTitle>
          <CardDescription>Kerberos 环境准备要点</CardDescription>
        </CardHeader>
        <CardContent>
          <ul className="list-disc pl-5 space-y-2 text-sm text-muted-foreground">
            <li>平台运行环境需可访问 KDC/Realm，并配置时钟同步（NTP）。</li>
            <li>服务主体建议使用专用 principal，如 hive/_HOST@REALM，授权最小化。</li>
            <li>生产环境建议以 keytab 分发，避免明文密码；妥善保管密钥。</li>
            <li>若启用 SPNEGO，请确保前置网关和浏览器均正确配置。</li>
          </ul>
        </CardContent>
      </Card>
    </div>
  );
}

