import { render, screen, fireEvent } from "@testing-library/react";
import Sidebar from "./Sidebar";
import { useAuth } from "@/context/AuthContext";
import { useWebSocket } from "@/context/WebSocketContext";
import { apiFetch } from "@/lib/api";

// Mock contexts and api
jest.mock("@/context/AuthContext", () => ({
  useAuth: jest.fn(),
}));
jest.mock("@/context/WebSocketContext", () => ({
  useWebSocket: jest.fn(),
}));
jest.mock("@/lib/api", () => ({
  apiFetch: jest.fn(),
}));

describe("Sidebar", () => {
  beforeEach(() => {
    (useAuth as jest.Mock).mockReturnValue({ userId: 1 });
    (useWebSocket as jest.Mock).mockReturnValue({ onlineUsers: {} });
    (apiFetch as jest.Mock).mockResolvedValue([]);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it("renders three navigation tabs: Chats, Contacts, and Requests", async () => {
    const { waitFor } = require("@testing-library/react");
    render(
      <Sidebar
        activeChat={null}
        onSelectChat={jest.fn()}
        onSelectProfileUser={jest.fn()}
        refreshTrigger={0}
      />
    );

    // Assert that the three tabs exist
    expect(screen.getByRole("button", { name: /chats/i })).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /contacts/i })
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /requests/i })
    ).toBeInTheDocument();

    await waitFor(() => {
      expect(apiFetch).toHaveBeenCalled();
    });
  });

  it("renders only CONTACT status contacts in the Contacts tab", async () => {
    const { waitFor } = require("@testing-library/react");
    (apiFetch as jest.Mock).mockImplementation((url) => {
      if (url === "/api/v1/contacts") {
        return Promise.resolve([
          {
            id: 1,
            contactUserId: 101,
            contactUsername: "user1",
            status: "CONTACT",
            createdAt: "2023-01-01",
          },
          {
            id: 2,
            contactUserId: 102,
            contactUsername: "user2",
            status: "ACCEPTED",
            createdAt: "2023-01-01",
          },
          {
            id: 3,
            contactUserId: 103,
            contactUsername: "user3",
            status: "CONTACT",
            createdAt: "2023-01-01",
          },
        ]);
      }
      return Promise.resolve([]);
    });

    render(
      <Sidebar
        activeChat={null}
        onSelectChat={jest.fn()}
        onSelectProfileUser={jest.fn()}
        refreshTrigger={0}
      />
    );

    await waitFor(() => {
      expect(apiFetch).toHaveBeenCalled();
    });

    // Switch to Contacts tab
    fireEvent.click(screen.getByRole("button", { name: /contacts/i }));

    // Verify filter chips
    expect(screen.getByText("All Contacts")).toBeInTheDocument();
    expect(screen.getByText("Blocked")).toBeInTheDocument();

    // Only user1 and user3 should be rendered, not user2
    expect(screen.getByText("user1")).toBeInTheDocument();
    expect(screen.getByText("user3")).toBeInTheDocument();
    expect(screen.queryByText("user2")).not.toBeInTheDocument();
  });

  it("renders BLOCKED status contacts when Blocked filter is clicked", async () => {
    const { waitFor } = require("@testing-library/react");
    (apiFetch as jest.Mock).mockImplementation((url) => {
      if (url === "/api/v1/contacts") {
        return Promise.resolve([
          {
            id: 1,
            contactUserId: 101,
            contactUsername: "user1",
            status: "CONTACT",
            createdAt: "2023-01-01",
          },
          {
            id: 2,
            contactUserId: 102,
            contactUsername: "user2",
            status: "BLOCKED",
            createdAt: "2023-01-01",
          },
        ]);
      }
      return Promise.resolve([]);
    });

    render(
      <Sidebar
        activeChat={null}
        onSelectChat={jest.fn()}
        onSelectProfileUser={jest.fn()}
        refreshTrigger={0}
      />
    );

    await waitFor(() => {
      expect(apiFetch).toHaveBeenCalled();
    });

    // Switch to Contacts tab
    fireEvent.click(screen.getByRole("button", { name: /contacts/i }));

    // Verify filter chips
    const blockedButton = screen.getByText("Blocked");
    expect(blockedButton).toBeInTheDocument();

    // Click Blocked
    fireEvent.click(blockedButton);

    // Only user2 should be rendered, not user1
    expect(screen.getByText("user2")).toBeInTheDocument();
    expect(screen.queryByText("user1")).not.toBeInTheDocument();
  });
  it("hides sidebar on mobile when viewMode is profile and activeChat is null", () => {
    const { container } = render(
      <Sidebar
        activeChat={null}
        onSelectChat={jest.fn()}
        onSelectProfileUser={jest.fn()}
        refreshTrigger={0}
        viewMode="profile"
      />
    );
    const aside = container.querySelector("aside");
    expect(aside).toHaveClass("hidden");
    expect(aside).not.toHaveClass("flex w-full");
  });
});
