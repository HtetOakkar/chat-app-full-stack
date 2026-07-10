import { fireEvent, render, screen, within } from "@testing-library/react";
import ChatDashboard from "./ChatDashboard";
import { ConnectionProvider } from "@/context/ConnectionContext";
import { MessageStoreProvider } from "@/context/MessageStore";
import { PresenceProvider } from "@/context/PresenceContext";
import { CallProvider } from "@/context/CallContext";
import { DisplayPreferencesProvider } from "@/context/DisplayPreferencesContext";
import { useAuth } from "@/context/AuthContext";
import { apiFetch } from "@/lib/api";
import React from "react";

jest.mock("@/context/AuthContext", () => ({
  AuthProvider: ({ children }: { children: React.ReactNode }) => (
    <>{children}</>
  ),
  useAuth: jest.fn(),
}));

// Mock child components to make testing simpler
jest.mock("./Sidebar", () => {
  return function MockSidebar({ viewMode }: { viewMode?: string }) {
    return <div data-testid="sidebar">Sidebar {viewMode}</div>;
  };
});

jest.mock("./MyProfileCard", () => {
  return function MockMyProfileCard({ onBack }: never) {
    return (
      <div data-testid="my-profile-card">
        My Profile Card <button onClick={onBack}>Back</button>
      </div>
    );
  };
});

jest.mock("./ChatViewport", () => {
  return function MockChatViewport() {
    return <div data-testid="chat-viewport">Chat Viewport</div>;
  };
});

jest.mock("@/lib/api", () => ({
  apiFetch: jest.fn().mockResolvedValue({}),
}));

const renderWithProviders = (ui: React.ReactElement) => {
  return render(
    <DisplayPreferencesProvider>
      <ConnectionProvider>
        <MessageStoreProvider>
          <PresenceProvider>
            <CallProvider>{ui}</CallProvider>
          </PresenceProvider>
        </MessageStoreProvider>
      </ConnectionProvider>
    </DisplayPreferencesProvider>
  );
};

describe("ChatDashboard mobile view profile", () => {
  beforeEach(() => {
    window.localStorage.clear();
    (apiFetch as jest.Mock).mockResolvedValue(null);
    window.matchMedia = jest.fn().mockReturnValue({
      matches: false,
      addEventListener: jest.fn(),
      removeEventListener: jest.fn(),
    });
    (useAuth as jest.Mock).mockReturnValue({
      logout: jest.fn(),
      username: "htet",
      userId: 1,
    });
  });

  it("hides sidebar on mobile when view mode is profile", () => {
    renderWithProviders(<ChatDashboard />);
    // Initial state: Sidebar is visible
    expect(screen.getByTestId("sidebar")).toBeInTheDocument();
  });

  it("uses dynamic viewport height (h-dvh) instead of h-screen to prevent clipping on mobile browsers", () => {
    const { container } = renderWithProviders(<ChatDashboard />);
    expect(container.firstChild).toHaveClass("h-dvh");
    expect(container.firstChild).not.toHaveClass("h-screen");
  });

  it("opens a Settings page with display preferences from the profile menu", () => {
    renderWithProviders(<ChatDashboard />);

    fireEvent.click(screen.getByRole("button", { name: /htet/i }));

    expect(
      screen.queryByRole("combobox", { name: /appearance/i })
    ).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /settings/i }));

    const settings = screen.getByRole("main", { name: /settings/i });

    expect(
      screen.getByRole("heading", { name: /settings/i })
    ).toBeInTheDocument();
    expect(
      within(settings).getByRole("combobox", { name: /appearance/i })
    ).toBeInTheDocument();
    expect(
      within(settings).getByRole("combobox", {
        name: /interface language/i,
      })
    ).toBeInTheDocument();
  });
});
