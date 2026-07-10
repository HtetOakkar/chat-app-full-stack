type BrowserLocation = Pick<Location, "protocol" | "hostname">;

const LOOPBACK_HOSTS = new Set(["localhost", "127.0.0.1", "::1"]);

function trimTrailingSlash(value: string) {
  return value.endsWith("/") ? value.slice(0, -1) : value;
}

function currentLocation(): BrowserLocation | null {
  if (typeof window === "undefined" || !window.location) {
    return null;
  }
  return window.location;
}

function shouldUseSameHostBackend(
  location: BrowserLocation | null
): location is BrowserLocation {
  return location !== null && !LOOPBACK_HOSTS.has(location.hostname);
}

export function apiEndpoint(
  endpoint: string,
  location: BrowserLocation | null = currentLocation()
) {
  const configuredBaseUrl = process.env.NEXT_PUBLIC_API_URL;
  if (configuredBaseUrl) {
    return `${trimTrailingSlash(configuredBaseUrl)}${endpoint}`;
  }

  if (shouldUseSameHostBackend(location)) {
    const port = process.env.NEXT_PUBLIC_API_PORT || "8181";
    return `${location.protocol}//${location.hostname}:${port}${endpoint}`;
  }

  return endpoint;
}
