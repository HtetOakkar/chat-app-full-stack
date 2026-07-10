import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import AuthContainer from "./AuthContainer";
import { DisplayPreferencesProvider } from "@/context/DisplayPreferencesContext";
import { useAuth } from "@/context/AuthContext";

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
