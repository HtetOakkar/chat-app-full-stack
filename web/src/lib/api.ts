export async function apiFetch(endpoint: string, options: RequestInit = {}) {
  const token = typeof window !== "undefined" ? localStorage.getItem("token") : null;
  
  const headers = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string>),
  } as Record<string, string>;

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(endpoint, {
    ...options,
    headers,
  });

  if (!response.ok) {
    let errorMsg = "API request failed";
    try {
      const errorData = await response.json();
      errorMsg = errorData.message || errorMsg;
    } catch {
      if (response.status === 401) {
        // Handle unauthorized generically if needed
        errorMsg = "Unauthorized/Session Expired";
      }
    }

    if (response.status === 401 && typeof window !== "undefined") {
      window.dispatchEvent(new CustomEvent("auth:unauthorized"));
    }

    throw new Error(errorMsg);
  }

  // Some endpoints return 204 No Content with no body
  if (response.status === 204) {
    return null;
  }

  return response.json();
}
