import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import ChatViewport from "./ChatViewport";
import { useAuth } from "@/context/AuthContext";
import { useWebSocket } from "@/context/WebSocketContext";

window.HTMLElement.prototype.scrollIntoView = jest.fn();

jest.mock("@/context/AuthContext", () => ({
  useAuth: jest.fn(),
}));
jest.mock("@/context/WebSocketContext", () => ({
  useWebSocket: jest.fn(),
}));
jest.mock("@/lib/api", () => ({
  apiFetch: jest.fn().mockResolvedValue([]),
}));

describe("ChatViewport", () => {
  const mockSendPublicMessage = jest.fn();
  const mockSendPrivateMessage = jest.fn();
  const mockSendTypingIndicator = jest.fn();
  const mockLoadPublicHistory = jest.fn();
  const mockLoadPrivateHistory = jest.fn();

  beforeEach(() => {
    (useAuth as jest.Mock).mockReturnValue({ userId: 1 });
    (useWebSocket as jest.Mock).mockReturnValue({
      connected: true,
      publicMessages: [],
      privateMessages: {},
      onlineUsers: {},
      typingUsers: {},
      sendPublicMessage: mockSendPublicMessage,
      sendPrivateMessage: mockSendPrivateMessage,
      sendTypingIndicator: mockSendTypingIndicator,
      loadPublicHistory: mockLoadPublicHistory,
      loadPrivateHistory: mockLoadPrivateHistory,
      hasMorePublicHistory: false,
      hasMorePrivateHistory: {},
    });
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it("displays typing indicator when the active chat user is typing", () => {
    (useWebSocket as jest.Mock).mockReturnValue({
      connected: true,
      publicMessages: [],
      privateMessages: {},
      onlineUsers: {},
      typingUsers: { 2: true },
      sendPublicMessage: mockSendPublicMessage,
      sendPrivateMessage: mockSendPrivateMessage,
      sendTypingIndicator: mockSendTypingIndicator,
      loadPublicHistory: mockLoadPublicHistory,
      loadPrivateHistory: mockLoadPrivateHistory,
      hasMorePublicHistory: false,
      hasMorePrivateHistory: {},
    });

    render(
      <ChatViewport
        activeChat={{
          id: 2,
          isPublic: false,
          username: "user2",
          status: "ACCEPTED",
        }}
        onBannerAction={jest.fn()}
      />
    );

    expect(screen.getByText("user2 is typing...")).toBeInTheDocument();
  });

  it("sends typing indicator when user types in the input", async () => {
    render(
      <ChatViewport
        activeChat={{
          id: 2,
          isPublic: false,
          username: "user2",
          status: "ACCEPTED",
        }}
        onBannerAction={jest.fn()}
      />
    );

    const input = screen.getByPlaceholderText("Compose message for user2...");
    fireEvent.change(input, { target: { value: "h" } });

    await waitFor(() => {
      expect(mockSendTypingIndicator).toHaveBeenCalledWith(2, true);
    });
  });

  it("sends typing indicator false immediately after sending a private message", async () => {
    render(
      <ChatViewport
        activeChat={{
          id: 2,
          isPublic: false,
          username: "user2",
          status: "ACCEPTED",
        }}
        onBannerAction={jest.fn()}
      />
    );

    const input = screen.getByPlaceholderText("Compose message for user2...");
    fireEvent.change(input, { target: { value: "hello" } });

    const sendButton = screen.getByRole("button", { name: /send/i });
    fireEvent.click(sendButton);

    await waitFor(() => {
      expect(mockSendPrivateMessage).toHaveBeenCalledWith(2, "hello");
      expect(mockSendTypingIndicator).toHaveBeenCalledWith(2, false);
    });
  });
});
