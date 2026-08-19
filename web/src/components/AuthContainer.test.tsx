import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import AuthContainer from "./AuthContainer";
import { DisplayPreferencesProvider } from "@/context/DisplayPreferencesContext";
import { useAuth } from "@/context/AuthContext";
import { useRouter } from "next/navigation";

const mockPush = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({
    push: mockPush,
  }),
}));

jest.mock("@/context/AuthContext", () => ({
  useAuth: jest.fn(),
}));

const renderAuth = () =>
  render(
    <DisplayPreferencesProvider>
      <AuthContainer />
    </DisplayPreferencesProvider>
  );

describe("AuthContainer localization", () => {
  beforeEach(() => {
    (useAuth as jest.Mock).mockReturnValue({ login: jest.fn() });
    window.localStorage.clear();
    global.fetch = jest.fn();
    window.matchMedia = jest.fn().mockReturnValue({
      matches: false,
      addEventListener: jest.fn(),
      removeEventListener: jest.fn(),
    });
  });

  it("shows normal English chat-app copy instead of registry copy", () => {
    renderAuth();

    expect(
      screen.getByRole("heading", { name: /welcome back/i })
    ).toBeInTheDocument();
    expect(
      screen.getByText(/sign in to continue your conversations/i)
    ).toBeInTheDocument();
    expect(screen.queryByText(/registry/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/curator/i)).not.toBeInTheDocument();
  });

  it("keeps display preferences in a compact toolbar after the auth context", () => {
    renderAuth();

    const secureSignIn = screen.getByText(/secure sign in/i);
    const preferences = screen.getByRole("toolbar", {
      name: /display preferences/i,
    });

    expect(preferences).toContainElement(
      screen.getByRole("combobox", { name: /appearance/i })
    );
    expect(preferences).toContainElement(
      screen.getByRole("combobox", { name: /interface language/i })
    );
    expect(
      secureSignIn.compareDocumentPosition(preferences) &
        Node.DOCUMENT_POSITION_FOLLOWING
    ).toBeTruthy();
  });

  it("switches app-owned auth text to Burmese", () => {
    renderAuth();

    fireEvent.change(screen.getByLabelText(/interface language/i), {
      target: { value: "my" },
    });

    expect(
      screen.getByRole("heading", { name: "ပြန်လည်ကြိုဆိုပါတယ်" })
    ).toBeInTheDocument();
    expect(screen.getByLabelText("အသုံးပြုသူအမည်")).toBeInTheDocument();
  });

  it("leaves server-provided errors unchanged", async () => {
    (global.fetch as jest.Mock).mockResolvedValue({
      ok: false,
      json: async () => ({ message: "SERVER SAYS NO" }),
    });
    renderAuth();

    fireEvent.change(screen.getByLabelText(/interface language/i), {
      target: { value: "my" },
    });
    fireEvent.change(screen.getByLabelText("အသုံးပြုသူအမည်"), {
      target: { value: "htet" },
    });
    fireEvent.change(screen.getByLabelText("စကားဝှက်"), {
      target: { value: "secret" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: /ဝင်မည်/ }).at(-1)!);

    await waitFor(() =>
      expect(screen.getByText("SERVER SAYS NO")).toBeInTheDocument()
    );
  });
});

describe("AuthContainer redirect continuity", () => {
  const mockLogin = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();
    (useAuth as jest.Mock).mockReturnValue({ login: mockLogin });
    window.localStorage.clear();
    window.sessionStorage.clear();
    global.fetch = jest.fn();
  });

  it("navigates to redirect URL after successful login", async () => {
    window.history.pushState({}, "", "/?redirect=/join/token-xyz");

    (global.fetch as jest.Mock).mockResolvedValue({
      ok: true,
      json: async () => ({ token: "fake-jwt-token" }),
    });

    renderAuth();

    expect(window.sessionStorage.getItem("auth_redirect")).toBe(
      "/join/token-xyz"
    );

    fireEvent.change(screen.getByLabelText(/username/i), {
      target: { value: "alice" },
    });
    fireEvent.change(screen.getByLabelText(/^password$/i), {
      target: { value: "password123" },
    });
    fireEvent.click(
      screen.getAllByRole("button", { name: /sign in/i }).at(-1)!
    );

    await waitFor(() => {
      expect(mockLogin).toHaveBeenCalledWith("fake-jwt-token");
      expect(window.sessionStorage.getItem("auth_redirect")).toBeNull();
      expect(mockPush).toHaveBeenCalledWith("/join/token-xyz");
    });
  });

  it("preserves redirect across multi-step registration and email verification", async () => {
    window.history.pushState({}, "", "/");
    window.sessionStorage.setItem("auth_redirect", "/join/token-multi-step");

    (global.fetch as jest.Mock)
      // Step 1: signup response
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ message: "Verification code sent" }),
      })
      // Step 2: verify-email response
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ token: "verified-jwt-token" }),
      });

    renderAuth();

    // Switch to sign up
    fireEvent.click(screen.getByRole("button", { name: /sign up/i }));

    fireEvent.change(screen.getByLabelText(/username/i), {
      target: { value: "bob" },
    });
    fireEvent.change(screen.getByLabelText(/email/i), {
      target: { value: "bob@example.com" },
    });
    fireEvent.change(screen.getByLabelText(/^password/i), {
      target: { value: "password123" },
    });
    fireEvent.change(screen.getByLabelText(/re-enter password/i), {
      target: { value: "password123" },
    });
    fireEvent.click(screen.getByRole("button", { name: /create account/i }));

    // Verification screen appears
    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "Email Verification" })
      ).toBeInTheDocument();
    });

    // Enter 6-digit code
    fireEvent.change(screen.getByLabelText(/verification code/i), {
      target: { value: "123456" },
    });
    fireEvent.click(screen.getByRole("button", { name: /verify code/i }));

    await waitFor(() => {
      expect(mockLogin).toHaveBeenCalledWith("verified-jwt-token");
      expect(window.sessionStorage.getItem("auth_redirect")).toBeNull();
      expect(mockPush).toHaveBeenCalledWith("/join/token-multi-step");
    });
  });
});
