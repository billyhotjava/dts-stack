import "./polyfills/legacy-browser";
import "./styles.css";
import ReactDOM from "react-dom/client";
import { RouterProvider } from "react-router";
import { createRoutes } from "./routes";
import { startPlatformSessionHeartbeat } from "./api/platformSession";

// BUG-005: Keep platform session alive while analytics is active
startPlatformSessionHeartbeat();

const router = createRoutes();

ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(<RouterProvider router={router} />);
