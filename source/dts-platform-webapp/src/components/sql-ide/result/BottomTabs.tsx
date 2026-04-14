import { Tabs } from "antd";
import { type FC } from "react";
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

const TAB_ITEMS = (props: BottomTabsProps) => [
  {
    key: "results",
    label: "结果",
    children: (
      <div style={{ height: "100%", overflow: "hidden" }}>
        <ResultGrid
          executionId={props.executionId}
          gridState={props.gridState}
          onGridStateChange={props.onGridStateChange}
        />
      </div>
    ),
  },
  {
    key: "chart",
    label: "图表",
    children: (
      <div style={{ height: "100%", overflow: "auto" }}>
        <ResultChart executionId={props.executionId} />
      </div>
    ),
  },
  {
    key: "pivot",
    label: "透视表",
    children: (
      <div style={{ height: "100%", overflow: "auto" }}>
        <ResultPivot executionId={props.executionId} />
      </div>
    ),
  },
  {
    key: "plan",
    label: "查询计划",
    children: (
      <div style={{ height: "100%", overflow: "auto" }}>
        <QueryPlanView
          executionId={props.executionId}
          fallbackSql={props.sql}
          engine={props.engine}
          datasourceId={props.datasourceId}
          catalog={props.catalog}
        />
      </div>
    ),
  },
  {
    key: "log",
    label: "日志",
    children: (
      <div style={{ height: "100%", overflow: "auto" }}>
        <LogPanel executionId={props.executionId} showAdvanced={false} />
      </div>
    ),
  },
];

export const BottomTabs: FC<BottomTabsProps> = (props) => {
  return (
    <Tabs
      defaultActiveKey="results"
      size="small"
      style={{ height: "100%", display: "flex", flexDirection: "column" }}
      tabBarStyle={{ margin: 0, paddingLeft: 8, flexShrink: 0 }}
      items={TAB_ITEMS(props)}
    />
  );
};
