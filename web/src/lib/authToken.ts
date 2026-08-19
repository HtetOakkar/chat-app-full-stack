let authToken: string | null = null;

export function setAuthToken(token: string) {
  authToken = token;
  if (typeof window !== "undefined") {
    try {
      localStorage.setItem("token", token);
    } catch {
      // Ignore in restricted environments
    }
  }
}

export function getAuthToken(): string | null {
  if (!authToken && typeof window !== "undefined") {
    try {
      authToken = localStorage.getItem("token");
    } catch {
      // Ignore in restricted environments
    }
  }
  return authToken;
}

export function clearAuthToken() {
  authToken = null;
  if (typeof window !== "undefined") {
    try {
      localStorage.removeItem("token");
    } catch {
      // Ignore in restricted environments
    }
  }
}
