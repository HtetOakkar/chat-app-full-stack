import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import UserProfileCard from "./UserProfileCard";
import { apiFetch } from "@/lib/api";

jest.mock("@/lib/api", () => ({
  apiFetch: jest.fn(),
}));

jest.mock("@/context/WebSocketContext", () => ({
  useWebSocket: () => ({
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
});
