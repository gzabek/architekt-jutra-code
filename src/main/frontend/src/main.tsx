import { ChakraProvider } from "@chakra-ui/react";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router-dom";
import { router } from "./router";
import { system } from "./theme";
import { PluginProvider } from "./plugins/PluginContext";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <ChakraProvider value={system}>
      <PluginProvider>
        <RouterProvider router={router} />
      </PluginProvider>
    </ChakraProvider>
  </StrictMode>,
);
