import { createBrowserRouter, Navigate, Outlet } from "react-router-dom";
import { AppShell } from "./components/layout/AppShell";
import { CategoryListPage } from "./pages/CategoryListPage";
import { CategoryFormPage } from "./pages/CategoryFormPage";
import { ProductListPage } from "./pages/ProductListPage";
import { ProductFormPage } from "./pages/ProductFormPage";
import { ProductDetailPage } from "./pages/ProductDetailPage";
import { PluginListPage } from "./pages/PluginListPage";
import { PluginDetailPage } from "./pages/PluginDetailPage";
import { PluginFormPage } from "./pages/PluginFormPage";
import { PluginPageRoute } from "./pages/PluginPageRoute";

function Layout() {
  return (
    <AppShell>
      <Outlet />
    </AppShell>
  );
}

export const router = createBrowserRouter([
  {
    path: "/",
    element: <Layout />,
    children: [
      { index: true, element: <Navigate to="/products" replace /> },
      { path: "products", element: <ProductListPage /> },
      { path: "products/new", element: <ProductFormPage /> },
      { path: "products/:id", element: <ProductDetailPage /> },
      { path: "products/:id/edit", element: <ProductFormPage /> },
      { path: "categories", element: <CategoryListPage /> },
      { path: "categories/new", element: <CategoryFormPage /> },
      { path: "categories/:id/edit", element: <CategoryFormPage /> },
      { path: "plugins", element: <PluginListPage /> },
      { path: "plugins/new", element: <PluginFormPage /> },
      { path: "plugins/:pluginId/detail", element: <PluginDetailPage /> },
      { path: "plugins/:pluginId/edit", element: <PluginFormPage /> },
      { path: "plugins/:pluginId/*", element: <PluginPageRoute /> },
    ],
  },
]);
