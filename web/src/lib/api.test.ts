import { apiFetch } from "./api";
import { setAuthToken, clearAuthToken } from "./authToken";

describe("apiFetch bearer token transport", () => {
  beforeEach(() => {
    clearAuthToken();
    localStorage.clear();
    jest.restoreAllMocks();
  });

  it("sends the in-memory bearer token instead of reading localStorage", async () => {
    localStorage.setItem("token", "stored-token");
    setAuthToken("memory-token");

    const getItemSpy = jest.spyOn(Storage.prototype, "getItem");
    const fetchMock = jest.fn().mockResolvedValue({
      ok: true,
      text: async () => "{}",
    } as Response);
    global.fetch = fetchMock;

    await apiFetch("/api/test");

    expect(getItemSpy).not.toHaveBeenCalledWith("token");
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/test",
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer memory-token",
        }),
      })
    );
  });
});
