import { Button, message } from "antd";
import { type FC, useState } from "react";
import { createTempView } from "../api/sqlIdeSubquery";
import { useTabStore } from "../tabs/useTabStore";

export interface SubQueryButtonProps {
  executionId: string;
}

export const SubQueryButton: FC<SubQueryButtonProps> = ({ executionId }) => {
  const [loading, setLoading] = useState(false);
  const openTab = useTabStore((s) => s.openTab);
  const updateTab = useTabStore((s) => s.updateTab);

  const handleQueryThis = async () => {
    setLoading(true);
    try {
      const v = await createTempView(executionId);
      const newId = openTab({
        title: `Query Result · ${executionId.slice(0, 8)}`,
        sqlText: `SELECT * FROM ${v.viewName} LIMIT 100`,
      });
      updateTab(newId, { subqueryViewName: v.viewName });
      void message.success(`已创建临时视图 ${v.viewName}（${v.rowCount} 行）`);
    } catch {
      void message.error("创建临时视图失败");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Button size="small" loading={loading} onClick={handleQueryThis}>
      Query This Result
    </Button>
  );
};
