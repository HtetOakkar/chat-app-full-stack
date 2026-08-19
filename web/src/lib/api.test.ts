import { apiFetch } from "./api";
import { setAuthToken, clearAuthToken } from "./authToken";

describe("apiFetch bearer token transport", () => {
  beforeEach(() => {
    clearAuthToken();
    localStorage.clear();
    jest.restoreAllMocks();
  });

  it("sends the bearer token in Authorization header", async () => {
    setAuthToken("test-token-123");

    const fetchMock = jest.fn().mockResolvedValue({
      ok: true,
      text: async () => "{}",
    } as Response);
    global.fetch = fetchMock;

    await apiFetch("/api/test");

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/test",
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer test-token-123",
        }),
      })
    );
  });

  it("retrieves token from localStorage if available", async () => {
    localStorage.setItem("token", "persisted-token");

    const fetchMock = jest.fn().mockResolvedValue({
      ok: true,
      text: async () => "{}",
    } as Response);
    global.fetch = fetchMock;

    await apiFetch("/api/test");

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/test",
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer persisted-token",
        }),
      })
    );
  });
});
