import type { FC } from "react";
import { SqlIde } from "@/components/sql-ide/SqlIde";

const SqlIdePage: FC = () => (
  <div
    style={{
      display: "flex",
      flex: "1 1 auto",
      width: "100%",
      height: "calc(100vh - 160px)",
      minHeight: 500,
    }}
  >
    <SqlIde />
  </div>
);

export default SqlIdePage;
