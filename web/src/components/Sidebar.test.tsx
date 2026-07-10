import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import Sidebar from "./Sidebar";
import { useAuth } from "@/context/AuthContext";
import { usePresence } from "@/context/PresenceContext";
import { apiFetch } from "@/lib/api";

// Mock contexts and api
jest.mock("@/context/AuthContext", () => ({
  useAuth: jest.fn(),
}));
jest.mock("@/context/PresenceContext", () => ({
  usePresence: jest.fn(),
}));
jest.mock("@/lib/api", () => ({
  apiFetch: jest.fn(),
}));

describe("Sidebar", () => {
  beforeEach(() => {
    (useAuth as jest.Mock).mockReturnValue({ userId: 1 });
    (usePresence as jest.Mock).mockReturnValue({ onlineUsers: {} });
    (apiFetch as jest.Mock).mockResolvedValue([]);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it("renders three navigation tabs: Chats, Contacts, and Requests", async () => {
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

  it("filters contacts locally and opens the selected contact profile", async () => {
    const mockOnSelectProfileUser = jest.fn();
    (apiFetch as jest.Mock).mockImplementation((url) => {
      if (url === "/api/v1/contacts") {
        return Promise.resolve([
          {
            id: 1,
            contactUserId: 101,
            contactUsername: "alice",
            status: "CONTACT",
            createdAt: "2023-01-01",
          },
          {
            id: 2,
            contactUserId: 102,
            contactUsername: "bob",
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
        onSelectProfileUser={mockOnSelectProfileUser}
        refreshTrigger={0}
      />
    );

    await waitFor(() => {
      expect(screen.getByText("alice")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: /contacts/i }));
    fireEvent.change(screen.getByPlaceholderText("Search contacts..."), {
      target: { value: "ali" },
    });

    expect(screen.getByText("alice")).toBeInTheDocument();
    expect(screen.queryByText("bob")).not.toBeInTheDocument();
    expect(apiFetch).not.toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/users/search")
    );

    fireEvent.click(screen.getByText("alice"));

    expect(mockOnSelectProfileUser).toHaveBeenCalledWith({
      id: 101,
      username: "alice",
      status: "CONTACT",
    });
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

  it("formats call history JSON in lastMessageContent into a readable label", async () => {
    (apiFetch as jest.Mock).mockImplementation((url: string) => {
      if (url === "/api/v1/contacts") {
        return Promise.resolve([
          {
            id: 1,
            contactUserId: 2,
            contactUsername: "caller_bob",
            status: "ACCEPTED",
            lastMessageContent:
              '{"outcome":"completed","duration":10,"videoUsed":true}',
            lastMessageSenderId: 2,
            unreadCount: 0,
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
      expect(screen.getByText("caller_bob")).toBeInTheDocument();
    });

    expect(screen.getByText("Call Ended")).toBeInTheDocument();
    expect(screen.queryByText(/outcome/)).not.toBeInTheDocument();
  });
});
