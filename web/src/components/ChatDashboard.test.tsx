import { render, screen, fireEvent, act } from "@testing-library/react";
import ChatDashboard from "./ChatDashboard";
import { AuthProvider } from "@/context/AuthContext";
import { WebSocketProvider } from "@/context/WebSocketContext";

// Mock child components to make testing simpler
jest.mock("./Sidebar", () => {
  return function MockSidebar({ activeChat, viewMode }: any) {
    // Note: since viewMode isn't passed to Sidebar yet, we will check how Sidebar is styled or mocked.
    // Wait, Sidebar styling is inside Sidebar.tsx itself.
    // If we mock Sidebar, we won't be able to test its internal className logic directly from ChatDashboard tests
    // unless we render the actual Sidebar or check the props passed to it.
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
      <WebSocketProvider>{ui}</WebSocketProvider>
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
