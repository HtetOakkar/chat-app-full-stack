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

describe("AuthProvider bearer token transport", () => {
  beforeEach(() => {
    localStorage.clear();
    jest.restoreAllMocks();
  });

  it("keeps bearer tokens in memory instead of localStorage", async () => {
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

    act(() => {
      screen.getByText("login").click();
    });

    expect(screen.getByTestId("authenticated")).toHaveTextContent("true");
    expect(screen.getByTestId("userId")).toHaveTextContent("42");
    expect(screen.getByTestId("username")).toHaveTextContent("alice");
    expect(localStorage.getItem("token")).toBeNull();
    expect(setItemSpy).not.toHaveBeenCalledWith("token", expect.any(String));

    act(() => {
      screen.getByText("logout").click();
    });

    expect(screen.getByTestId("authenticated")).toHaveTextContent("false");
    expect(screen.getByTestId("token")).toHaveTextContent("");
    expect(removeItemSpy).not.toHaveBeenCalledWith("token");
  });

  it("does not restore authentication from localStorage on reload", async () => {
    localStorage.setItem("token", makeToken({ jti: "7", sub: "stored" }));

    render(
      <AuthProvider>
        <AuthProbe />
      </AuthProvider>
    );

    await waitFor(() =>
      expect(screen.getByTestId("loading")).toHaveTextContent("false")
    );

    expect(screen.getByTestId("authenticated")).toHaveTextContent("false");
    expect(screen.getByTestId("token")).toHaveTextContent("");
  });
});
