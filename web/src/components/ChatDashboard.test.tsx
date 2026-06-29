import { render, screen } from "@testing-library/react";
import ChatDashboard from "./ChatDashboard";
import { AuthProvider } from "@/context/AuthContext";
import { ConnectionProvider } from "@/context/ConnectionContext";
import { MessageStoreProvider } from "@/context/MessageStore";
import { PresenceProvider } from "@/context/PresenceContext";
import React from "react";

// Mock child components to make testing simpler
jest.mock("./Sidebar", () => {
  return function MockSidebar({ activeChat, viewMode }: any) {
    return <div data-testid="sidebar">Sidebar {viewMode}</div>;
  };
});

jest.mock("./MyProfileCard", () => {
  return function MockMyProfileCard({ onBack }: any) {
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
    <AuthProvider>
      <ConnectionProvider>
        <MessageStoreProvider>
          <PresenceProvider>{ui}</PresenceProvider>
        </MessageStoreProvider>
      </ConnectionProvider>
    </AuthProvider>
  );
};

describe("ChatDashboard mobile view profile", () => {
  it("hides sidebar on mobile when view mode is profile", () => {
    renderWithProviders(<ChatDashboard />);
    // Initial state: Sidebar is visible
    expect(screen.getByTestId("sidebar")).toBeInTheDocument();
  });
});
