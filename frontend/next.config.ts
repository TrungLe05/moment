import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // ADR 0001: chỉ xuất trang tĩnh ra out/, không dùng tính năng cần server Node.js.
  // Không bật cacheComponents/partialPrefetching của mẫu create-next-app: cần PPR, không chạy được khi export.
  output: "export",
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
