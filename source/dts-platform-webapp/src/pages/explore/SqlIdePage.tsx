import type { FC } from "react";
import { SqlIde } from "@/components/sql-ide/SqlIde";

const SqlIdePage: FC = () => (
  <div style={{ position: "absolute", inset: 0, display: "flex" }}>
    <SqlIde />
  </div>
);

export default SqlIdePage;
