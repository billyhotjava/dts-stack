import { useEffect, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Button } from "@/ui/button";
import { listDashboards } from "@/api/platformApi";

type Dashboard = { code: string; name: string; level: string; url: string };

export default function DashboardsPage() {
  const [items, setItems] = useState<Dashboard[]>([]);
  const [loading, setLoading] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const data = (await listDashboards()) as Dashboard[];
      setItems(data || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader className="flex items-center justify-between">
          <CardTitle className="text-base">仪表盘</CardTitle>
          <Button variant="outline" onClick={load} disabled={loading}>
            刷新
          </Button>
        </CardHeader>
        <CardContent>
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {items.map((d) => (
              <a
                key={d.code}
                href={d.url}
                target="_blank"
                className="block rounded-md border p-3 hover:bg-muted/40"
                rel="noreferrer"
              >
                <div className="flex items-center justify-between">
                  <div className="font-medium">{d.name}</div>
                  <div className="text-xs text-muted-foreground">{d.level}</div>
                </div>
                <div className="mt-1 text-xs text-muted-foreground break-all">{d.url}</div>
              </a>
            ))}
            {!items.length && <div className="text-sm text-muted-foreground">暂无可见仪表盘</div>}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

