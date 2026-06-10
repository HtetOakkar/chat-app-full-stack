import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: "http://127.0.0.1:8181/api/:path*", // Proxy to Backend nativel
      },
    ];
  },
};

export default nextConfig;
