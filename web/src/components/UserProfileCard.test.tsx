import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import UserProfileCard from "./UserProfileCard";
import { apiFetch } from "@/lib/api";

jest.mock("@/lib/api", () => ({
  apiFetch: jest.fn(),
}));

jest.mock("@/context/PresenceContext", () => ({
  usePresence: () => ({
    onlineUsers: {
      2: { status: "ONLINE", lastSeen: new Date().toISOString() },
    },
  }),
}));

describe("UserProfileCard", () => {
  beforeEach(() => {
    (apiFetch as jest.Mock).mockResolvedValue({
      username: "testuser",
      fullName: "Test User",
      email: "test@example.com",
      emailVerified: true,
    });
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it("renders Message, Block, and Delete Contact actions for a contact", async () => {
    const mockOnSelectChat = jest.fn();

    render(
      <UserProfileCard
        userId={2}
        currentUserId={1}
        userStatus="CONTACT"
        onBack={jest.fn()}
        onSelectChat={mockOnSelectChat}
      />
    );

    // Wait for the profile to load
    await waitFor(() =>
      expect(screen.getByText("Test User")).toBeInTheDocument()
    );

    // Check primary Message action
    const messageButton = screen.getByRole("button", { name: /message/i });
    expect(messageButton).toBeInTheDocument();

    // Check placeholder actions
    expect(screen.getByRole("button", { name: /block/i })).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /delete contact/i })
    ).toBeInTheDocument();

    // Click Message button should trigger onSelectChat
    fireEvent.click(messageButton);
    expect(mockOnSelectChat).toHaveBeenCalledWith(
      expect.objectContaining({
        id: 2,
        username: "testuser",
      })
    );
  });

  it("calls block api and navigates back when Block is clicked", async () => {
    const mockOnBack = jest.fn();

    render(
      <UserProfileCard
        userId={2}
        currentUserId={1}
        userStatus="CONTACT"
        onBack={mockOnBack}
      />
    );

    await waitFor(() =>
      expect(screen.getByText("Test User")).toBeInTheDocument()
    );

    const blockButton = screen.getByRole("button", { name: /block/i });

    // Setup mock for the block call
    (apiFetch as jest.Mock).mockResolvedValueOnce({});

    fireEvent.click(blockButton);

    expect(apiFetch).toHaveBeenCalledWith(
      "/api/v1/contacts/2/block",
      expect.objectContaining({
        method: "PUT",
      })
    );

    await waitFor(() => {
      expect(mockOnBack).toHaveBeenCalled();
    });
  });

  it("calls delete contact api and navigates back when Delete Contact is clicked", async () => {
    const mockOnBack = jest.fn();

    render(
      <UserProfileCard
        userId={2}
        currentUserId={1}
        userStatus="CONTACT"
        onBack={mockOnBack}
      />
    );

    await waitFor(() =>
      expect(screen.getByText("Test User")).toBeInTheDocument()
    );

    const deleteButton = screen.getByRole("button", {
      name: /delete contact/i,
    });

    (apiFetch as jest.Mock).mockResolvedValueOnce({});

    fireEvent.click(deleteButton);

    expect(apiFetch).toHaveBeenCalledWith(
      "/api/v1/contacts/2",
      expect.objectContaining({
        method: "DELETE",
      })
    );

    await waitFor(() => {
      expect(mockOnBack).toHaveBeenCalled();
    });
  });

  it("renders Unblock button for a blocked user", async () => {
    render(
      <UserProfileCard
        userId={2}
        currentUserId={1}
        userStatus="BLOCKED"
        onBack={jest.fn()}
      />
    );

    await waitFor(() =>
      expect(screen.getByText("Test User")).toBeInTheDocument()
    );

    // Should show Unblock buttons (header + actions list)
    const unblockButtons = screen.getAllByRole("button", { name: /unblock/i });
    expect(unblockButtons.length).toBe(2);

    // Should NOT show Block or Delete Contact
    expect(
      screen.queryByRole("button", { name: /^block$/i })
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /delete contact/i })
    ).not.toBeInTheDocument();

    // Should NOT show Message button
    expect(
      screen.queryByRole("button", { name: /message/i })
    ).not.toBeInTheDocument();
  });

  it("calls unblock api and navigates back when Unblock is clicked", async () => {
    const mockOnBack = jest.fn();

    render(
      <UserProfileCard
        userId={2}
        currentUserId={1}
        userStatus="BLOCKED"
        onBack={mockOnBack}
      />
    );

    await waitFor(() =>
      expect(screen.getByText("Test User")).toBeInTheDocument()
    );

    const unblockButtons = screen.getAllByRole("button", { name: /unblock/i });

    // Setup mock for the unblock call
    (apiFetch as jest.Mock).mockResolvedValueOnce({});

    // Click the actions-list Unblock button (second one)
    fireEvent.click(unblockButtons[1]);

    expect(apiFetch).toHaveBeenCalledWith(
      "/api/v1/contacts/2/unblock",
      expect.objectContaining({
        method: "PUT",
      })
    );

    await waitFor(() => {
      expect(mockOnBack).toHaveBeenCalled();
    });
  });
});
