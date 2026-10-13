import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
const proxy = {
  "/api": {
    target: process.env.BANK_API_URL || "http://localhost:8080",
    changeOrigin: true,
  },
};
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, strictPort: true, proxy },
  preview: { proxy },
});
