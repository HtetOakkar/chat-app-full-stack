import { apiEndpoint } from "./apiBase";

describe("apiEndpoint", () => {
  const originalEnv = process.env;

  beforeEach(() => {
    process.env = { ...originalEnv };
    delete process.env.NEXT_PUBLIC_API_URL;
    delete process.env.NEXT_PUBLIC_API_PORT;
  });

  afterEach(() => {
    process.env = originalEnv;
  });

  it("keeps localhost requests relative for the Next rewrite", () => {
    expect(
      apiEndpoint("/api/v1/auth/login", {
        protocol: "http:",
        hostname: "localhost",
      })
    ).toBe("/api/v1/auth/login");
  });

  it("routes LAN browser requests directly to the backend on the same host", () => {
    expect(
      apiEndpoint("/api/v1/auth/login", {
        protocol: "http:",
        hostname: "192.168.0.121",
      })
    ).toBe("http://192.168.0.121:8181/api/v1/auth/login");
  });

  it("allows overriding the backend URL explicitly", () => {
    process.env.NEXT_PUBLIC_API_URL = "http://chat-api.test:9000/";

    expect(
      apiEndpoint("/api/v1/auth/login", {
        protocol: "http:",
        hostname: "192.168.0.121",
      })
    ).toBe("http://chat-api.test:9000/api/v1/auth/login");
  });
});
