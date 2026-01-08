import "./polyfills/legacy-browser";
import "./styles.css";
import ReactDOM from "react-dom/client";
import { RouterProvider } from "react-router";
import { normalizeLocale } from "./i18n";
import { createRoutes } from "./routes";

const locale = normalizeLocale(navigator.language);
const router = createRoutes(locale);

ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(<RouterProvider router={router} />);
