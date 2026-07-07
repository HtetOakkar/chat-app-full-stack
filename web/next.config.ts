import type { NextConfig } from "next";
import os from "os";

const getLocalIPs = (): string[] => {
  const interfaces = os.networkInterfaces();
  const ips: string[] = [];
  for (const name of Object.keys(interfaces)) {
    const netInterface = interfaces[name];
    if (netInterface) {
      for (const net of netInterface) {
        if (net.family === "IPv4" && !net.internal) {
          ips.push(net.address);
        }
      }
    }
  }
  return ips;
};

const localIPs = getLocalIPs();
const devOrigins = [
  "localhost:3000",
  "127.0.0.1:3000",
  "http://localhost:3000",
  "http://127.0.0.1:3000",
];
localIPs.forEach((ip) => {
  devOrigins.push(ip);
  devOrigins.push(`${ip}:3000`);
  devOrigins.push(`http://${ip}:3000`);
});

const nextConfig: NextConfig = {
  // Allow Dev server access from local network IPs
  allowedDevOrigins: devOrigins,
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: "http://127.0.0.1:8181/api/:path*",
      },
    ];
  },
};

export default nextConfig;
