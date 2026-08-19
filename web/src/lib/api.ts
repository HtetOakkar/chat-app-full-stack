import { getAuthToken } from "./authToken";
import { apiEndpoint } from "./apiBase";

export async function apiFetch(endpoint: string, options: RequestInit = {}) {
  const token = getAuthToken();

  const headers = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string>),
  } as Record<string, string>;

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(apiEndpoint(endpoint), {
    ...options,
    headers,
  });

  if (!response.ok) {
    let errorMsg = `API request failed with status ${response.status}`;
    try {
      const errorData = await response.json();
      errorMsg = errorData.message || errorData.error || errorMsg;
    } catch {
      // JSON parsing failed, try reading plain text or keep fallback
      try {
        const errText = await response.text();
        if (errText && errText.length < 200 && !errText.includes("<!DOCTYPE")) {
          errorMsg = errText;
        }
      } catch {}
    }

    if (response.status === 401) {
      errorMsg = "Unauthorized/Session Expired";
      if (typeof window !== "undefined") {
        window.dispatchEvent(new CustomEvent("auth:unauthorized"));
      }
    }

    throw new Error(errorMsg);
  }

  const text = await response.text();
  return text ? JSON.parse(text) : null;
}
