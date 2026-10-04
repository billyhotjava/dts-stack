import { Tabs } from "antd";
import { type FC, useMemo } from "react";
import { ResultGrid } from "./ResultGrid";
import { ResultChart } from "./ResultChart";
import { ResultPivot } from "./ResultPivot";
import { QueryPlanView } from "./QueryPlanView";
import { LogPanel } from "./LogPanel";
import type { GridColumnState } from "./columnState";

export interface BottomTabsProps {
  executionId: string;
  sql: string;
  engine: string;
  datasourceId: string | null;
  catalog: string | null;
  gridState: GridColumnState;
  onGridStateChange: (next: GridColumnState) => void;
}

export const BottomTabs: FC<BottomTabsProps> = (props) => {
  const { executionId, sql, engine, datasourceId, catalog, gridState, onGridStateChange } = props;
  // Memoise the items array — previously it was rebuilt on every render, which
  // caused antd to remount every panel and reset inner state (chart config,
  // pivot selections, grid scroll).
  const items = useMemo(
    () => [
      {
        key: "results",
        label: "结果",
        children: (
          <div style={{ height: "100%", overflow: "hidden" }}>
            <ResultGrid
              executionId={executionId}
              gridState={gridState}
              onGridStateChange={onGridStateChange}
            />
          </div>
        ),
      },
      {
        key: "chart",
        label: "图表",
        children: (
          <div style={{ height: "100%", overflow: "auto" }}>
            <ResultChart executionId={executionId} />
          </div>
        ),
      },
      {
        key: "pivot",
        label: "透视表",
        children: (
          <div style={{ height: "100%", overflow: "auto" }}>
            <ResultPivot executionId={executionId} />
          </div>
        ),
      },
      {
        key: "plan",
        label: "查询计划",
        children: (
          <div style={{ height: "100%", overflow: "auto" }}>
            <QueryPlanView
              executionId={executionId}
              fallbackSql={sql}
              engine={engine}
              datasourceId={datasourceId}
              catalog={catalog}
            />
          </div>
        ),
      },
      {
        key: "log",
        label: "日志",
        children: (
          <div style={{ height: "100%", overflow: "auto" }}>
            <LogPanel executionId={executionId} />
          </div>
        ),
      },
    ],
    [executionId, sql, engine, datasourceId, catalog, gridState, onGridStateChange],
  );

  return (
    <div
      className="sqlide-bottom-tabs"
      style={{ height: "100%", display: "flex", flexDirection: "column", minHeight: 0 }}
    >
      <Tabs
        defaultActiveKey="results"
        size="small"
        style={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column" }}
        tabBarStyle={{ margin: 0, paddingLeft: 8, flexShrink: 0 }}
        items={items}
      />
      {/*
        antd v5 default is `.ant-tabs-tabpane { flex: none }`, which collapses the
        pane height to its content. That breaks the inner `height: 100%` chain used
        by ResultGrid's scroll container (flex:1 + overflow:auto), so scrolling
        silently "disappears" and rows overflow into the clipped BottomPanel.
        Force the active pane to fill its flex parent so the inner grid can own
        its scroll and the sticky thead actually sticks.
      */}
      <style>{`
        .sqlide-bottom-tabs .ant-tabs-content-holder,
        .sqlide-bottom-tabs .ant-tabs-content {
          display: flex;
          flex-direction: column;
          flex: 1 1 auto;
          min-height: 0;
        }
        .sqlide-bottom-tabs .ant-tabs-tabpane {
          height: 100%;
          min-height: 0;
        }
      `}</style>
    </div>
  );
};
