import {
  sanitizeRedirectPath,
  saveAuthRedirect,
  consumeAuthRedirect,
  AUTH_REDIRECT_STORAGE_KEY,
} from "./authRedirect";

describe("authRedirect helper", () => {
  beforeEach(() => {
    sessionStorage.clear();
    window.history.pushState({}, "", "/");
  });

  describe("sanitizeRedirectPath", () => {
    it("accepts valid relative paths", () => {
      expect(sanitizeRedirectPath("/join/token-123")).toBe("/join/token-123");
      expect(sanitizeRedirectPath("/w/acme")).toBe("/w/acme");
      expect(sanitizeRedirectPath("/")).toBe("/");
    });

    it("rejects open redirect and protocol-relative URLs", () => {
      expect(sanitizeRedirectPath("//evil.com")).toBeNull();
      expect(sanitizeRedirectPath("//evil.com/join")).toBeNull();
      expect(sanitizeRedirectPath("/\\evil.com")).toBeNull();
      expect(sanitizeRedirectPath("https://evil.com")).toBeNull();
      expect(sanitizeRedirectPath("javascript:alert(1)")).toBeNull();
      expect(sanitizeRedirectPath("")).toBeNull();
      expect(sanitizeRedirectPath(null)).toBeNull();
      expect(sanitizeRedirectPath(undefined)).toBeNull();
    });
  });

  describe("saveAuthRedirect and consumeAuthRedirect", () => {
    it("saves and consumes safe redirect from sessionStorage", () => {
      saveAuthRedirect("/join/secret-token");
      expect(sessionStorage.getItem(AUTH_REDIRECT_STORAGE_KEY)).toBe(
        "/join/secret-token"
      );

      const consumed = consumeAuthRedirect();
      expect(consumed).toBe("/join/secret-token");
      expect(sessionStorage.getItem(AUTH_REDIRECT_STORAGE_KEY)).toBeNull();
    });

    it("prioritizes query param if present", () => {
      saveAuthRedirect("/join/from-storage");
      window.history.pushState({}, "", "/?redirect=/join/from-param");

      const consumed = consumeAuthRedirect();
      expect(consumed).toBe("/join/from-param");
      expect(sessionStorage.getItem(AUTH_REDIRECT_STORAGE_KEY)).toBeNull();
    });

    it("ignores malicious query param and falls back to safe sessionStorage or null", () => {
      saveAuthRedirect("/join/safe-token");
      window.history.pushState({}, "", "/?redirect=//evil.com");

      const consumed = consumeAuthRedirect();
      expect(consumed).toBe("/join/safe-token");
      expect(sessionStorage.getItem(AUTH_REDIRECT_STORAGE_KEY)).toBeNull();
    });
  });
});
