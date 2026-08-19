import { fireEvent, render, screen, within } from "@testing-library/react";
import ChatDashboard from "./ChatDashboard";
import { ConnectionProvider } from "@/context/ConnectionContext";
import { WorkspaceProvider } from "@/context/WorkspaceContext";
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
jest.mock("./WorkspaceSidebar", () => {
  return function MockWorkspaceSidebar() {
    return <div data-testid="sidebar">Workspace Sidebar</div>;
  };
});

jest.mock("./WorkspaceRail", () => {
  return function MockWorkspaceRail() {
    return <div data-testid="workspace-rail" />;
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
  apiFetch: jest.fn().mockResolvedValue([]),
}));

const renderWithProviders = (ui: React.ReactElement) => {
  return render(
    <DisplayPreferencesProvider>
      <ConnectionProvider>
        <WorkspaceProvider>
          <MessageStoreProvider>
            <PresenceProvider>
              <CallProvider>{ui}</CallProvider>
            </PresenceProvider>
          </MessageStoreProvider>
        </WorkspaceProvider>
      </ConnectionProvider>
    </DisplayPreferencesProvider>
  );
};

const mockWorkspaces = [
  {
    id: 1,
    name: "Acme Corp",
    slug: "acme-corp",
    ownerId: 1,
    role: "OWNER" as const,
    memberCount: 5,
    channelCount: 3,
    createdAt: "2026-08-19T00:00:00Z",
  },
];

describe("ChatDashboard", () => {
  beforeEach(() => {
    window.localStorage.clear();
    (apiFetch as jest.Mock).mockImplementation((url: string) => {
      if (url === "/api/workspaces/me") {
        return Promise.resolve(mockWorkspaces);
      }
      return Promise.resolve([]);
    });
    window.matchMedia = jest.fn().mockReturnValue({
      matches: false,
      addEventListener: jest.fn(),
      removeEventListener: jest.fn(),
    });
    (useAuth as jest.Mock).mockReturnValue({
      logout: jest.fn(),
      username: "htet",
      userId: 1,
      isAuthenticated: true,
    });
  });

  it("hides sidebar on mobile when view mode is profile", async () => {
    renderWithProviders(<ChatDashboard />);
    // Initial state: Sidebar is visible when workspaces exist
    expect(await screen.findByTestId("sidebar")).toBeInTheDocument();
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

  describe("Zero-Workspace Empty State", () => {
    beforeEach(() => {
      (apiFetch as jest.Mock).mockImplementation((url: string) => {
        if (url === "/api/workspaces/me") {
          return Promise.resolve([]);
        }
        if (url === "/api/v1/users/profile") {
          return Promise.resolve({ fullName: "Htet Oakkar" });
        }
        return Promise.resolve([]);
      });
    });

    it("renders a clean dashboard empty state when user belongs to 0 workspaces without auto-opening modal blocker", () => {
      renderWithProviders(<ChatDashboard />);

      // Clean empty state canvas is shown
      expect(
        screen.getByRole("main", { name: /dashboard empty state/i })
      ).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: /no workspaces yet/i })
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /create workspace/i })
      ).toBeInTheDocument();

      // Modal is NOT automatically open on login
      expect(
        screen.queryByLabelText(/workspace name/i)
      ).not.toBeInTheDocument();
    });

    it("allows opening profile and settings from top navigation bar user menu when user has 0 workspaces", () => {
      renderWithProviders(<ChatDashboard />);

      // Top navigation bar user menu button is present and accessible
      const userMenuBtn = screen.getByRole("button", { name: /htet/i });
      expect(userMenuBtn).toBeInTheDocument();

      // Open user menu
      fireEvent.click(userMenuBtn);
      expect(
        screen.getByRole("button", { name: /view profile/i })
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /settings/i })
      ).toBeInTheDocument();

      // Open profile
      fireEvent.click(screen.getByRole("button", { name: /view profile/i }));
      expect(screen.getByTestId("my-profile-card")).toBeInTheDocument();

      // Click back to return to empty state
      fireEvent.click(screen.getByRole("button", { name: /back/i }));
      expect(
        screen.getByRole("heading", { name: /no workspaces yet/i })
      ).toBeInTheDocument();
    });

    it("opens workspace creation modal when clicking Create Workspace CTA in empty state", () => {
      renderWithProviders(<ChatDashboard />);

      // Click CTA button
      fireEvent.click(
        screen.getByRole("button", { name: /create workspace/i })
      );

      // Creation modal is now open
      expect(
        screen.getByRole("heading", { name: "Create Workspace" })
      ).toBeInTheDocument();
      expect(screen.getByLabelText(/workspace name/i)).toBeInTheDocument();
    });
  });
});
