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
    });
  });

  afterEach(() => {
    jest.restoreAllMocks();
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

  it("does not display birth date or email fields for another user's profile", async () => {
    (apiFetch as jest.Mock).mockResolvedValueOnce({
      username: "testuser",
      fullName: "Test User",
      birthDate: "1990-01-01",
      email: "test@example.com",
      emailVerified: true,
    });

    render(<UserProfileCard userId={2} currentUserId={1} onBack={jest.fn()} />);

    await waitFor(() =>
      expect(screen.getByText("Test User")).toBeInTheDocument()
    );

    expect(screen.queryByText(/birth date/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/email address/i)).not.toBeInTheDocument();
    expect(screen.queryByText("test@example.com")).not.toBeInTheDocument();
    expect(screen.queryByText(/verified/i)).not.toBeInTheDocument();
  });

  it("calls block api and navigates back when Block is clicked", async () => {
    const mockOnBack = jest.fn();
    jest.spyOn(window, "confirm").mockReturnValue(true);

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

    expect(window.confirm).toHaveBeenCalledWith("Block this user?");
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

  it("does not block when the confirmation is cancelled", async () => {
    jest.spyOn(window, "confirm").mockReturnValue(false);

    render(
      <UserProfileCard
        userId={2}
        currentUserId={1}
        userStatus="CONTACT"
        onBack={jest.fn()}
      />
    );

    await waitFor(() =>
      expect(screen.getByText("Test User")).toBeInTheDocument()
    );

    fireEvent.click(screen.getByRole("button", { name: /block/i }));

    expect(apiFetch).not.toHaveBeenCalledWith(
      "/api/v1/contacts/2/block",
      expect.anything()
    );
  });

  it("calls delete contact api and navigates back when Delete Contact is clicked", async () => {
    const mockOnBack = jest.fn();
    jest.spyOn(window, "confirm").mockReturnValue(true);

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

    expect(window.confirm).toHaveBeenCalledWith(
      "Remove this contact? This will also remove your conversation."
    );
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

  it("does not delete contact when the confirmation is cancelled", async () => {
    jest.spyOn(window, "confirm").mockReturnValue(false);

    render(
      <UserProfileCard
        userId={2}
        currentUserId={1}
        userStatus="CONTACT"
        onBack={jest.fn()}
      />
    );

    await waitFor(() =>
      expect(screen.getByText("Test User")).toBeInTheDocument()
    );

    fireEvent.click(screen.getByRole("button", { name: /delete contact/i }));

    expect(apiFetch).not.toHaveBeenCalledWith(
      "/api/v1/contacts/2",
      expect.anything()
    );
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
