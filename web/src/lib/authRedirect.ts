export const AUTH_REDIRECT_STORAGE_KEY = "auth_redirect";

/**
 * Sanitizes a redirect path to ensure it is a safe local relative path
 * and cannot be used as an open redirect (e.g. protocol-relative URLs like '//evil.com').
 */
export function sanitizeRedirectPath(
  path: string | null | undefined
): string | null {
  if (!path) return null;
  const trimmed = path.trim();
  if (
    trimmed.startsWith("/") &&
    !trimmed.startsWith("//") &&
    !trimmed.startsWith("/\\")
  ) {
    return trimmed;
  }
  return null;
}

/**
 * Saves a safe redirect destination to sessionStorage.
 */
export function saveAuthRedirect(path: string): void {
  if (typeof window === "undefined") return;
  const safePath = sanitizeRedirectPath(path);
  if (safePath) {
    sessionStorage.setItem(AUTH_REDIRECT_STORAGE_KEY, safePath);
  }
}

/**
 * Reads and clears any pending redirect destination from query params or sessionStorage.
 */
export function consumeAuthRedirect(): string | null {
  if (typeof window === "undefined") return null;

  const params = new URLSearchParams(window.location.search);
  const paramRedirect = sanitizeRedirectPath(params.get("redirect"));
  const sessionRedirect = sanitizeRedirectPath(
    sessionStorage.getItem(AUTH_REDIRECT_STORAGE_KEY)
  );

  // Clear storage regardless
  sessionStorage.removeItem(AUTH_REDIRECT_STORAGE_KEY);

  return paramRedirect || sessionRedirect;
}
