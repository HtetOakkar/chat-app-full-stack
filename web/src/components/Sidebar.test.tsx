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

  it("renders both CONTACT and ACCEPTED status contacts in the All Contacts tab", async () => {
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

    // user1, user2 (ACCEPTED), and user3 should all be rendered
    expect(screen.getByText("user1")).toBeInTheDocument();
    expect(screen.getByText("user2")).toBeInTheDocument();
    expect(screen.getByText("user3")).toBeInTheDocument();
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

  it("switches between All Contacts and Blocked filters correctly without omitting accepted contacts", async () => {
    (apiFetch as jest.Mock).mockImplementation((url) => {
      if (url === "/api/v1/contacts") {
        return Promise.resolve([
          {
            id: 1,
            contactUserId: 101,
            contactUsername: "contact_user",
            status: "CONTACT",
            createdAt: "2023-01-01",
          },
          {
            id: 2,
            contactUserId: 102,
            contactUsername: "accepted_user",
            status: "ACCEPTED",
            createdAt: "2023-01-01",
          },
        ]);
      }
      if (url === "/api/v1/contacts/blocked") {
        return Promise.resolve([
          {
            id: 3,
            contactUserId: 103,
            contactUsername: "blocked_user",
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

    const allContactsButton = screen.getByText("All Contacts");
    const blockedButton = screen.getByText("Blocked");

    // In All Contacts: contact_user and accepted_user are visible; blocked_user is not
    expect(screen.getByText("contact_user")).toBeInTheDocument();
    expect(screen.getByText("accepted_user")).toBeInTheDocument();
    expect(screen.queryByText("blocked_user")).not.toBeInTheDocument();

    // Switch to Blocked
    fireEvent.click(blockedButton);
    expect(screen.getByText("blocked_user")).toBeInTheDocument();
    expect(screen.queryByText("contact_user")).not.toBeInTheDocument();
    expect(screen.queryByText("accepted_user")).not.toBeInTheDocument();

    // Switch back to All Contacts
    fireEvent.click(allContactsButton);
    expect(screen.getByText("contact_user")).toBeInTheDocument();
    expect(screen.getByText("accepted_user")).toBeInTheDocument();
    expect(screen.queryByText("blocked_user")).not.toBeInTheDocument();
  });

  it("searches within contacts tab consistently across both ACCEPTED and CONTACT users", async () => {
    const mockOnSelectProfileUser = jest.fn();
    (apiFetch as jest.Mock).mockImplementation((url) => {
      if (url === "/api/v1/contacts") {
        return Promise.resolve([
          {
            id: 1,
            contactUserId: 101,
            contactUsername: "alice_contact",
            contactFullName: "Alice Smith",
            status: "CONTACT",
            createdAt: "2023-01-01",
          },
          {
            id: 2,
            contactUserId: 102,
            contactUsername: "bob_accepted",
            contactFullName: "Bob Jones",
            status: "ACCEPTED",
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
      expect(apiFetch).toHaveBeenCalled();
    });

    fireEvent.click(screen.getByRole("button", { name: /contacts/i }));

    const searchInput = screen.getByPlaceholderText("Search contacts...");

    // Search for accepted user by username
    fireEvent.change(searchInput, {
      target: { value: "bob" },
    });

    expect(screen.getByText("Bob Jones")).toBeInTheDocument();
    expect(screen.queryByText("Alice Smith")).not.toBeInTheDocument();

    // Click on the accepted contact
    fireEvent.click(screen.getByText("Bob Jones"));
    expect(mockOnSelectProfileUser).toHaveBeenCalledWith({
      id: 102,
      username: "bob_accepted",
      status: "ACCEPTED",
    });

    // Search for contact user by full name
    fireEvent.change(searchInput, {
      target: { value: "smith" },
    });

    expect(screen.getByText("Alice Smith")).toBeInTheDocument();
    expect(screen.queryByText("Bob Jones")).not.toBeInTheDocument();
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

  it("does not render cleared conversation with no new messages in Chats tab, but renders it in Contacts tab", async () => {
    (apiFetch as jest.Mock).mockImplementation((url: string) => {
      if (url === "/api/v1/contacts") {
        return Promise.resolve([
          {
            id: 1,
            contactUserId: 101,
            contactUsername: "active_user",
            contactFullName: "Active User",
            status: "CONTACT",
            lastMessageContent: "Hello there",
            lastMessageTimestamp: "2026-08-17T10:00:00Z",
            createdAt: "2026-08-17T09:00:00Z",
          },
          {
            id: 2,
            contactUserId: 102,
            contactUsername: "cleared_user",
            contactFullName: "Cleared User",
            status: "CONTACT",
            clearedAt: "2026-08-17T10:00:00Z",
            lastMessageContent: null,
            lastMessageTimestamp: null,
            createdAt: "2026-08-17T09:00:00Z",
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

    // Wait for the active contact to render in the Chats tab
    await waitFor(() => {
      expect(screen.getByText("Active User")).toBeInTheDocument();
    });

    // In Chats tab (default): cleared_user should NOT be in active chats list
    expect(screen.queryByText("Cleared User")).not.toBeInTheDocument();
    expect(screen.queryByText("cleared_user")).not.toBeInTheDocument();

    // Switch to Contacts tab
    fireEvent.click(screen.getByRole("button", { name: /contacts/i }));

    // In Contacts tab: BOTH active_user and cleared_user SHOULD be rendered in the address book
    expect(screen.getByText("Active User")).toBeInTheDocument();
    expect(screen.getByText("Cleared User")).toBeInTheDocument();
  });

  it("renders cleared contact in Chats tab if a new message has arrived after clearing", async () => {
    (apiFetch as jest.Mock).mockImplementation((url: string) => {
      if (url === "/api/v1/contacts") {
        return Promise.resolve([
          {
            id: 1,
            contactUserId: 101,
            contactUsername: "active_user",
            contactFullName: "Active User",
            status: "CONTACT",
            clearedAt: "2026-08-17T10:00:00Z",
            lastMessageContent: "New message after clear",
            lastMessageTimestamp: "2026-08-17T10:05:00Z",
            lastMessageSenderId: 101,
            createdAt: "2026-08-17T09:00:00Z",
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
      expect(screen.getByText("Active User")).toBeInTheDocument();
    });

    // In Chats tab: active_user should be rendered because a new message exists
    expect(screen.getByText("New message after clear")).toBeInTheDocument();
  });
});
