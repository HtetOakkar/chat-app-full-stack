import { render, screen, waitFor } from "@testing-library/react";
import { act } from "react";
import { AuthProvider, useAuth } from "./AuthContext";

const makeToken = (claims: Record<string, unknown>) => {
  const payload = btoa(JSON.stringify(claims));
  return `header.${payload}.signature`;
};

function AuthProbe() {
  const { login, logout, isAuthenticated, isLoading, token, userId, username } =
    useAuth();

  return (
    <div>
      <div data-testid="loading">{String(isLoading)}</div>
      <div data-testid="authenticated">{String(isAuthenticated)}</div>
      <div data-testid="token">{token ?? ""}</div>
      <div data-testid="userId">{userId ?? ""}</div>
      <div data-testid="username">{username ?? ""}</div>
      <button onClick={() => login(makeToken({ jti: "42", sub: "alice" }))}>
        login
      </button>
      <button onClick={logout}>logout</button>
    </div>
  );
}

describe("AuthProvider authentication lifecycle", () => {
  beforeEach(() => {
    localStorage.clear();
    jest.restoreAllMocks();
  });

  it("stores bearer tokens in localStorage and memory on login, and clears on logout", async () => {
    const setItemSpy = jest.spyOn(Storage.prototype, "setItem");
    const removeItemSpy = jest.spyOn(Storage.prototype, "removeItem");

    render(
      <AuthProvider>
        <AuthProbe />
      </AuthProvider>
    );

    await waitFor(() =>
      expect(screen.getByTestId("loading")).toHaveTextContent("false")
    );

    const testToken = makeToken({ jti: "42", sub: "alice" });
    act(() => {
      screen.getByText("login").click();
    });

    expect(screen.getByTestId("authenticated")).toHaveTextContent("true");
    expect(screen.getByTestId("userId")).toHaveTextContent("42");
    expect(screen.getByTestId("username")).toHaveTextContent("alice");
    expect(localStorage.getItem("token")).toBe(testToken);
    expect(setItemSpy).toHaveBeenCalledWith("token", testToken);

    act(() => {
      screen.getByText("logout").click();
    });

    expect(screen.getByTestId("authenticated")).toHaveTextContent("false");
    expect(screen.getByTestId("token")).toHaveTextContent("");
    expect(localStorage.getItem("token")).toBeNull();
    expect(removeItemSpy).toHaveBeenCalledWith("token");
  });

  it("restores authentication from localStorage on page reload / initial mount", async () => {
    const storedToken = makeToken({ jti: "7", sub: "stored-user" });
    localStorage.setItem("token", storedToken);

    render(
      <AuthProvider>
        <AuthProbe />
      </AuthProvider>
    );

    await waitFor(() =>
      expect(screen.getByTestId("loading")).toHaveTextContent("false")
    );

    expect(screen.getByTestId("authenticated")).toHaveTextContent("true");
    expect(screen.getByTestId("token")).toHaveTextContent(storedToken);
    expect(screen.getByTestId("userId")).toHaveTextContent("7");
    expect(screen.getByTestId("username")).toHaveTextContent("stored-user");
  });

  it("clears authentication when receiving auth:unauthorized event", async () => {
    const storedToken = makeToken({ jti: "99", sub: "expired-user" });
    localStorage.setItem("token", storedToken);

    render(
      <AuthProvider>
        <AuthProbe />
      </AuthProvider>
    );

    await waitFor(() =>
      expect(screen.getByTestId("loading")).toHaveTextContent("false")
    );

    expect(screen.getByTestId("authenticated")).toHaveTextContent("true");

    act(() => {
      window.dispatchEvent(new CustomEvent("auth:unauthorized"));
    });

    expect(screen.getByTestId("authenticated")).toHaveTextContent("false");
    expect(screen.getByTestId("token")).toHaveTextContent("");
    expect(localStorage.getItem("token")).toBeNull();
  });
});
